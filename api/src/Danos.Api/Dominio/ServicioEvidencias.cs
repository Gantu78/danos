using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace Danos.Api.Dominio;

public enum TipoResultadoSubida
{
    Subida,
    NoDeclarada,
    MuyGrande,
    HashDistinto
}

public sealed record ResultadoSubida(TipoResultadoSubida Tipo, EvidenciaSubidaDto? Evidencia = null, string? Mensaje = null);

/// <summary>
/// PUT /v1/evidencias/{id}. Idempotente: una foto ya subida con el mismo hash responde 200 sin reescribir.
/// El archivo se acepta solo si su SHA-256 coincide con el que la app declaró en el lote.
/// </summary>
public class ServicioEvidencias(DanosDbContext db, AlmacenArchivos almacen, IOptions<OpcionesEvidencias> opciones)
{
    public async Task<ResultadoSubida> SubirAsync(Guid id, Stream contenido, long tamanoDeclarado, CancellationToken cancelar)
    {
        var evidencia = await (from e in db.Evidencias where e.Id == id select e).FirstOrDefaultAsync(cancelar);
        if (evidencia is null)
        {
            return new ResultadoSubida(TipoResultadoSubida.NoDeclarada);
        }

        long maximo = opciones.Value.TamanoMaximoMb * 1024L * 1024L;
        if (tamanoDeclarado > maximo)
        {
            return MuyGrande(maximo);
        }

        var temporal = await almacen.GuardarTemporalAsync(contenido, cancelar);
        try
        {
            if (temporal.Bytes > maximo)
            {
                return MuyGrande(maximo);
            }
            if (!string.Equals(temporal.Sha256, evidencia.Sha256Declarado, StringComparison.OrdinalIgnoreCase))
            {
                return new ResultadoSubida(TipoResultadoSubida.HashDistinto, Mensaje:
                    $"El SHA-256 del archivo ({temporal.Sha256}) no coincide con el declarado ({evidencia.Sha256Declarado})");
            }

            var rutaFinal = almacen.RutaFinal(evidencia.EvaluacionId, evidencia.Id);
            if (evidencia.Subida && evidencia.RutaArchivo == rutaFinal && File.Exists(rutaFinal))
            {
                return Subida(evidencia);   // reenvío: mismo hash, no se reescribe
            }

            almacen.MoverAFinal(temporal, rutaFinal);
            evidencia.Subida = true;
            evidencia.RutaArchivo = rutaFinal;
            evidencia.InstanteSubida = DateTime.UtcNow;
            evidencia.TamanoBytes = temporal.Bytes;
            await db.SaveChangesAsync(cancelar);
            return Subida(evidencia);
        }
        finally
        {
            almacen.Descartar(temporal);
        }
    }

    private static ResultadoSubida Subida(Evidencia e) =>
        new(TipoResultadoSubida.Subida, new EvidenciaSubidaDto(e.Id, e.EvaluacionId, e.Subida, e.InstanteSubida, e.TamanoBytes));

    private static ResultadoSubida MuyGrande(long maximo) =>
        new(TipoResultadoSubida.MuyGrande, Mensaje: $"El archivo supera el máximo de {maximo / (1024 * 1024)} MB");
}
