using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace Danos.Api.Dominio;

/// <summary>
/// Aplica un lote de operaciones hechas sin conexión (FP-06, QA-14). Invariantes:
/// (1) ninguna rama borra ni sobrescribe: la evaluación vigente nunca se reemplaza por una entrante en conflicto;
/// (2) detecta conflictos y duplicados pero nunca los resuelve: conserva ambas versiones;
/// (3) reenviar una operación devuelve exactamente el mismo resultado y no crea nada;
/// (4) cada operación corre en su propia transacción: el error de una no tumba las demás.
/// </summary>
public class ServicioSincronizacion(
    DanosDbContext db,
    IOptions<OpcionesDuplicados> opcionesDuplicados,
    ILogger<ServicioSincronizacion> logger)
{
    private sealed record Catalogo(IReadOnlySet<string> Tipos, IReadOnlySet<Guid> Desastres);

    private sealed record Contexto(string IdDispositivo, long DesfaseRelojMs, Catalogo Catalogo);

    public async Task<List<ResultadoOperacionDto>> ProcesarLoteAsync(
        LoteSincronizacionDto lote, DateTime instanteRecepcion, CancellationToken cancelar)
    {
        // Una vez por lote: cuánto va adelantado (+) o atrasado (−) el reloj del celular.
        long desfase = lote.InstanteEnvioDispositivo is null
            ? 0
            : (long)(instanteRecepcion - lote.InstanteEnvioDispositivo.Value.UtcDateTime).TotalMilliseconds;

        var contexto = new Contexto(lote.IdDispositivo!, desfase, await CargarCatalogoAsync(cancelar));

        var resultados = new List<ResultadoOperacionDto>();
        foreach (var op in lote.Operaciones!)
        {
            try
            {
                resultados.Add(await ProcesarOperacionAsync(op, contexto, cancelar));
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                // Solo llega aquí un error que tampoco se resolvió al reintentar. Se corta el lote: las
                // operaciones anteriores ya hicieron commit (cada una en su transacción) y las siguientes
                // pueden depender de esta (CREAR → ACTUALIZAR), así que no se procesan. Con el 503 la app
                // reenvía el lote completo, lo ya procesado devuelve su resultado guardado y se retoma
                // en orden desde aquí: nada se pierde ni se marca INVALIDA por error.
                logger.LogError(ex, "No se pudo procesar la operación {IdOperacion}; se corta el lote", op.IdOperacion);
                throw new LoteIncompletoException(resultados.Count, lote.Operaciones!.Count);
            }
        }
        return resultados;
    }

    private async Task<Catalogo> CargarCatalogoAsync(CancellationToken cancelar)
    {
        var tipos = await (from t in db.TiposActivo select t.Codigo).ToListAsync(cancelar);
        var desastres = await (from d in db.Desastres select d.Id).ToListAsync(cancelar);
        return new Catalogo(tipos.ToHashSet(), desastres.ToHashSet());
    }

    /// <summary>
    /// Si SaveChanges choca con otro lote que cambió el mismo activo en paralelo (token de concurrencia de
    /// Activo.Version o PK de ActivoVersion/OperacionProcesada), se revierte, se limpia el ChangeTracker y se
    /// reevalúa la operación UNA vez: en el segundo intento la regla ya ve la versión del otro dispositivo,
    /// o el paso 1 encuentra la operación ya procesada.
    /// </summary>
    private async Task<ResultadoOperacionDto> ProcesarOperacionAsync(OperacionDto op, Contexto ctx, CancellationToken cancelar)
    {
        for (int intento = 1; ; intento++)
        {
            db.ChangeTracker.Clear();
            try
            {
                return await IntentarAsync(op, ctx, cancelar);
            }
            catch (DbUpdateException ex) when (intento == 1 && EsCarrera(ex))
            {
                logger.LogInformation("Choque de concurrencia en la operación {IdOperacion} ({Tipo}); se reevalúa.",
                    op.IdOperacion, ex.GetType().Name);
            }
            finally
            {
                db.ChangeTracker.Clear();
            }
        }
    }

    /// <summary>
    /// Solo los choques con otro lote en paralelo se reintentan: token de concurrencia, llave duplicada
    /// (23505), serialización (40001) o interbloqueo (40P01). Un error permanente no se repite.
    /// </summary>
    private static bool EsCarrera(DbUpdateException ex) =>
        ex is DbUpdateConcurrencyException
        || ex.InnerException is Npgsql.PostgresException { SqlState: "23505" or "40001" or "40P01" };

    private async Task<ResultadoOperacionDto> IntentarAsync(OperacionDto op, Contexto ctx, CancellationToken cancelar)
    {
        // Sin idOperacion no hay cómo hacer el reenvío idempotente: se rechaza sin guardar nada.
        if (op.IdOperacion is not Guid idOperacion || idOperacion == Guid.Empty)
        {
            return Resultado(Guid.Empty, ResultadoOperacion.INVALIDA, EstadoEvaluacion.RECHAZADA,
                "Falta el atributo obligatorio 'idOperacion'", null);
        }

        await using var transaccion = await db.Database.BeginTransactionAsync(cancelar);

        // 1. REENVÍO: lo guardado la primera vez, sin tocar nada más.
        var guardado = await (
            from o in db.OperacionesProcesadas
            where o.IdOperacion == idOperacion
            select o.ResultadoJson).FirstOrDefaultAsync(cancelar);
        if (guardado != null)
        {
            return Json.Deserializar<ResultadoOperacionDto>(guardado);
        }

        // 2. INVÁLIDA: lo que se decide sin mirar evaluaciones ni activos.
        string? motivo = ValidadorEvaluacion.Validar(op, ctx.Catalogo.Tipos, ctx.Catalogo.Desastres);
        if (motivo != null)
        {
            return await GuardarAsync(transaccion, idOperacion, ctx, Invalida(idOperacion, motivo), cancelar);
        }

        var e = op.Evaluacion!;
        var tipo = Enum.Parse<TipoOperacion>(op.Tipo!);
        var evaluacionId = e.Id!.Value;
        var activoId = e.ActivoId!.Value;

        // 2b. INVÁLIDA: lo que depende de lo guardado.
        var existente = await (from x in db.Evaluaciones where x.Id == evaluacionId select x).FirstOrDefaultAsync(cancelar);
        motivo = tipo switch
        {
            TipoOperacion.CREAR when existente != null =>
                $"Ya existe una evaluación con id {evaluacionId}",
            TipoOperacion.ACTUALIZAR when existente == null =>
                $"No existe la evaluación {evaluacionId} que se quiere actualizar",
            TipoOperacion.ACTUALIZAR when existente!.IdDispositivo != ctx.IdDispositivo =>
                $"La evaluación {evaluacionId} pertenece a otro dispositivo",
            TipoOperacion.ACTUALIZAR when existente!.ActivoId != activoId =>
                "Una actualización no puede cambiar el activo de la evaluación",
            _ => null
        };
        motivo ??= await ValidarEvidenciasAsync(e, evaluacionId, cancelar);

        var activo = await (from a in db.Activos where a.Id == activoId select a).FirstOrDefaultAsync(cancelar);
        if (motivo == null && activo == null && e.ActivoNuevo == null)
        {
            motivo = $"El activo {activoId} no existe y la operación no trae activoNuevo";
        }
        // Una base que el servidor nunca emitió saltaría la regla de conflictos (sobrescritura silenciosa).
        if (motivo == null && activo != null && op.VersionBaseActivo is int b && (b < 0 || b > activo.Version))
        {
            motivo = $"versionBaseActivo {b} no es válida: el activo {activoId} va en la versión {activo.Version}";
        }
        if (motivo != null)
        {
            return await GuardarAsync(transaccion, idOperacion, ctx, Invalida(idOperacion, motivo), cancelar);
        }

        var resultado = activo == null
            ? await ActivoNuevoAsync(idOperacion, e, ctx, cancelar)
            : await ActivoExistenteAsync(idOperacion, tipo, op.VersionBaseActivo, e, existente, activo, ctx, cancelar);

        return await GuardarAsync(transaccion, idOperacion, ctx, resultado, cancelar);
    }

    // ---------- 3. Activo nuevo ----------

    private async Task<ResultadoOperacionDto> ActivoNuevoAsync(
        Guid idOperacion, EvaluacionEntranteDto e, Contexto ctx, CancellationToken cancelar)
    {
        var ahora = Ahora();
        var nuevo = e.ActivoNuevo!;
        var instanteCampo = e.InstanteCampo!.Value.UtcDateTime;

        var activo = new Activo
        {
            Id = e.ActivoId!.Value,
            TipoActivoCodigo = nuevo.TipoCodigo!,
            Lat = nuevo.Lat!.Value,
            Lon = nuevo.Lon!.Value,
            Referencia = nuevo.Referencia ?? "",
            Version = 1,
            CreadoPorDispositivo = ctx.IdDispositivo
        };
        db.Activos.Add(activo);

        await BloquearTipoAsync(activo.TipoActivoCodigo, cancelar);
        var duplicado = await BuscarDuplicadoAsync(activo, instanteCampo, cancelar);
        bool enConflicto = duplicado != null;

        var evaluacion = NuevaEvaluacion(e, ctx, ahora, enConflicto ? EstadoEvaluacion.EN_CONFLICTO : EstadoInicial(e));
        db.Evaluaciones.Add(evaluacion);
        var version = NuevaVersion(e, evaluacion.Id, 1, null, enConflicto, ctx, ahora);
        db.EvaluacionVersiones.Add(version);
        db.ActivoVersiones.Add(new ActivoVersion
        {
            ActivoId = activo.Id, Numero = 1, IdDispositivo = ctx.IdDispositivo, EvaluacionId = evaluacion.Id, Instante = ahora
        });
        AgregarEvidencias(e, evaluacion.Id, yaDeclaradas: new HashSet<Guid>());
        EmitirEv09(evaluacion.Id, e, ctx, ahora, enConflicto, versionesConservadas: enConflicto ? 2 : 1);

        if (duplicado is null)
        {
            return Resultado(idOperacion, ResultadoOperacion.APLICADA, evaluacion.Estado, null, 1, ahora);
        }

        var opciones = opcionesDuplicados.Value;
        double distancia = Haversine.DistanciaMetros(activo.Lat, activo.Lon, duplicado.Lat, duplicado.Lon);
        double horas = Math.Abs((duplicado.InstanteCampo - instanteCampo).TotalHours);
        string regla = $"Posible duplicado: el activo {duplicado.ActivoId} del mismo tipo ({activo.TipoActivoCodigo}) " +
                       $"está a {distancia:0.0} m y tiene la evaluación {duplicado.EvaluacionId} a {horas:0.0} h " +
                       $"(umbral {opciones.DistanciaMetros} m, ±{opciones.VentanaHoras} h). Dispositivo: {ctx.IdDispositivo}";
        CrearConflicto(activo.Id, TipoConflicto.DUPLICADO, duplicado.EvaluacionId, evaluacion.Id, version.Id, regla, ahora);

        return Resultado(idOperacion, ResultadoOperacion.POSIBLE_DUPLICADO, EstadoEvaluacion.EN_CONFLICTO,
            "Hay otro activo del mismo tipo muy cerca con una evaluación reciente; queda para revisión", 1, ahora);
    }

    /// <summary>
    /// Candado de PostgreSQL por tipo de activo, hasta el fin de la transacción. Sin él, dos lotes que crean
    /// a la vez activos cercanos del mismo tipo no ven la fila del otro (aún sin commit) y ninguno detecta el
    /// duplicado. Con él, el segundo espera al commit del primero y su búsqueda ya lo encuentra.
    /// Crear activos nuevos es raro frente a evaluar los existentes, así que serializar por tipo no estorba.
    /// </summary>
    private async Task BloquearTipoAsync(string tipoCodigo, CancellationToken cancelar)
    {
        const int espacio = 0x44414E31;   // "DAN1": separa estos candados de otros que use la base
        int clave = ClaveEstable(tipoCodigo);
        await db.Database.ExecuteSqlAsync($"SELECT pg_advisory_xact_lock({espacio}, {clave})", cancelar);
    }

    /// <summary>FNV-1a de 32 bits: igual en todos los procesos (string.GetHashCode cambia por proceso).</summary>
    private static int ClaveEstable(string texto)
    {
        uint hash = 2166136261;
        foreach (char c in texto)
        {
            hash = (hash ^ c) * 16777619;
        }
        return unchecked((int)hash);
    }

    private async Task<CandidatoDuplicado?> BuscarDuplicadoAsync(Activo activo, DateTime instanteCampo, CancellationToken cancelar)
    {
        var o = opcionesDuplicados.Value;
        // Recuadro en SQL para no traer toda la tabla; la distancia exacta la decide Haversine en memoria.
        double dLat = Haversine.GradosLatitud(o.DistanciaMetros);
        double dLon = Haversine.GradosLongitud(o.DistanciaMetros, activo.Lat);
        var desde = instanteCampo.AddHours(-o.VentanaHoras);
        var hasta = instanteCampo.AddHours(o.VentanaHoras);
        double minLat = activo.Lat - dLat, maxLat = activo.Lat + dLat;
        double minLon = activo.Lon - dLon, maxLon = activo.Lon + dLon;
        string tipo = activo.TipoActivoCodigo;
        Guid propio = activo.Id;

        var candidatos = await (
            from a in db.Activos.AsNoTracking()
            join ev in db.Evaluaciones.AsNoTracking() on a.Id equals ev.ActivoId
            where a.TipoActivoCodigo == tipo && a.Id != propio
               && a.Lat >= minLat && a.Lat <= maxLat && a.Lon >= minLon && a.Lon <= maxLon
               && ev.InstanteCampo >= desde && ev.InstanteCampo <= hasta
            select new CandidatoDuplicado(a.Id, a.TipoActivoCodigo, a.Lat, a.Lon, ev.Id, ev.InstanteCampo))
            .ToListAsync(cancelar);

        return DetectorDuplicados.Buscar(activo.Lat, activo.Lon, tipo, instanteCampo, candidatos, o.DistanciaMetros, o.VentanaHoras);
    }

    // ---------- 4. Activo existente ----------

    private async Task<ResultadoOperacionDto> ActivoExistenteAsync(
        Guid idOperacion, TipoOperacion tipo, int? versionBase, EvaluacionEntranteDto e,
        Evaluacion? existente, Activo activo, Contexto ctx, CancellationToken cancelar)
    {
        var ahora = Ahora();
        var versiones = await (
            from v in db.ActivoVersiones
            where v.ActivoId == activo.Id
            select new VersionRegistrada(v.Numero, v.IdDispositivo)).ToListAsync(cancelar);
        var veredicto = ReglaConflicto.Evaluar(versionBase, ctx.IdDispositivo, versiones);

        int numeroVersion = 1;
        var yaDeclaradas = new HashSet<Guid>();
        if (existente != null)
        {
            numeroVersion = await (
                from v in db.EvaluacionVersiones
                where v.EvaluacionId == existente.Id
                select (int?)v.NumeroVersion).MaxAsync(cancelar) ?? 0;
            numeroVersion++;
            var declaradas = await (from x in db.Evidencias where x.EvaluacionId == existente.Id select x.Id).ToListAsync(cancelar);
            yaDeclaradas = declaradas.ToHashSet();
        }

        if (!veredicto.HayConflicto)
        {
            // Si la evaluación tiene un conflicto pendiente, la edición se aplica pero el estado sigue
            // EN_CONFLICTO: el servidor no da por resuelto nada, eso es de la revisión humana.
            bool conflictoPendiente = existente != null && await (
                from c in db.Conflictos
                where c.Estado == EstadoConflicto.PENDIENTE && c.EvaluacionEntranteId == existente.Id
                select c.Id).AnyAsync(cancelar);
            var estado = conflictoPendiente ? EstadoEvaluacion.EN_CONFLICTO : EstadoInicial(e);
            Evaluacion evaluacion;
            if (tipo == TipoOperacion.CREAR)
            {
                evaluacion = NuevaEvaluacion(e, ctx, ahora, estado);
                db.Evaluaciones.Add(evaluacion);
            }
            else
            {
                evaluacion = existente!;
                CopiarContenido(e, evaluacion, ctx, ahora);
                evaluacion.Estado = estado;
            }

            db.EvaluacionVersiones.Add(NuevaVersion(e, evaluacion.Id, numeroVersion, versionBase, false, ctx, ahora));
            int nuevaVersion = activo.Version + 1;
            activo.Version = nuevaVersion;   // UPDATE ... WHERE version = <la leída>
            db.ActivoVersiones.Add(new ActivoVersion
            {
                ActivoId = activo.Id, Numero = nuevaVersion, IdDispositivo = ctx.IdDispositivo, EvaluacionId = evaluacion.Id, Instante = ahora
            });
            AgregarEvidencias(e, evaluacion.Id, yaDeclaradas);
            EmitirEv09(evaluacion.Id, e, ctx, ahora, conflictoPendiente, numeroVersion);
            return Resultado(idOperacion, ResultadoOperacion.APLICADA, estado, null, nuevaVersion, ahora);
        }

        // Con conflicto: NO se toca la evaluación vigente ni Activo.Version. Se guarda la entrante completa.
        var vigenteId = await (
            from v in db.ActivoVersiones
            where v.ActivoId == activo.Id && v.EvaluacionId != null
            orderby v.Numero descending
            select v.EvaluacionId).FirstOrDefaultAsync(cancelar);

        Evaluacion entrante;
        if (tipo == TipoOperacion.CREAR)
        {
            entrante = NuevaEvaluacion(e, ctx, ahora, EstadoEvaluacion.EN_CONFLICTO);
            db.Evaluaciones.Add(entrante);
        }
        else
        {
            // Solo se marca: el contenido nuevo queda en la EvaluacionVersion en conflicto.
            entrante = existente!;
            entrante.Estado = EstadoEvaluacion.EN_CONFLICTO;
        }

        var version = NuevaVersion(e, entrante.Id, numeroVersion, versionBase, true, ctx, ahora);
        db.EvaluacionVersiones.Add(version);
        AgregarEvidencias(e, entrante.Id, yaDeclaradas);
        CrearConflicto(activo.Id, TipoConflicto.VERSION, vigenteId, entrante.Id, version.Id, veredicto.Detalle, ahora);
        EmitirEv09(entrante.Id, e, ctx, ahora, true, numeroVersion + (vigenteId is null ? 0 : 1));

        return Resultado(idOperacion, ResultadoOperacion.EN_CONFLICTO, EstadoEvaluacion.EN_CONFLICTO,
            "El activo cambió en el servidor desde la descarga; se conservan ambas versiones para revisión", null, ahora);
    }

    // ---------- Piezas comunes ----------

    private async Task<string?> ValidarEvidenciasAsync(EvaluacionEntranteDto e, Guid evaluacionId, CancellationToken cancelar)
    {
        if (e.Evidencias is null || e.Evidencias.Count == 0)
        {
            return null;
        }
        var ids = new List<Guid>();
        foreach (var ev in e.Evidencias)
        {
            ids.Add(ev.Id!.Value);
        }
        var deOtra = await (
            from x in db.Evidencias
            where ids.Contains(x.Id) && x.EvaluacionId != evaluacionId
            select x.Id).FirstOrDefaultAsync(cancelar);
        return deOtra == Guid.Empty ? null : $"La evidencia {deOtra} ya pertenece a otra evaluación";
    }

    private void AgregarEvidencias(EvaluacionEntranteDto e, Guid evaluacionId, IReadOnlySet<Guid> yaDeclaradas)
    {
        if (e.Evidencias is null)
        {
            return;
        }
        foreach (var ev in e.Evidencias)
        {
            if (yaDeclaradas.Contains(ev.Id!.Value))
            {
                continue;
            }
            db.Evidencias.Add(new Evidencia
            {
                Id = ev.Id.Value,
                EvaluacionId = evaluacionId,
                Lat = ev.Lat!.Value,
                Lon = ev.Lon!.Value,
                InstanteCaptura = ev.InstanteCaptura!.Value.UtcDateTime,
                Sha256Declarado = ev.Sha256!.ToLowerInvariant(),
                TamanoBytes = ev.TamanoBytes!.Value,
                Subida = false
            });
        }
    }

    private static Evaluacion NuevaEvaluacion(EvaluacionEntranteDto e, Contexto ctx, DateTime ahora, EstadoEvaluacion estado)
    {
        var evaluacion = new Evaluacion { Id = e.Id!.Value, ActivoId = e.ActivoId!.Value, Estado = estado };
        CopiarContenido(e, evaluacion, ctx, ahora);
        return evaluacion;
    }

    private static void CopiarContenido(EvaluacionEntranteDto e, Evaluacion destino, Contexto ctx, DateTime ahora)
    {
        destino.DesastreId = e.DesastreId;
        destino.InstanteCampo = e.InstanteCampo!.Value.UtcDateTime;
        destino.Evaluador = e.Evaluador!.Trim();
        destino.Organizacion = e.Organizacion!.Trim();
        destino.Lat = e.Lat!.Value;
        destino.Lon = e.Lon!.Value;
        destino.TipoActivoCodigo = e.TipoActivoCodigo!;
        destino.NivelDano = Enum.Parse<NivelDano>(e.NivelDano!);
        destino.Descripcion = e.Descripcion!;
        destino.PrecisionM = e.PrecisionM!.Value;
        destino.InstanteServidor = ahora;
        destino.IdDispositivo = ctx.IdDispositivo;
        destino.DesfaseRelojMs = ctx.DesfaseRelojMs;
    }

    private static EvaluacionVersion NuevaVersion(EvaluacionEntranteDto e, Guid evaluacionId, int numero, int? versionBase,
        bool enConflicto, Contexto ctx, DateTime ahora) => new()
    {
        Id = Guid.NewGuid(),
        EvaluacionId = evaluacionId,
        NumeroVersion = numero,
        ContenidoJson = Json.Serializar(e),
        Autor = e.Evaluador!.Trim(),
        IdDispositivo = ctx.IdDispositivo,
        VersionActivoBase = versionBase,
        EnConflicto = enConflicto,
        Instante = ahora
    };

    private void CrearConflicto(Guid activoId, TipoConflicto tipo, Guid? vigenteId, Guid entranteId, Guid versionEntranteId,
        string regla, DateTime ahora)
    {
        var conflicto = new Conflicto
        {
            Id = Guid.NewGuid(),
            ActivoId = activoId,
            Tipo = tipo,
            EvaluacionVigenteId = vigenteId,
            EvaluacionEntranteId = entranteId,
            VersionEntranteId = versionEntranteId,
            Regla = regla,
            Estado = EstadoConflicto.PENDIENTE,
            Instante = ahora
        };
        db.Conflictos.Add(conflicto);

        var ids = new List<Guid>();
        if (vigenteId is Guid v)
        {
            ids.Add(v);
        }
        ids.Add(entranteId);
        Emitir("EV-10", new
        {
            idConflicto = conflicto.Id,
            idsEvaluaciones = ids,
            reglaDeteccion = regla,
            estadoRevision = conflicto.Estado.ToString()
        }, ahora);
    }

    /// <summary>versionesConservadas: versiones guardadas de la evaluación, más la vigente si quedó en conflicto.</summary>
    private void EmitirEv09(Guid idEvaluacion, EvaluacionEntranteDto e, Contexto ctx, DateTime ahora, bool enConflicto,
        int versionesConservadas) =>
        Emitir("EV-09", new
        {
            idEvaluacion,
            idDispositivo = ctx.IdDispositivo,
            instanteLocal = e.InstanteCampo!.Value.UtcDateTime,
            instanteServidor = ahora,
            enConflicto,
            versionesConservadas
        }, ahora);

    private void Emitir(string tipo, object payload, DateTime ahora) =>
        db.EventosSalientes.Add(new EventoSaliente { Tipo = tipo, PayloadJson = Json.Serializar(payload), Instante = ahora });

    /// <summary>7. Siempre se guarda el resultado, dentro de la misma transacción que los cambios.</summary>
    private async Task<ResultadoOperacionDto> GuardarAsync(
        Microsoft.EntityFrameworkCore.Storage.IDbContextTransaction transaccion,
        Guid idOperacion, Contexto ctx, ResultadoOperacionDto resultado, CancellationToken cancelar)
    {
        db.OperacionesProcesadas.Add(new OperacionProcesada
        {
            IdOperacion = idOperacion,
            IdDispositivo = ctx.IdDispositivo,
            ResultadoJson = Json.Serializar(resultado),
            Instante = resultado.InstanteServidor
        });
        await db.SaveChangesAsync(cancelar);
        await transaccion.CommitAsync(cancelar);
        return resultado;
    }

    private static EstadoEvaluacion EstadoInicial(EvaluacionEntranteDto e) =>
        e.DesastreId is null ? EstadoEvaluacion.PRELIMINAR : EstadoEvaluacion.REGISTRADA;

    private static ResultadoOperacionDto Invalida(Guid idOperacion, string motivo) =>
        Resultado(idOperacion, ResultadoOperacion.INVALIDA, EstadoEvaluacion.RECHAZADA, motivo, null);

    private static ResultadoOperacionDto Resultado(Guid idOperacion, ResultadoOperacion resultado, EstadoEvaluacion? estado,
        string? motivo, int? nuevaVersion, DateTime? instante = null) =>
        new(idOperacion, resultado, estado, motivo, nuevaVersion, instante ?? Ahora());

    /// <summary>
    /// UTC truncado a microsegundos (la precisión de PostgreSQL), para que el resultado guardado y el
    /// devuelto la primera vez sean idénticos al reenviar.
    /// </summary>
    private static DateTime Ahora()
    {
        long ticks = DateTime.UtcNow.Ticks;
        return new DateTime(ticks - ticks % 10, DateTimeKind.Utc);
    }
}

public class LoteIncompletoException(int procesadas, int total)
    : Exception($"Se procesaron {procesadas} de {total} operaciones y la siguiente falló; reenvíe el lote " +
                "(lo ya procesado devolverá el mismo resultado)")
{
    public int Procesadas { get; } = procesadas;
}
