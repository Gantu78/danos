using Danos.Api.Acceso;
using Danos.Api.Dominio;
using Danos.Api.Infraestructura;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

// Microservicio de Evaluación de Daños (base propia, AD-01). Recibe por lotes las evaluaciones que los
// evaluadores registraron sin conexión, detecta conflictos y duplicados sin resolverlos (QA-14) y sirve
// el mapa de calor de lo ya sincronizado.
// Escucha en http://0.0.0.0:5080 (appsettings.json → Kestrel) para que lo alcancen el emulador (10.0.2.2)
// y un celular en la LAN. Sin TLS: simplificación declarada.
var builder = WebApplication.CreateBuilder(args);

// X-Usuario lleva el nombre del evaluador ("Ana Pérez"): sin esto Kestrel rechaza con 400 cualquier
// encabezado con tildes. Se aceptan en UTF-8.
builder.WebHost.ConfigureKestrel(o => o.RequestHeaderEncodingSelector = _ => System.Text.Encoding.UTF8);

builder.Services
    .AddControllers(o => o.Filters.Add<FiltroEncabezados>())
    .AddJsonOptions(o => Json.Configurar(o.JsonSerializerOptions))
    // Si el JSON llega mal formado se responde 400 con { "error": "..." }, como el resto de errores.
    .ConfigureApiBehaviorOptions(o => o.InvalidModelStateResponseFactory = contexto =>
        new BadRequestObjectResult(new ErrorDto(PrimerError(contexto.ModelState))));
builder.Services.AddRazorPages();
// Que Razor no convierta las tildes en entidades (&#xF3;); sigue escapando <, >, & y comillas.
builder.Services.Configure<Microsoft.Extensions.WebEncoders.WebEncoderOptions>(o =>
    o.TextEncoderSettings = new System.Text.Encodings.Web.TextEncoderSettings(System.Text.Unicode.UnicodeRanges.All));
builder.Services.AddOpenApi();

// La cadena se lee al crear cada DbContext (no al arrancar), así las pruebas pueden apuntar a otra base.
// Se configura en appsettings.Development.json → ConnectionStrings:Danos.
builder.Services.AddDbContext<DanosDbContext>((servicios, o) =>
    o.UseNpgsql(servicios.GetRequiredService<IConfiguration>().GetConnectionString("Danos")
        ?? throw new InvalidOperationException("Falta ConnectionStrings:Danos en la configuración.")));

builder.Services.Configure<OpcionesEvidencias>(builder.Configuration.GetSection(OpcionesEvidencias.Seccion));
builder.Services.Configure<OpcionesDuplicados>(builder.Configuration.GetSection(OpcionesDuplicados.Seccion));
builder.Services.Configure<OpcionesSincronizacion>(builder.Configuration.GetSection(OpcionesSincronizacion.Seccion));

builder.Services.AddScoped<ServicioDescarga>();
builder.Services.AddScoped<ServicioSincronizacion>();
builder.Services.AddScoped<ServicioEvidencias>();
builder.Services.AddScoped<ServicioRevision>();
builder.Services.AddScoped<ServicioMapaCalor>();
builder.Services.AddSingleton<AlmacenArchivos>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    await Semilla.EjecutarAsync(app.Services, app.Logger);
    app.MapOpenApi();
}

app.UseStaticFiles();
app.MapGet("/salud", () => Results.Ok(new { estado = "ok", instante = DateTime.UtcNow }));
app.MapControllers();
app.MapRazorPages();
app.Run();

static string PrimerError(Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary estado)
{
    // Los errores de lectura del JSON traen la ruta del campo ("$.operaciones[0].evaluacion.lat").
    string? general = null;
    foreach (var entrada in estado)
    {
        foreach (var error in entrada.Value.Errors)
        {
            if (entrada.Key.StartsWith('$'))
            {
                return $"El campo '{entrada.Key.TrimStart('$', '.')}' no tiene un valor válido";
            }
            general ??= error.ErrorMessage;
        }
    }
    return general ?? "Petición inválida";
}

// Para que WebApplicationFactory<Program> encuentre el punto de entrada en las pruebas.
public partial class Program;
