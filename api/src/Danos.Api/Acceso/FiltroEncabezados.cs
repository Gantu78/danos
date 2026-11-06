using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;

namespace Danos.Api.Acceso;

// Sin API Gateway ni contraseñas (simplificación declarada): el evaluador se identifica con encabezados.
// Todo lo que cuelga de /v1 exige los tres; si falta alguno, 400.
// Es un filtro de recurso (no de acción) para responder ANTES de leer el cuerpo: así una foto de 10 MB
// sin encabezados no se alcanza a recibir completa.
public class FiltroEncabezados : IResourceFilter
{
    public const string Usuario = "X-Usuario";
    public const string Organizacion = "X-Organizacion";
    public const string Dispositivo = "X-Dispositivo";
    private static readonly string[] Obligatorios = [Usuario, Organizacion, Dispositivo];

    public void OnResourceExecuting(ResourceExecutingContext contexto)
    {
        var peticion = contexto.HttpContext.Request;
        if (!peticion.Path.StartsWithSegments("/v1"))
        {
            return;
        }
        var faltan = new List<string>();
        foreach (var nombre in Obligatorios)
        {
            if (string.IsNullOrWhiteSpace(peticion.Headers[nombre]))
            {
                faltan.Add(nombre);
            }
        }
        if (faltan.Count > 0)
        {
            contexto.Result = new BadRequestObjectResult(
                new ErrorDto($"Faltan encabezados obligatorios: {string.Join(", ", faltan)}"));
        }
    }

    public void OnResourceExecuted(ResourceExecutedContext contexto)
    {
    }
}
