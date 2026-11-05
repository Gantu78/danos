using Danos.Api.Dominio;
using Microsoft.AspNetCore.Mvc;

namespace Danos.Api.Acceso;

// Descarga previa (RF-ED-12): todo lo que la app necesita para trabajar sin red.
[ApiController]
[Route("v1/descarga")]
public class DescargaController(ServicioDescarga descarga) : ControllerBase
{
    [HttpGet]
    public async Task<DescargaDto> Obtener([FromQuery] Guid? desastreId) => await descarga.ObtenerAsync(desastreId);
}
