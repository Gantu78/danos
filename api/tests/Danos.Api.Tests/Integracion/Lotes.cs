using System.Security.Cryptography;
using Danos.Api.Acceso;
using Danos.Api.Infraestructura;

namespace Danos.Api.Tests.Integracion;

/// <summary>Arma lotes y operaciones como los manda la app.</summary>
public static class Lotes
{
    public static LoteSincronizacionDto Lote(string dispositivo, params OperacionDto[] operaciones) => new()
    {
        IdDispositivo = dispositivo,
        InstanteEnvioDispositivo = DateTimeOffset.UtcNow,
        Operaciones = [.. operaciones]
    };

    public static OperacionDto Crear(Guid activoId, int? versionBase, string nivel = "GRAVE", Guid? desastreId = null,
        bool conDesastre = true, byte[]? foto = null)
    {
        var evaluacion = Evaluacion(activoId, nivel, conDesastre ? desastreId ?? Semilla.DesastreInundacion : null);
        if (foto != null)
        {
            evaluacion.Evidencias =
            [
                new EvidenciaDeclaradaDto
                {
                    Id = Guid.NewGuid(), Lat = evaluacion.Lat, Lon = evaluacion.Lon,
                    InstanteCaptura = DateTimeOffset.UtcNow, Sha256 = Sha256(foto), TamanoBytes = foto.Length
                }
            ];
        }
        return new OperacionDto { IdOperacion = Guid.NewGuid(), Tipo = "CREAR", VersionBaseActivo = versionBase, Evaluacion = evaluacion };
    }

    public static OperacionDto Actualizar(EvaluacionEntranteDto original, int? versionBase, string nivel) => new()
    {
        IdOperacion = Guid.NewGuid(),
        Tipo = "ACTUALIZAR",
        VersionBaseActivo = versionBase,
        Evaluacion = new EvaluacionEntranteDto
        {
            Id = original.Id,
            ActivoId = original.ActivoId,
            DesastreId = original.DesastreId,
            InstanteCampo = DateTimeOffset.UtcNow,
            Evaluador = original.Evaluador,
            Organizacion = original.Organizacion,
            Lat = original.Lat,
            Lon = original.Lon,
            PrecisionM = original.PrecisionM,
            TipoActivoCodigo = original.TipoActivoCodigo,
            NivelDano = nivel,
            Descripcion = "Actualización: " + nivel
        }
    };

    public static OperacionDto CrearConActivoNuevo(double lat, double lon, string tipo = "vivienda", DateTimeOffset? instante = null)
    {
        var activoId = Guid.NewGuid();
        var evaluacion = Evaluacion(activoId, "MODERADO", Semilla.DesastreInundacion);
        evaluacion.Lat = lat;
        evaluacion.Lon = lon;
        evaluacion.TipoActivoCodigo = tipo;
        evaluacion.InstanteCampo = instante ?? DateTimeOffset.UtcNow;
        evaluacion.ActivoNuevo = new ActivoNuevoDto { Id = activoId, TipoCodigo = tipo, Lat = lat, Lon = lon, Referencia = "Activo creado en campo" };
        return new OperacionDto { IdOperacion = Guid.NewGuid(), Tipo = "CREAR", VersionBaseActivo = null, Evaluacion = evaluacion };
    }

    public static EvaluacionEntranteDto Evaluacion(Guid activoId, string nivel, Guid? desastreId) => new()
    {
        Id = Guid.NewGuid(),
        ActivoId = activoId,
        DesastreId = desastreId,
        InstanteCampo = DateTimeOffset.UtcNow.AddMinutes(-30),
        Evaluador = "Ana Pérez",
        Organizacion = "Cruz Roja",
        Lat = 4.6,
        Lon = -74.1,
        PrecisionM = 6,
        TipoActivoCodigo = "vivienda",
        NivelDano = nivel,
        Descripcion = "Evaluación de prueba",
        Evidencias = []
    };

    public static byte[] FotoJpeg(int tamano = 2048)
    {
        var bytes = new byte[tamano];
        Random.Shared.NextBytes(bytes);
        bytes[0] = 0xFF; bytes[1] = 0xD8; bytes[^2] = 0xFF; bytes[^1] = 0xD9;
        return bytes;
    }

    public static string Sha256(byte[] contenido) => Convert.ToHexStringLower(SHA256.HashData(contenido));
}
