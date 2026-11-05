using System.Net;
using System.Net.Http.Json;
using Danos.Api.Acceso;
using Danos.Api.Dominio;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;
using static Danos.Api.Tests.Integracion.Lotes;

namespace Danos.Api.Tests.Integracion;

[Collection(ColeccionApi.Nombre)]
public class SincronizacionTests(FabricaApi api)
{
    // ---------- Rama 1: reenvío ----------

    [Fact]
    public async Task Reenvio_identico_devuelve_lo_mismo_y_no_crea_filas()
    {
        var cliente = api.Cliente("reenvio-A");
        var lote = Lote("reenvio-A",
            Crear(FabricaApi.TomarActivoSembrado(), 1, foto: FotoJpeg()),
            Crear(FabricaApi.TomarActivoSembrado(), 1, conDesastre: false),
            CrearConActivoNuevo(FabricaApi.TomarZonaLibre().Lat, FabricaApi.TomarZonaLibre().Lon),
            Crear(Guid.NewGuid(), 1));   // INVALIDA: activo inexistente sin activoNuevo

        var primera = await FabricaApi.Sincronizar(cliente, lote);
        var antes = await Contar();
        var segunda = await FabricaApi.Sincronizar(cliente, lote);
        var despues = await Contar();

        Assert.Equal(Json.Serializar(primera), Json.Serializar(segunda));
        Assert.Equal(antes, despues);
    }

    // ---------- Rama 2: inválida ----------

    [Fact]
    public async Task Falta_un_atributo_es_invalida_y_no_guarda_la_evaluacion()
    {
        var op = Crear(FabricaApi.TomarActivoSembrado(), 1);
        op.Evaluacion!.Evaluador = null;

        var r = await Uno("inval-A", op);

        Assert.Equal(ResultadoOperacion.INVALIDA, r.Resultado);
        Assert.Equal(EstadoEvaluacion.RECHAZADA, r.EstadoEvaluacion);
        Assert.Contains("evaluador", r.Motivo);
        await using var db = api.NuevoContexto();
        Assert.False(await db.Evaluaciones.AnyAsync(e => e.Id == op.Evaluacion.Id));
        Assert.True(await db.OperacionesProcesadas.AnyAsync(o => o.IdOperacion == op.IdOperacion));
    }

    [Fact]
    public async Task Nivel_tipo_y_desastre_invalidos()
    {
        var nivel = Crear(FabricaApi.TomarActivoSembrado(), 1, nivel: "CATASTROFICO");
        var tipo = Crear(FabricaApi.TomarActivoSembrado(), 1);
        tipo.Evaluacion!.TipoActivoCodigo = "nave";
        var desastre = Crear(FabricaApi.TomarActivoSembrado(), 1, desastreId: Guid.NewGuid());
        var coords = Crear(FabricaApi.TomarActivoSembrado(), 1);
        coords.Evaluacion!.Lat = 95;

        var respuesta = await FabricaApi.Sincronizar(api.Cliente("inval-B"), Lote("inval-B", nivel, tipo, desastre, coords));

        Assert.All(respuesta.Resultados, r => Assert.Equal(ResultadoOperacion.INVALIDA, r.Resultado));
    }

    [Fact]
    public async Task Crear_con_un_id_que_ya_existe_es_invalida()
    {
        var primera = Crear(FabricaApi.TomarActivoSembrado(), 1);
        var repetida = Crear(FabricaApi.TomarActivoSembrado(), 1);
        repetida.Evaluacion!.Id = primera.Evaluacion!.Id;

        var respuesta = await FabricaApi.Sincronizar(api.Cliente("inval-C"), Lote("inval-C", primera, repetida));

        Assert.Equal(ResultadoOperacion.APLICADA, respuesta.Resultados[0].Resultado);
        Assert.Equal(ResultadoOperacion.INVALIDA, respuesta.Resultados[1].Resultado);
    }

    [Fact]
    public async Task Actualizar_inexistente_o_de_otro_dispositivo_es_invalida()
    {
        var creada = Crear(FabricaApi.TomarActivoSembrado(), 1);
        Assert.Equal(ResultadoOperacion.APLICADA, (await Uno("dueno", creada)).Resultado);

        var deOtro = await Uno("intruso", Actualizar(creada.Evaluacion!, 2, "LEVE"));
        var inexistente = Crear(FabricaApi.TomarActivoSembrado(), 1);
        var actualizarInexistente = await Uno("dueno", Actualizar(inexistente.Evaluacion!, 1, "LEVE"));

        Assert.Equal(ResultadoOperacion.INVALIDA, deOtro.Resultado);
        Assert.Contains("otro dispositivo", deOtro.Motivo);
        Assert.Equal(ResultadoOperacion.INVALIDA, actualizarInexistente.Resultado);
    }

    [Fact]
    public async Task Activo_inexistente_sin_activoNuevo_es_invalida()
    {
        var r = await Uno("inval-D", Crear(Guid.NewGuid(), 1));
        Assert.Equal(ResultadoOperacion.INVALIDA, r.Resultado);
        Assert.Contains("activo", r.Motivo);
    }

    // ---------- Rama 3: activo nuevo ----------

    [Fact]
    public async Task Activo_nuevo_se_crea_con_version_1()
    {
        var (lat, lon) = FabricaApi.TomarZonaLibre();
        var op = CrearConActivoNuevo(lat, lon);

        var r = await Uno("nuevo-A", op);

        Assert.Equal(ResultadoOperacion.APLICADA, r.Resultado);
        Assert.Equal(1, r.NuevaVersionActivo);
        Assert.Equal(EstadoEvaluacion.REGISTRADA, r.EstadoEvaluacion);
        await using var db = api.NuevoContexto();
        var activo = await db.Activos.SingleAsync(a => a.Id == op.Evaluacion!.ActivoId);
        Assert.Equal(1, activo.Version);
        Assert.Equal("nuevo-A", activo.CreadoPorDispositivo);
        var version = await db.ActivoVersiones.SingleAsync(v => v.ActivoId == activo.Id);
        Assert.Equal(op.Evaluacion!.Id, version.EvaluacionId);
    }

    [Fact]
    public async Task Activo_nuevo_cerca_de_otro_del_mismo_tipo_es_posible_duplicado()
    {
        var (lat, lon) = FabricaApi.TomarZonaLibre();
        var primero = CrearConActivoNuevo(lat, lon);
        var segundo = CrearConActivoNuevo(lat + 10 / 111_195.0, lon);   // 10 m al norte
        var otroTipo = CrearConActivoNuevo(lat - 10 / 111_195.0, lon, tipo: "puente");

        Assert.Equal(ResultadoOperacion.APLICADA, (await Uno("dup-A", primero)).Resultado);
        var r = await Uno("dup-B", segundo);
        var rOtroTipo = await Uno("dup-B", otroTipo);

        Assert.Equal(ResultadoOperacion.POSIBLE_DUPLICADO, r.Resultado);
        Assert.Equal(EstadoEvaluacion.EN_CONFLICTO, r.EstadoEvaluacion);
        Assert.Equal(ResultadoOperacion.APLICADA, rOtroTipo.Resultado);

        await using var db = api.NuevoContexto();
        var conflicto = await db.Conflictos.SingleAsync(c => c.EvaluacionEntranteId == segundo.Evaluacion!.Id);
        Assert.Equal(TipoConflicto.DUPLICADO, conflicto.Tipo);
        Assert.Equal(primero.Evaluacion!.Id, conflicto.EvaluacionVigenteId);
        Assert.True(await db.Activos.AnyAsync(a => a.Id == segundo.Evaluacion!.ActivoId));
        Assert.True(await HayEvento(db, "EV-10", conflicto.Id.ToString()));
    }

    // ---------- Rama 4: activo existente sin conflicto ----------

    [Fact]
    public async Task Sin_conflicto_aplica_y_sube_la_version_del_activo()
    {
        var activoId = FabricaApi.TomarActivoSembrado();
        var op = Crear(activoId, 1, foto: FotoJpeg());

        var r = await Uno("ok-A", op);

        Assert.Equal(ResultadoOperacion.APLICADA, r.Resultado);
        Assert.Equal(EstadoEvaluacion.REGISTRADA, r.EstadoEvaluacion);
        Assert.Equal(2, r.NuevaVersionActivo);
        await using var db = api.NuevoContexto();
        Assert.Equal(2, (await db.Activos.SingleAsync(a => a.Id == activoId)).Version);
        Assert.Equal("ok-A", (await db.ActivoVersiones.SingleAsync(v => v.ActivoId == activoId && v.Numero == 2)).IdDispositivo);
        Assert.Equal(1, await db.EvaluacionVersiones.CountAsync(v => v.EvaluacionId == op.Evaluacion!.Id));
        var evidencia = await db.Evidencias.SingleAsync(e => e.EvaluacionId == op.Evaluacion!.Id);
        Assert.False(evidencia.Subida);
        Assert.True(await HayEvento(db, "EV-09", op.Evaluacion!.Id.ToString()!));
    }

    [Fact]
    public async Task Sin_desastre_queda_preliminar()
    {
        var r = await Uno("ok-B", Crear(FabricaApi.TomarActivoSembrado(), 1, conDesastre: false));
        Assert.Equal(ResultadoOperacion.APLICADA, r.Resultado);
        Assert.Equal(EstadoEvaluacion.PRELIMINAR, r.EstadoEvaluacion);
    }

    [Fact]
    public async Task Cadena_del_mismo_dispositivo_crear_y_actualizar_con_la_misma_base()
    {
        var activoId = FabricaApi.TomarActivoSembrado();
        var crear = Crear(activoId, 1, nivel: "LEVE");
        var actualizar = Actualizar(crear.Evaluacion!, 1, "GRAVE");

        var respuesta = await FabricaApi.Sincronizar(api.Cliente("cadena-A"), Lote("cadena-A", crear, actualizar));

        Assert.Equal(ResultadoOperacion.APLICADA, respuesta.Resultados[0].Resultado);
        Assert.Equal(ResultadoOperacion.APLICADA, respuesta.Resultados[1].Resultado);
        Assert.Equal(3, respuesta.Resultados[1].NuevaVersionActivo);
        await using var db = api.NuevoContexto();
        var evaluacion = await db.Evaluaciones.SingleAsync(e => e.Id == crear.Evaluacion!.Id);
        Assert.Equal(NivelDano.GRAVE, evaluacion.NivelDano);
        Assert.Equal(2, await db.EvaluacionVersiones.CountAsync(v => v.EvaluacionId == evaluacion.Id));
    }

    // ---------- Rama 4: activo existente con conflicto ----------

    [Fact]
    public async Task Otro_dispositivo_en_medio_da_conflicto_y_la_vigente_queda_intacta()
    {
        var activoId = FabricaApi.TomarActivoSembrado();
        var deB = Crear(activoId, 1, nivel: "LEVE");
        var deA = Crear(activoId, 1, nivel: "DESTRUCCION_TOTAL");

        Assert.Equal(ResultadoOperacion.APLICADA, (await Uno("medio-B", deB)).Resultado);
        var r = await Uno("medio-A", deA);

        Assert.Equal(ResultadoOperacion.EN_CONFLICTO, r.Resultado);
        Assert.Equal(EstadoEvaluacion.EN_CONFLICTO, r.EstadoEvaluacion);
        Assert.Null(r.NuevaVersionActivo);

        await using var db = api.NuevoContexto();
        Assert.Equal(2, (await db.Activos.SingleAsync(a => a.Id == activoId)).Version);
        var vigente = await db.Evaluaciones.SingleAsync(e => e.Id == deB.Evaluacion!.Id);
        Assert.Equal(NivelDano.LEVE, vigente.NivelDano);
        Assert.Equal(EstadoEvaluacion.REGISTRADA, vigente.Estado);
        var entrante = await db.Evaluaciones.SingleAsync(e => e.Id == deA.Evaluacion!.Id);
        Assert.Equal(EstadoEvaluacion.EN_CONFLICTO, entrante.Estado);
        Assert.Equal(NivelDano.DESTRUCCION_TOTAL, entrante.NivelDano);

        var conflicto = await db.Conflictos.SingleAsync(c => c.EvaluacionEntranteId == deA.Evaluacion!.Id);
        Assert.Equal(TipoConflicto.VERSION, conflicto.Tipo);
        Assert.Equal(deB.Evaluacion!.Id, conflicto.EvaluacionVigenteId);
        Assert.Contains("medio-B", conflicto.Regla);
        Assert.True(await HayEvento(db, "EV-10", conflicto.Id.ToString()));

        var revision = await api.Cliente("revisor").GetFromJsonAsync<List<ConflictoDto>>("/v1/conflictos?estado=PENDIENTE", Json.Opciones);
        var dto = Assert.Single(revision!, c => c.Id == conflicto.Id);
        Assert.Equal(deB.Evaluacion.Id, dto.Vigente!.EvaluacionId);
        Assert.Equal("LEVE", dto.Vigente.Contenido.GetProperty("nivelDano").GetString());
        Assert.Equal("DESTRUCCION_TOTAL", dto.Entrante.Contenido.GetProperty("nivelDano").GetString());
    }

    [Fact]
    public async Task Actualizar_en_conflicto_guarda_la_version_y_no_toca_el_activo()
    {
        var activoId = FabricaApi.TomarActivoSembrado();
        var deA = Crear(activoId, 1, nivel: "LEVE");
        Assert.Equal(ResultadoOperacion.APLICADA, (await Uno("act-A", deA)).Resultado);          // v2 (A)
        Assert.Equal(ResultadoOperacion.APLICADA, (await Uno("act-B", Crear(activoId, 2))).Resultado); // v3 (B)

        var r = await Uno("act-A", Actualizar(deA.Evaluacion!, 2, "MODERADO"));

        Assert.Equal(ResultadoOperacion.EN_CONFLICTO, r.Resultado);
        await using var db = api.NuevoContexto();
        Assert.Equal(3, (await db.Activos.SingleAsync(a => a.Id == activoId)).Version);
        var evaluacion = await db.Evaluaciones.SingleAsync(e => e.Id == deA.Evaluacion!.Id);
        Assert.Equal(NivelDano.LEVE, evaluacion.NivelDano);   // el contenido vigente no cambia
        Assert.Equal(EstadoEvaluacion.EN_CONFLICTO, evaluacion.Estado);
        var versiones = await db.EvaluacionVersiones.Where(v => v.EvaluacionId == evaluacion.Id).OrderBy(v => v.NumeroVersion).ToListAsync();
        Assert.Equal(2, versiones.Count);
        Assert.True(versiones[1].EnConflicto);
    }

    // ---------- Concurrencia ----------

    [Fact]
    public async Task Dos_lotes_concurrentes_sobre_el_mismo_activo_uno_aplica_y_otro_queda_en_conflicto()
    {
        for (int intento = 0; intento < 5; intento++)
        {
            var activoId = FabricaApi.TomarActivoSembrado();
            var loteA = Lote("conc-A", Crear(activoId, 1));
            var loteB = Lote("conc-B", Crear(activoId, 1));

            var tareas = new[]
            {
                FabricaApi.Sincronizar(api.Cliente("conc-A"), loteA),
                FabricaApi.Sincronizar(api.Cliente("conc-B"), loteB)
            };
            var respuestas = await Task.WhenAll(tareas);
            var resultados = respuestas.Select(x => x.Resultados[0].Resultado).OrderBy(x => x).ToList();

            Assert.Equal([ResultadoOperacion.APLICADA, ResultadoOperacion.EN_CONFLICTO], resultados);
            await using var db = api.NuevoContexto();
            Assert.Equal(2, (await db.Activos.SingleAsync(a => a.Id == activoId)).Version);
            Assert.Equal(1, await db.Conflictos.CountAsync(c => c.ActivoId == activoId));
        }
    }

    // ---------- Contrato ----------

    [Fact]
    public async Task Sin_encabezados_responde_400()
    {
        var cliente = api.CreateClient();
        cliente.DefaultRequestHeaders.Add("X-Usuario", "Ana");
        var respuesta = await cliente.PostAsJsonAsync("/v1/sincronizacion/evaluaciones", Lote("x"), Json.Opciones);
        Assert.Equal(HttpStatusCode.BadRequest, respuesta.StatusCode);
        Assert.Contains("X-Organizacion", await respuesta.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task Lote_con_mas_de_100_operaciones_responde_400()
    {
        var operaciones = Enumerable.Range(0, 101).Select(_ => Crear(Guid.NewGuid(), 1)).ToArray();
        var respuesta = await api.Cliente("grande").PostAsJsonAsync("/v1/sincronizacion/evaluaciones",
            Lote("grande", operaciones), Json.Opciones);
        Assert.Equal(HttpStatusCode.BadRequest, respuesta.StatusCode);
    }

    [Fact]
    public async Task Los_resultados_van_en_el_orden_de_las_operaciones()
    {
        var ops = new[]
        {
            Crear(Guid.NewGuid(), 1),
            Crear(FabricaApi.TomarActivoSembrado(), 1),
            Crear(Guid.NewGuid(), 1)
        };
        var respuesta = await FabricaApi.Sincronizar(api.Cliente("orden"), Lote("orden", ops));
        Assert.Equal(ops.Select(o => o.IdOperacion!.Value), respuesta.Resultados.Select(r => r.IdOperacion));
    }

    [Fact]
    public async Task Descarga_trae_catalogo_desastres_y_activos_con_version()
    {
        var descarga = await api.Cliente("descarga").GetFromJsonAsync<DescargaDto>("/v1/descarga", Json.Opciones);

        Assert.Equal(11, descarga!.TiposActivo.Count);
        Assert.All(descarga.TiposActivo, t => Assert.Equal(5, t.Criterios.Count));
        var vivienda = descarga.TiposActivo.Single(t => t.Codigo == "vivienda");
        Assert.Equal("Fisuras no estructurales", vivienda.Criterios.Single(c => c.Nivel == NivelDano.MODERADO).Criterio);
        Assert.Equal(3, descarga.Desastres.Count);
        Assert.True(descarga.Activos.Count >= ActivosDePrueba.Total);
        Assert.Contains(descarga.Activos, a => a.Id == ActivosDePrueba.Id(ActivosDePrueba.Total));
    }

    [Fact]
    public async Task Evaluaciones_por_dispositivo_trae_estado_y_versiones()
    {
        var op = Crear(FabricaApi.TomarActivoSembrado(), 1);
        await Uno("listado-A", op);

        var lista = await api.Cliente("listado-A").GetFromJsonAsync<List<EvaluacionResumenDto>>(
            "/v1/evaluaciones?idDispositivo=listado-A", Json.Opciones);

        var e = Assert.Single(lista!);
        Assert.Equal(op.Evaluacion!.Id, e.Id);
        Assert.Equal(1, e.NumeroVersiones);
    }

    [Fact]
    public async Task Salud_responde_200()
    {
        var respuesta = await api.CreateClient().GetAsync("/salud");
        Assert.Equal(HttpStatusCode.OK, respuesta.StatusCode);
    }

    // ---------- Historial de solo inserción ----------

    [Fact]
    public async Task Modificar_o_borrar_una_EvaluacionVersion_lanza_excepcion()
    {
        var op = Crear(FabricaApi.TomarActivoSembrado(), 1);
        await Uno("historial", op);

        await using (var db = api.NuevoContexto())
        {
            var version = await db.EvaluacionVersiones.FirstAsync(v => v.EvaluacionId == op.Evaluacion!.Id);
            version.Autor = "otro";
            await Assert.ThrowsAsync<HistorialInmutableException>(() => db.SaveChangesAsync());
        }
        await using (var db = api.NuevoContexto())
        {
            var version = await db.EvaluacionVersiones.FirstAsync(v => v.EvaluacionId == op.Evaluacion!.Id);
            db.EvaluacionVersiones.Remove(version);
            Assert.Throws<HistorialInmutableException>(() => db.SaveChanges());
        }
    }

    /// <summary>PayloadJson es jsonb: se filtra en memoria (LIKE no aplica a jsonb).</summary>
    private static async Task<bool> HayEvento(DanosDbContext db, string tipo, string texto)
    {
        var payloads = await db.EventosSalientes.Where(e => e.Tipo == tipo).Select(e => e.PayloadJson).ToListAsync();
        return payloads.Any(p => p.Contains(texto));
    }

    private async Task<ResultadoOperacionDto> Uno(string dispositivo, OperacionDto op)
    {
        var respuesta = await FabricaApi.Sincronizar(api.Cliente(dispositivo), Lote(dispositivo, op));
        return Assert.Single(respuesta.Resultados);
    }

    private async Task<(int, int, int, int, int, int, int, int)> Contar()
    {
        await using var db = api.NuevoContexto();
        return (await db.Evaluaciones.CountAsync(), await db.EvaluacionVersiones.CountAsync(),
            await db.Activos.CountAsync(), await db.ActivoVersiones.CountAsync(),
            await db.Conflictos.CountAsync(), await db.OperacionesProcesadas.CountAsync(),
            await db.EventosSalientes.CountAsync(), await db.Evidencias.CountAsync());
    }
}
