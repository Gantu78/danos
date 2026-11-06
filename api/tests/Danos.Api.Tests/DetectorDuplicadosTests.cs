using Danos.Api.Dominio;

namespace Danos.Api.Tests;

public class DetectorDuplicadosTests
{
    private static readonly DateTime Ahora = new(2026, 11, 5, 15, 0, 0, DateTimeKind.Utc);
    private const double Metro = 1 / 111_195.0;   // grados de latitud por metro

    private static CandidatoDuplicado Candidato(double metrosAlNorte, string tipo, double horas) =>
        new(Guid.NewGuid(), tipo, 4.65 + metrosAlNorte * Metro, -74.10, Guid.NewGuid(), Ahora.AddHours(horas));

    private static CandidatoDuplicado? Buscar(params CandidatoDuplicado[] candidatos) =>
        DetectorDuplicados.Buscar(4.65, -74.10, "vivienda", Ahora, candidatos, 25, 24);

    [Fact]
    public void Mismo_tipo_cerca_y_dentro_de_la_ventana_es_duplicado()
    {
        var c = Candidato(20, "vivienda", -3);
        Assert.Equal(c, Buscar(c));
    }

    [Fact]
    public void A_mas_de_25_m_no_es_duplicado()
    {
        Assert.Null(Buscar(Candidato(30, "vivienda", 0)));
    }

    [Fact]
    public void Otro_tipo_no_es_duplicado()
    {
        Assert.Null(Buscar(Candidato(5, "puente", 0)));
    }

    [Theory]
    [InlineData(-25)]
    [InlineData(24.5)]
    public void Fuera_de_la_ventana_no_es_duplicado(double horas)
    {
        Assert.Null(Buscar(Candidato(5, "vivienda", horas)));
    }

    [Theory]
    [InlineData(-24)]
    [InlineData(24)]
    public void El_borde_de_la_ventana_cuenta(double horas)
    {
        Assert.NotNull(Buscar(Candidato(5, "vivienda", horas)));
    }

    [Fact]
    public void Devuelve_el_mas_cercano()
    {
        var lejos = Candidato(22, "vivienda", 1);
        var cerca = Candidato(3, "vivienda", 1);
        Assert.Equal(cerca, Buscar(lejos, cerca));
    }
}
