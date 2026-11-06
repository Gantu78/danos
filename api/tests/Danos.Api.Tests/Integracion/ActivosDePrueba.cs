using Danos.Api.Dominio;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Tests.Integracion;

/// <summary>
/// Activos que solo existen en la base de pruebas (evaluacion_danos_test). El API ya no siembra activos:
/// en producción los crea la app en campo. Aquí se insertan 300 en una cuadrícula sobre Bogotá
/// (~1 km × ~670 m, ningún par a menos de 25 m), en versión 1, como si los hubiera descargado un celular.
/// </summary>
public static class ActivosDePrueba
{
    public const int Total = 300;
    public const string Dispositivo = "semilla-pruebas";

    /// <summary>GUID fijo del activo de prueba número n (1..300).</summary>
    public static Guid Id(int n) => Guid.Parse($"a0000000-0000-0000-0000-{n:D12}");

    public static async Task SembrarAsync(DanosDbContext db)
    {
        var tipos = await (from t in db.TiposActivo orderby t.Codigo select t.Codigo).ToListAsync();
        var instante = new DateTime(2026, 11, 1, 0, 0, 0, DateTimeKind.Utc);

        for (int n = 1; n <= Total; n++)
        {
            int fila = (n - 1) / 20;
            int columna = (n - 1) % 20;
            var id = Id(n);
            db.Activos.Add(new Activo
            {
                Id = id,
                TipoActivoCodigo = tipos[(n - 1) % tipos.Count],
                Lat = Math.Round(4.560 + fila * 0.009, 6),
                Lon = Math.Round(-74.170 + columna * 0.006, 6),
                Referencia = $"Activo de prueba {n:000}",
                Version = 1,
                CreadoPorDispositivo = Dispositivo
            });
            db.ActivoVersiones.Add(new ActivoVersion
            {
                ActivoId = id, Numero = 1, IdDispositivo = Dispositivo, Instante = instante
            });
        }
        await db.SaveChangesAsync();
    }
}
