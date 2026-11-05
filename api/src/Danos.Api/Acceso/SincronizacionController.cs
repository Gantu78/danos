using Danos.Api.Dominio;
using Danos.Api.Infraestructura;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace Danos.Api.Acceso;

// Recibe el lote de operaciones de la app y devuelve un resultado por operación, en el mismo orden.
[ApiController]
[Route("v1/sincronizacion")]
public class SincronizacionController(ServicioSincronizacion sincronizacion, IOptions<OpcionesSincronizacion> opciones)
    : ControllerBase
{
    [HttpPost("evaluaciones")]
    public async Task<ActionResult<RespuestaSincronizacionDto>> Evaluaciones(LoteSincronizacionDto lote, CancellationToken cancelar)
    {
        // Se toma al llegar, antes de cualquier trabajo, para medir bien el desfase de reloj del celular.
        var instanteRecepcion = DateTime.UtcNow;

        if (string.IsNullOrWhiteSpace(lote.IdDispositivo))
        {
            return BadRequest(new ErrorDto("Falta idDispositivo en el lote"));
        }
        if (lote.IdDispositivo.Length > Limites.IdDispositivo)
        {
            return BadRequest(new ErrorDto($"idDispositivo supera los {Limites.IdDispositivo} caracteres"));
        }
        // La dueña de cada evaluación es el dispositivo del cuerpo: debe ser el mismo que se identificó.
        if (lote.IdDispositivo != Request.Headers[FiltroEncabezados.Dispositivo].ToString())
        {
            return BadRequest(new ErrorDto($"idDispositivo del cuerpo no coincide con el encabezado {FiltroEncabezados.Dispositivo}"));
        }
        if (lote.Operaciones is null)
        {
            return BadRequest(new ErrorDto("Falta la lista de operaciones"));
        }
        int maximo = opciones.Value.MaxOperacionesPorLote;
        if (lote.Operaciones.Count > maximo)
        {
            return BadRequest(new ErrorDto($"El lote trae {lote.Operaciones.Count} operaciones; el máximo es {maximo}"));
        }

        try
        {
            var resultados = await sincronizacion.ProcesarLoteAsync(lote, instanteRecepcion, cancelar);
            return new RespuestaSincronizacionDto(resultados);
        }
        catch (LoteIncompletoException ex)
        {
            // Sin 2xx la app conserva todo el lote en su cola y lo reenvía; lo ya procesado es idempotente.
            return StatusCode(StatusCodes.Status503ServiceUnavailable, new ErrorDto(ex.Message));
        }
    }
}
