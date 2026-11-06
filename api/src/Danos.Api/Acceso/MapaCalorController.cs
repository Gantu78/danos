using Danos.Api.Dominio;
using Microsoft.AspNetCore.Mvc;

namespace Danos.Api.Acceso;

// Puntos [lat, lon, peso] de la evaluación vigente de cada activo (RF-ED-11, AD-16).
// En el diseño completo esto es del Servicio de Consulta de Daños (/v1/consulta-danos/mapa); por ahora
// lo sirve este API leyendo su propia base (simplificación declarada).
[ApiController]
[Route("v1/mapa-calor")]
public class MapaCalorController(ServicioMapaCalor mapa) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<MapaCalorDto>> Obtener([FromQuery] Guid? desastreId, [FromQuery] string? nivelMinimo)
    {
        if (desastreId is null)
        {
            return BadRequest(new ErrorDto("Falta el parámetro desastreId"));
        }
        var nivel = NivelDano.SIN_DANO;
        if (!string.IsNullOrWhiteSpace(nivelMinimo))
        {
            if (!ValidadorEvaluacion.EsNombreDeEnum<NivelDano>(nivelMinimo))
            {
                return BadRequest(new ErrorDto($"nivelMinimo '{nivelMinimo}' no es un nivel de daño válido"));
            }
            nivel = Enum.Parse<NivelDano>(nivelMinimo);
        }
        var resultado = await mapa.ObtenerAsync(desastreId.Value, nivel);
        if (resultado is null)
        {
            return NotFound(new ErrorDto($"No existe el desastre {desastreId}"));
        }
        return resultado;
    }
}
