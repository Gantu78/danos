using Danos.Api.Dominio;
using Microsoft.AspNetCore.Mvc;

namespace Danos.Api.Acceso;

// Endpoints de revisión y pruebas (no los usa la app).
[ApiController]
[Route("v1")]
public class RevisionController(ServicioRevision revision) : ControllerBase
{
    /// <summary>Conflictos con ambas versiones completas, para revisión humana.</summary>
    [HttpGet("conflictos")]
    public async Task<ActionResult<List<ConflictoDto>>> Conflictos([FromQuery] string estado = "PENDIENTE")
    {
        if (!ValidadorEvaluacion.EsNombreDeEnum<EstadoConflicto>(estado))
        {
            return BadRequest(new ErrorDto($"Estado '{estado}' no válido (PENDIENTE o RESUELTO)"));
        }
        return await revision.ConflictosAsync(Enum.Parse<EstadoConflicto>(estado));
    }

    /// <summary>Evaluaciones de un dispositivo con su estado y número de versiones.</summary>
    [HttpGet("evaluaciones")]
    public async Task<ActionResult<List<EvaluacionResumenDto>>> Evaluaciones([FromQuery] string? idDispositivo)
    {
        if (string.IsNullOrWhiteSpace(idDispositivo))
        {
            return BadRequest(new ErrorDto("Falta el parámetro idDispositivo"));
        }
        return await revision.EvaluacionesAsync(idDispositivo);
    }
}
