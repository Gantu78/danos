using Danos.Api.Acceso;

namespace Danos.Api.Dominio;

/// <summary>
/// Revisa lo que se puede decidir sin consultar evaluaciones ni activos: atributos obligatorios,
/// catálogo, nivel, desastre, coordenadas y evidencias. Devuelve el motivo del rechazo o null si es válida.
/// Las reglas que dependen de lo guardado (id repetido, dueño de la evaluación, activo inexistente)
/// las revisa ServicioSincronizacion.
/// </summary>
public static class ValidadorEvaluacion
{
    public static string? Validar(OperacionDto op, IReadOnlySet<string> tiposActivo, IReadOnlySet<Guid> desastres)
    {
        if (op.IdOperacion is null || op.IdOperacion == Guid.Empty)
        {
            return Falta("idOperacion");
        }
        if (string.IsNullOrWhiteSpace(op.Tipo))
        {
            return Falta("tipo");
        }
        if (!EsNombreDeEnum<TipoOperacion>(op.Tipo))
        {
            return $"El tipo de operación '{op.Tipo}' no es válido (CREAR o ACTUALIZAR)";
        }

        var e = op.Evaluacion;
        if (e is null)
        {
            return Falta("evaluacion");
        }

        // Todos los atributos son obligatorios menos desastreId y evidencias.
        if (e.Id is null || e.Id == Guid.Empty) return Falta("id");
        if (e.ActivoId is null || e.ActivoId == Guid.Empty) return Falta("activoId");
        if (e.InstanteCampo is null) return Falta("instanteCampo");
        if (string.IsNullOrWhiteSpace(e.Evaluador)) return Falta("evaluador");
        if (string.IsNullOrWhiteSpace(e.Organizacion)) return Falta("organizacion");
        if (e.Lat is null) return Falta("lat");
        if (e.Lon is null) return Falta("lon");
        if (e.PrecisionM is null) return Falta("precisionM");
        if (string.IsNullOrWhiteSpace(e.TipoActivoCodigo)) return Falta("tipoActivoCodigo");
        if (string.IsNullOrWhiteSpace(e.NivelDano)) return Falta("nivelDano");
        if (string.IsNullOrWhiteSpace(e.Descripcion)) return Falta("descripcion");

        // Un texto más largo que su columna haría fallar el INSERT en cada reenvío: se rechaza aquí.
        string? largo = Largo("evaluador", e.Evaluador, Limites.Nombre)
            ?? Largo("organizacion", e.Organizacion, Limites.Nombre)
            ?? Largo("descripcion", e.Descripcion, Limites.Descripcion);
        if (largo != null)
        {
            return largo;
        }
        if (!InstanteRazonable(e.InstanteCampo.Value))
        {
            return $"El instanteCampo {e.InstanteCampo.Value:O} está fuera del rango aceptado ({Minimo:yyyy} a {Maximo:yyyy})";
        }

        if (!tiposActivo.Contains(e.TipoActivoCodigo))
        {
            return $"El tipo de activo '{e.TipoActivoCodigo}' no existe";
        }
        if (!EsNombreDeEnum<NivelDano>(e.NivelDano))
        {
            return $"El nivel de daño '{e.NivelDano}' no es válido";
        }
        if (e.DesastreId is not null && !desastres.Contains(e.DesastreId.Value))
        {
            return $"El desastre {e.DesastreId} no existe";
        }
        if (!CoordenadasValidas(e.Lat.Value, e.Lon.Value))
        {
            return "Las coordenadas de la evaluación están fuera de rango";
        }
        if (!double.IsFinite(e.PrecisionM.Value) || e.PrecisionM.Value < 0)
        {
            return "La precisionM debe ser un número mayor o igual a 0";
        }

        var nuevo = e.ActivoNuevo;
        if (nuevo is not null)
        {
            if (nuevo.Id != e.ActivoId)
            {
                return "activoNuevo.id debe ser igual a activoId";
            }
            if (string.IsNullOrWhiteSpace(nuevo.TipoCodigo) || !tiposActivo.Contains(nuevo.TipoCodigo))
            {
                return $"activoNuevo: el tipo de activo '{nuevo.TipoCodigo}' no existe";
            }
            if (nuevo.Lat is null || nuevo.Lon is null || !CoordenadasValidas(nuevo.Lat.Value, nuevo.Lon.Value))
            {
                return "activoNuevo: coordenadas faltantes o fuera de rango";
            }
            string? referencia = Largo("activoNuevo.referencia", nuevo.Referencia, Limites.Referencia);
            if (referencia != null)
            {
                return referencia;
            }
        }

        if (e.Evidencias is not null)
        {
            var vistas = new HashSet<Guid>();
            for (int i = 0; i < e.Evidencias.Count; i++)
            {
                var ev = e.Evidencias[i];
                string? motivo = ValidarEvidencia(ev);
                if (motivo != null)
                {
                    return $"evidencia {i + 1}: {motivo}";
                }
                if (!vistas.Add(ev.Id!.Value))
                {
                    return $"evidencia {i + 1}: id repetido en la misma evaluación";
                }
            }
        }
        return null;
    }

    private static string? ValidarEvidencia(EvidenciaDeclaradaDto ev)
    {
        if (ev.Id is null || ev.Id == Guid.Empty) return "falta id";
        if (ev.Lat is null || ev.Lon is null || !CoordenadasValidas(ev.Lat.Value, ev.Lon.Value))
            return "coordenadas faltantes o fuera de rango";
        if (ev.InstanteCaptura is null) return "falta instanteCaptura";
        if (!InstanteRazonable(ev.InstanteCaptura.Value)) return "instanteCaptura fuera del rango aceptado";
        if (ev.TamanoBytes is null || ev.TamanoBytes <= 0) return "tamanoBytes debe ser mayor que 0";
        if (!EsSha256(ev.Sha256)) return "sha256 debe tener 64 caracteres hexadecimales";
        return null;
    }

    // Rango amplio a propósito: solo descarta valores por defecto (0001-01-01) o absurdos que romperían
    // el cálculo de la ventana de duplicados. Un celular con el reloj reiniciado (1970) se acepta.
    private static readonly DateTimeOffset Minimo = new(1900, 1, 1, 0, 0, 0, TimeSpan.Zero);
    private static readonly DateTimeOffset Maximo = new(2200, 1, 1, 0, 0, 0, TimeSpan.Zero);

    public static bool InstanteRazonable(DateTimeOffset instante) => instante >= Minimo && instante < Maximo;

    private static string? Largo(string campo, string? texto, int maximo) =>
        texto != null && texto.Length > maximo
            ? $"El atributo '{campo}' supera los {maximo} caracteres ({texto.Length})"
            : null;

    public static bool CoordenadasValidas(double lat, double lon) =>
        double.IsFinite(lat) && double.IsFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;

    public static bool EsSha256(string? texto)
    {
        if (texto is null || texto.Length != 64)
        {
            return false;
        }
        foreach (char c in texto)
        {
            if (!Uri.IsHexDigit(c))
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>Solo el nombre exacto ("GRAVE"): Enum.TryParse también aceptaría "3" o "grave".</summary>
    public static bool EsNombreDeEnum<T>(string texto) where T : struct, Enum
    {
        foreach (var nombre in Enum.GetNames<T>())
        {
            if (nombre == texto)
            {
                return true;
            }
        }
        return false;
    }

    private static string Falta(string campo) => $"Falta el atributo obligatorio '{campo}'";
}
