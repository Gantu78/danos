using Danos.Api.Infraestructura;

namespace Danos.Api.Tests;

public class HaversineTests
{
    [Fact]
    public void Distancia_sobre_un_meridiano_es_R_por_delta_latitud()
    {
        // 0,1° de latitud sobre el mismo meridiano = 6 371 000 m · 0,1 · π/180 ≈ 11 119,49 m
        double d = Haversine.DistanciaMetros(4.6, -74.08, 4.7, -74.08);
        Assert.InRange(d, 11118.5, 11120.5);
    }

    [Fact]
    public void Distancia_entre_Plaza_de_Bolivar_y_Parque_de_la_93()
    {
        double d = Haversine.DistanciaMetros(4.5981, -74.0758, 4.6766, -74.0484);
        Assert.InRange(d, 9237, 9247);
    }

    [Fact]
    public void El_mismo_punto_da_cero()
    {
        Assert.Equal(0, Haversine.DistanciaMetros(4.74, -74.09, 4.74, -74.09), 6);
    }

    [Fact]
    public void Veinte_metros_al_norte_quedan_dentro_de_25_m()
    {
        double grados = 20 / 111_195.0;
        double d = Haversine.DistanciaMetros(4.65, -74.10, 4.65 + grados, -74.10);
        Assert.InRange(d, 19.9, 20.1);
    }
}
