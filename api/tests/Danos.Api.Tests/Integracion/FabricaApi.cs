using System.Net.Http.Json;
using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Npgsql;

namespace Danos.Api.Tests.Integracion;

[CollectionDefinition(Nombre)]
public class ColeccionApi : ICollectionFixture<FabricaApi>
{
    public const string Nombre = "API sobre evaluacion_danos_test";
}

/// <summary>
/// Levanta el API completo en memoria contra la base evaluacion_danos_test. La base se borra al empezar
/// cada corrida; al arrancar, el propio API (en Development) la migra y la siembra.
/// La cadena sale de la configuración del API (appsettings.Development.json, user-secrets o
/// ConnectionStrings__Danos): no se repite aquí.
/// </summary>
public class FabricaApi : WebApplicationFactory<Program>, IAsyncLifetime
{
    public const string BaseDePruebas = "evaluacion_danos_test";

    private static int siguienteActivo;
    private static int siguienteZona;

    public string CadenaPruebas { get; }
    public string RutaEvidencias { get; } = Path.Combine(Path.GetTempPath(), "danos-evidencias-pruebas", Guid.NewGuid().ToString("N"));

    public FabricaApi()
    {
        var configuracionApi = new ConfigurationBuilder()
            .SetBasePath(RutaProyectoApi())
            .AddJsonFile("appsettings.json")
            .AddJsonFile("appsettings.Development.json", optional: true)
            .AddUserSecrets(typeof(Program).Assembly, optional: true)   // misma prioridad que el API en Development
            .AddEnvironmentVariables()
            .Build();
        var cadena = configuracionApi.GetConnectionString("Danos")
            ?? throw new InvalidOperationException("Falta ConnectionStrings:Danos en el API");
        CadenaPruebas = new NpgsqlConnectionStringBuilder(cadena) { Database = BaseDePruebas, Pooling = true }.ConnectionString;
    }

    public async Task InitializeAsync()
    {
        // Se recrea en cada corrida: borrar aquí y dejar que el API migre y siembre al arrancar.
        var admin = new NpgsqlConnectionStringBuilder(CadenaPruebas) { Database = "postgres", Pooling = false };
        await using var conexion = new NpgsqlConnection(admin.ConnectionString);
        await conexion.OpenAsync();
        await using var comando = new NpgsqlCommand($"DROP DATABASE IF EXISTS {BaseDePruebas} WITH (FORCE)", conexion);
        await comando.ExecuteNonQueryAsync();
        _ = Server;   // arranca el host: migra y siembra catálogo y desastres
        await using var db = NuevoContexto();
        await ActivosDePrueba.SembrarAsync(db);   // activos solo para las pruebas
    }

    Task IAsyncLifetime.DisposeAsync()
    {
        try
        {
            Directory.Delete(RutaEvidencias, recursive: true);
        }
        catch (DirectoryNotFoundException)
        {
        }
        return Task.CompletedTask;
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Development");
        builder.ConfigureAppConfiguration((_, configuracion) =>
        {
            configuracion.AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["ConnectionStrings:Danos"] = CadenaPruebas,
                ["Evidencias:Ruta"] = RutaEvidencias,
            });
        });
    }

    public HttpClient Cliente(string dispositivo, string usuario = "Evaluador de prueba")
    {
        var cliente = CreateClient();
        cliente.DefaultRequestHeaders.Add("X-Usuario", usuario);
        cliente.DefaultRequestHeaders.Add("X-Organizacion", "Organización de prueba");
        cliente.DefaultRequestHeaders.Add("X-Dispositivo", dispositivo);
        return cliente;
    }

    public DanosDbContext NuevoContexto()
    {
        var scope = Services.CreateScope();
        return scope.ServiceProvider.GetRequiredService<DanosDbContext>();
    }

    /// <summary>Cada prueba toma activos de prueba distintos para no pisarse con las demás.</summary>
    public static Guid TomarActivoSembrado()
    {
        int n = Interlocked.Increment(ref siguienteActivo);
        if (n > ActivosDePrueba.Total)
        {
            throw new InvalidOperationException("Las pruebas usaron más activos que los sembrados");
        }
        return ActivosDePrueba.Id(n);
    }

    /// <summary>Una zona vacía (lejos de Bogotá y de las demás pruebas) para crear activos nuevos.</summary>
    public static (double Lat, double Lon) TomarZonaLibre()
    {
        int n = Interlocked.Increment(ref siguienteZona);
        return (10 + n * 0.05, -70 - n * 0.05);
    }

    public static async Task<RespuestaSincronizacionDto> Sincronizar(HttpClient cliente, LoteSincronizacionDto lote)
    {
        var respuesta = await cliente.PostAsJsonAsync("/v1/sincronizacion/evaluaciones", lote, Json.Opciones);
        var texto = await respuesta.Content.ReadAsStringAsync();
        Assert.True(respuesta.IsSuccessStatusCode, $"HTTP {(int)respuesta.StatusCode}: {texto}");
        return Json.Deserializar<RespuestaSincronizacionDto>(texto);
    }

    private static string RutaProyectoApi()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "Danos.sln")))
        {
            dir = dir.Parent;
        }
        if (dir == null)
        {
            throw new InvalidOperationException("No se encontró Danos.sln subiendo desde " + AppContext.BaseDirectory);
        }
        return Path.Combine(dir.FullName, "src", "Danos.Api");
    }
}
