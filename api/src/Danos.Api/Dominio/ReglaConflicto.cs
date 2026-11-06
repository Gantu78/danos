namespace Danos.Api.Dominio;

/// <summary>Una versión del activo y el dispositivo que la produjo.</summary>
public readonly record struct VersionRegistrada(int Numero, string IdDispositivo);

public sealed record VeredictoConflicto(
    bool HayConflicto,
    int Base,
    int VersionActual,
    List<string> DispositivosAjenos,
    string Detalle);

/// <summary>
/// Regla determinista de conflictos (RF-ED-13, AD-32). Función pura: no toca la base ni usa relojes.
/// Hay conflicto si el activo tiene alguna versión posterior a la que el evaluador descargó (la base)
/// y esa versión la produjo OTRO dispositivo. Las versiones del mismo dispositivo no cuentan: así una
/// cadena crear → editar hecha sin red no choca consigo misma.
/// </summary>
public static class ReglaConflicto
{
    public static VeredictoConflicto Evaluar(
        int? versionBase, string idDispositivo, IEnumerable<VersionRegistrada> versiones)
    {
        int b = versionBase ?? 0;
        int actual = 0;
        var ajenos = new List<string>();

        foreach (var v in versiones)
        {
            if (v.Numero > actual)
            {
                actual = v.Numero;
            }
            if (v.Numero > b && v.IdDispositivo != idDispositivo && !ajenos.Contains(v.IdDispositivo))
            {
                ajenos.Add(v.IdDispositivo);
            }
        }

        bool hayConflicto = ajenos.Count > 0;
        string detalle = hayConflicto
            ? $"Versión del activo: base {b}, versión actual {actual}; " +
              $"cambios posteriores a la base hechos por {string.Join(", ", ajenos)} (dispositivo del lote: {idDispositivo})"
            : $"Versión del activo: base {b}, versión actual {actual}; sin cambios de otros dispositivos después de la base";

        return new VeredictoConflicto(hayConflicto, b, actual, ajenos, detalle);
    }
}
