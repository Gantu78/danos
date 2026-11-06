namespace Danos.Api.Infraestructura;

// Copiada del taller de mapas (Mapas.Logica/Dominio/Geo.cs).
public static class Haversine
{
    /// <summary>Radio medio de la Tierra en metros.</summary>
    public const double RadioTierra = 6_371_000;

    /// <summary>
    /// Distancia sobre la superficie de la Tierra entre dos coordenadas, con la fórmula de Haversine.
    /// Trata la Tierra como una esfera: el error es de ~0,5 %, de sobra para decidir "a menos de 25 m".
    /// </summary>
    public static double DistanciaMetros(double lat1, double lon1, double lat2, double lon2)
    {
        double phi1 = ARadianes(lat1);
        double phi2 = ARadianes(lat2);
        double deltaPhi = ARadianes(lat2 - lat1);
        double deltaLambda = ARadianes(lon2 - lon1);

        double senoMedioPhi = Math.Sin(deltaPhi / 2);
        double senoMedioLambda = Math.Sin(deltaLambda / 2);
        double a = senoMedioPhi * senoMedioPhi
                 + Math.Cos(phi1) * Math.Cos(phi2) * senoMedioLambda * senoMedioLambda;

        return 2 * RadioTierra * Math.Asin(Math.Sqrt(a));
    }

    /// <summary>Cuántos grados de latitud equivalen a esa distancia (para armar un recuadro en SQL).</summary>
    public static double GradosLatitud(double metros) => metros / RadioTierra * 180 / Math.PI;

    /// <summary>Cuántos grados de longitud equivalen a esa distancia a esa latitud.</summary>
    public static double GradosLongitud(double metros, double latitud)
    {
        double coseno = Math.Max(Math.Cos(ARadianes(latitud)), 0.01);
        return GradosLatitud(metros) / coseno;
    }

    private static double ARadianes(double grados) => grados * Math.PI / 180;
}
