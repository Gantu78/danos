using Danos.Api.Infraestructura;

namespace Danos.Api.Dominio;

/// <summary>Un activo cercano y una de sus evaluaciones, candidato a ser el mismo activo.</summary>
public sealed record CandidatoDuplicado(
    Guid ActivoId,
    string TipoCodigo,
    double Lat,
    double Lon,
    Guid EvaluacionId,
    DateTime InstanteCampo);

/// <summary>
/// Un activo nuevo creado sin conexión puede ser uno que otro evaluador ya registró. Es posible duplicado
/// si hay otro activo del MISMO tipo a ≤ distanciaMetros (Haversine) con una evaluación dentro de
/// ±ventanaHoras del instante en campo. Solo detecta: nunca fusiona ni descarta.
/// </summary>
public static class DetectorDuplicados
{
    public static CandidatoDuplicado? Buscar(
        double lat, double lon, string tipoCodigo, DateTime instanteCampo,
        IEnumerable<CandidatoDuplicado> candidatos, double distanciaMetros, double ventanaHoras)
    {
        var ventana = TimeSpan.FromHours(ventanaHoras);
        CandidatoDuplicado? mejor = null;
        double mejorDistancia = double.MaxValue;

        foreach (var c in candidatos)
        {
            if (c.TipoCodigo != tipoCodigo)
            {
                continue;
            }
            if ((c.InstanteCampo - instanteCampo).Duration() > ventana)
            {
                continue;
            }
            double distancia = Haversine.DistanciaMetros(lat, lon, c.Lat, c.Lon);
            if (distancia <= distanciaMetros && distancia < mejorDistancia)
            {
                mejor = c;
                mejorDistancia = distancia;
            }
        }
        return mejor;
    }
}
