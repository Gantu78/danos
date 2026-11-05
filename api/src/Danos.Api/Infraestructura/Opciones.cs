using System.Text.Json;
using System.Text.Json.Serialization;

namespace Danos.Api.Infraestructura;

// Secciones de appsettings.json, leídas con IOptions<T>.

public class OpcionesEvidencias
{
    public const string Seccion = "Evidencias";
    public string Ruta { get; set; } = "evidencias";
    public int TamanoMaximoMb { get; set; } = 10;
}

public class OpcionesDuplicados
{
    public const string Seccion = "Duplicados";
    public double DistanciaMetros { get; set; } = 25;
    public double VentanaHoras { get; set; } = 24;
}

public class OpcionesSincronizacion
{
    public const string Seccion = "Sincronizacion";
    public int MaxOperacionesPorLote { get; set; } = 100;
}

/// <summary>
/// Las mismas opciones de JSON para las respuestas HTTP y para lo que se guarda en jsonb
/// (resultados, contenidos de versión, eventos): camelCase, enums como texto, sin nulos.
/// </summary>
public static class Json
{
    public static readonly JsonSerializerOptions Opciones = Configurar(new JsonSerializerOptions(JsonSerializerDefaults.Web));

    public static JsonSerializerOptions Configurar(JsonSerializerOptions o)
    {
        o.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
        o.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        o.Converters.Add(new JsonStringEnumConverter(allowIntegerValues: false));
        return o;
    }

    public static string Serializar<T>(T valor) => JsonSerializer.Serialize(valor, Opciones);

    public static T Deserializar<T>(string texto) =>
        JsonSerializer.Deserialize<T>(texto, Opciones) ?? throw new InvalidOperationException("JSON vacío");
}
