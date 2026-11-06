using Danos.Api.Dominio;

namespace Danos.Api.Tests;

// Banco de casos de la regla determinista. Cada caso se ARMA de forma que su respuesta se conoce por
// construcción (no se calcula con la misma fórmula que la regla): así una regla mal escrita no puede
// "acertar" copiándose a sí misma. Objetivo de QA-14 b: 0 falsos negativos.
public class ReglaConflictoTests
{
    private const string Propio = "dispositivo-A";
    private static readonly string[] Ajenos = ["dispositivo-B", "semilla", "dispositivo-C"];

    public static IEnumerable<object?[]> Casos()
    {
        var casos = new List<object?[]>();
        int?[] bases = [null, 0, 1, 2, 3, 4, 5];

        foreach (var versionBase in bases)
        {
            int b = versionBase ?? 0;
            for (int total = 1; total <= 7; total++)
            {
                // 1) Todas las versiones son del propio dispositivo → nunca hay conflicto.
                casos.Add(Caso("solo propias", versionBase, Versiones(total, _ => Propio), false));

                // 2) Versiones ajenas solo hasta la base (inclusive) → el evaluador ya las había visto.
                if (b >= 1 && total >= 1)
                {
                    casos.Add(Caso("ajenas antes de la base", versionBase,
                        Versiones(total, n => n <= Math.Min(b, total) ? Ajeno(n) : Propio), false));
                }

                // 3) Una sola versión ajena justo en la base y el resto propias → sin conflicto.
                if (b >= 1 && b <= total)
                {
                    casos.Add(Caso("ajena igual a la base", versionBase,
                        Versiones(total, n => n == b ? Ajeno(n) : Propio), false));
                }

                // 4) Una versión ajena después de la base → conflicto, esté donde esté.
                for (int pos = b + 1; pos <= total; pos++)
                {
                    int posicion = pos;
                    casos.Add(Caso($"ajena en {posicion} después de la base", versionBase,
                        Versiones(total, n => n == posicion ? Ajeno(n) : Propio), true));
                }

                // 5) Mezcla: ajenas antes de la base y la última versión ajena después de la base.
                if (total > b)
                {
                    casos.Add(Caso("mezcla con ajena al final", versionBase,
                        Versiones(total, n => n <= b || n == total ? Ajeno(n) : Propio), true));
                }

                // 6) Todas ajenas: conflicto si hay alguna por encima de la base.
                casos.Add(Caso("todas ajenas", versionBase, Versiones(total, Ajeno), total > b));
            }
        }
        return casos;
    }

    [Fact]
    public void El_banco_tiene_al_menos_100_casos()
    {
        Assert.True(Casos().Count() >= 100, $"Solo hay {Casos().Count()} casos");
    }

    [Fact]
    public void El_banco_tiene_casos_con_y_sin_conflicto()
    {
        var casos = Casos().ToList();
        Assert.Contains(casos, c => (bool)c[3]! == true);
        Assert.Contains(casos, c => (bool)c[3]! == false);
    }

    [Theory]
    [MemberData(nameof(Casos))]
    public void Detecta_conflicto_sin_falsos_negativos(
        string descripcion, int? versionBase, VersionRegistrada[] versiones, bool esperado)
    {
        var veredicto = ReglaConflicto.Evaluar(versionBase, Propio, versiones);
        Assert.True(esperado == veredicto.HayConflicto,
            $"{descripcion}: base={versionBase?.ToString() ?? "null"}, versiones=[{string.Join(", ", versiones)}]");
    }

    [Fact]
    public void Base_nula_cuenta_como_cero()
    {
        var versiones = new[] { new VersionRegistrada(1, "semilla") };
        Assert.True(ReglaConflicto.Evaluar(null, Propio, versiones).HayConflicto);
        Assert.False(ReglaConflicto.Evaluar(1, Propio, versiones).HayConflicto);
    }

    [Fact]
    public void Cadena_del_mismo_dispositivo_no_choca_consigo_misma()
    {
        // Semilla v1, el dispositivo A crea (v2) y luego edita con la misma base 1.
        var versiones = new[] { new VersionRegistrada(1, "semilla"), new VersionRegistrada(2, Propio) };
        Assert.False(ReglaConflicto.Evaluar(1, Propio, versiones).HayConflicto);
    }

    [Fact]
    public void El_veredicto_describe_base_version_actual_y_dispositivos()
    {
        var versiones = new[]
        {
            new VersionRegistrada(1, "semilla"),
            new VersionRegistrada(2, "dispositivo-B"),
            new VersionRegistrada(3, Propio)
        };
        var veredicto = ReglaConflicto.Evaluar(1, Propio, versiones);

        Assert.True(veredicto.HayConflicto);
        Assert.Equal(1, veredicto.Base);
        Assert.Equal(3, veredicto.VersionActual);
        Assert.Equal(["dispositivo-B"], veredicto.DispositivosAjenos);
        Assert.Contains("base 1", veredicto.Detalle);
        Assert.Contains("versión actual 3", veredicto.Detalle);
        Assert.Contains("dispositivo-B", veredicto.Detalle);
    }

    [Fact]
    public void Sin_versiones_no_hay_conflicto()
    {
        var veredicto = ReglaConflicto.Evaluar(0, Propio, []);
        Assert.False(veredicto.HayConflicto);
        Assert.Equal(0, veredicto.VersionActual);
    }

    private static object?[] Caso(string descripcion, int? versionBase, VersionRegistrada[] versiones, bool esperado) =>
        [descripcion, versionBase, versiones, esperado];

    private static VersionRegistrada[] Versiones(int total, Func<int, string> dueno)
    {
        var lista = new VersionRegistrada[total];
        for (int n = 1; n <= total; n++)
        {
            lista[n - 1] = new VersionRegistrada(n, dueno(n));
        }
        return lista;
    }

    private static string Ajeno(int numero) => Ajenos[numero % Ajenos.Length];
}
