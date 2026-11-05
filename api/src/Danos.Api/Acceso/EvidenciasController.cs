using Danos.Api.Dominio;
using Microsoft.AspNetCore.Mvc;

namespace Danos.Api.Acceso;

// Subida de fotos de evidencia (multipart, parte "archivo"). Idempotente por id.
[ApiController]
[Route("v1/evidencias")]
public class EvidenciasController(ServicioEvidencias evidencias) : ControllerBase
{
    [HttpPut("{id:guid}")]
    [RequestSizeLimit(64 * 1024 * 1024)]   // el tope real (TamanoMaximoMb) lo aplica el servicio con 413
    [RequestFormLimits(MultipartBodyLengthLimit = 64 * 1024 * 1024)]
    public async Task<IActionResult> Subir(Guid id, IFormFile? archivo, CancellationToken cancelar)
    {
        if (archivo is null)
        {
            return BadRequest(new ErrorDto("Falta la parte 'archivo' en el multipart"));
        }

        await using var contenido = archivo.OpenReadStream();
        var resultado = await evidencias.SubirAsync(id, contenido, archivo.Length, cancelar);

        return resultado.Tipo switch
        {
            TipoResultadoSubida.Subida => Ok(resultado.Evidencia),
            TipoResultadoSubida.NoDeclarada => NotFound(new ErrorDto($"La evidencia {id} no fue declarada en ningún lote")),
            TipoResultadoSubida.MuyGrande => StatusCode(StatusCodes.Status413PayloadTooLarge,
                new ErrorDto(resultado.Mensaje ?? "Archivo demasiado grande")),
            TipoResultadoSubida.HashDistinto => UnprocessableEntity(new ErrorDto(resultado.Mensaje ?? "El hash no coincide")),
            _ => StatusCode(StatusCodes.Status500InternalServerError)
        };
    }
}
