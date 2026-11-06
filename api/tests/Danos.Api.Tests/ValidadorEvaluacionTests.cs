using Danos.Api.Acceso;
using Danos.Api.Dominio;

namespace Danos.Api.Tests;

public class ValidadorEvaluacionTests
{
    private static readonly Guid Desastre = Guid.Parse("d0000000-0000-0000-0000-000000000001");
    private static readonly HashSet<string> Tipos = ["vivienda", "puente"];
    private static readonly HashSet<Guid> Desastres = [Desastre];

    private static OperacionDto Valida() => new()
    {
        IdOperacion = Guid.NewGuid(),
        Tipo = "CREAR",
        VersionBaseActivo = 1,
        Evaluacion = new EvaluacionEntranteDto
        {
            Id = Guid.NewGuid(),
            ActivoId = Guid.NewGuid(),
            DesastreId = Desastre,
            InstanteCampo = DateTimeOffset.UtcNow,
            Evaluador = "Ana",
            Organizacion = "Cruz Roja",
            Lat = 4.65,
            Lon = -74.1,
            PrecisionM = 5,
            TipoActivoCodigo = "vivienda",
            NivelDano = "GRAVE",
            Descripcion = "Muro caído",
            Evidencias =
            [
                new EvidenciaDeclaradaDto
                {
                    Id = Guid.NewGuid(), Lat = 4.65, Lon = -74.1, InstanteCaptura = DateTimeOffset.UtcNow,
                    Sha256 = new string('a', 64), TamanoBytes = 1000
                }
            ]
        }
    };

    private static string? Validar(OperacionDto op) => ValidadorEvaluacion.Validar(op, Tipos, Desastres);

    [Fact]
    public void Una_operacion_completa_es_valida()
    {
        Assert.Null(Validar(Valida()));
    }

    [Fact]
    public void Sin_desastre_ni_evidencias_sigue_siendo_valida()
    {
        var op = Valida();
        op.Evaluacion!.DesastreId = null;
        op.Evaluacion.Evidencias = null;
        Assert.Null(Validar(op));
    }

    public static TheoryData<string, Action<OperacionDto>> Faltantes() => new()
    {
        { "idOperacion", o => o.IdOperacion = null },
        { "tipo", o => o.Tipo = null },
        { "evaluacion", o => o.Evaluacion = null },
        { "id", o => o.Evaluacion!.Id = null },
        { "activoId", o => o.Evaluacion!.ActivoId = null },
        { "instanteCampo", o => o.Evaluacion!.InstanteCampo = null },
        { "evaluador", o => o.Evaluacion!.Evaluador = "  " },
        { "organizacion", o => o.Evaluacion!.Organizacion = null },
        { "lat", o => o.Evaluacion!.Lat = null },
        { "lon", o => o.Evaluacion!.Lon = null },
        { "precisionM", o => o.Evaluacion!.PrecisionM = null },
        { "tipoActivoCodigo", o => o.Evaluacion!.TipoActivoCodigo = null },
        { "nivelDano", o => o.Evaluacion!.NivelDano = null },
        { "descripcion", o => o.Evaluacion!.Descripcion = null },
    };

    [Theory]
    [MemberData(nameof(Faltantes))]
    public void Falta_un_atributo_obligatorio(string campo, Action<OperacionDto> quitar)
    {
        var op = Valida();
        quitar(op);
        var motivo = Validar(op);
        Assert.NotNull(motivo);
        Assert.Contains(campo, motivo);
    }

    [Fact]
    public void Tipo_de_operacion_desconocido()
    {
        var op = Valida();
        op.Tipo = "BORRAR";
        Assert.Contains("tipo", Validar(op));
    }

    [Fact]
    public void Tipo_de_activo_inexistente()
    {
        var op = Valida();
        op.Evaluacion!.TipoActivoCodigo = "nave_espacial";
        Assert.Contains("tipo de activo", Validar(op));
    }

    [Theory]
    [InlineData("CATASTROFICO")]
    [InlineData("grave")]
    [InlineData("3")]
    public void Nivel_de_dano_invalido(string nivel)
    {
        var op = Valida();
        op.Evaluacion!.NivelDano = nivel;
        Assert.Contains("nivel", Validar(op));
    }

    [Fact]
    public void Desastre_inexistente()
    {
        var op = Valida();
        op.Evaluacion!.DesastreId = Guid.NewGuid();
        Assert.Contains("desastre", Validar(op));
    }

    [Theory]
    [InlineData(91, 0)]
    [InlineData(-90.5, 0)]
    [InlineData(0, 180.1)]
    [InlineData(0, -181)]
    [InlineData(double.NaN, 0)]
    public void Coordenadas_fuera_de_rango(double lat, double lon)
    {
        var op = Valida();
        op.Evaluacion!.Lat = lat;
        op.Evaluacion.Lon = lon;
        Assert.Contains("coordenadas", Validar(op));
    }

    [Fact]
    public void Activo_nuevo_con_id_distinto_al_activoId()
    {
        var op = Valida();
        op.Evaluacion!.ActivoNuevo = new ActivoNuevoDto
        {
            Id = Guid.NewGuid(), TipoCodigo = "vivienda", Lat = 4.65, Lon = -74.1, Referencia = "Casa"
        };
        Assert.Contains("activoNuevo", Validar(op));
    }

    [Fact]
    public void Activo_nuevo_con_tipo_inexistente()
    {
        var op = Valida();
        op.Evaluacion!.ActivoNuevo = new ActivoNuevoDto
        {
            Id = op.Evaluacion.ActivoId, TipoCodigo = "castillo", Lat = 4.65, Lon = -74.1, Referencia = "Casa"
        };
        Assert.Contains("activoNuevo", Validar(op));
    }

    [Fact]
    public void Descripcion_vacia_es_invalida()
    {
        var op = Valida();
        op.Evaluacion!.Descripcion = "   ";
        Assert.Contains("descripcion", Validar(op));
    }

    public static TheoryData<string, Action<OperacionDto>> Largos() => new()
    {
        { "descripcion", o => o.Evaluacion!.Descripcion = new string('x', Limites.Descripcion + 1) },
        { "evaluador", o => o.Evaluacion!.Evaluador = new string('x', Limites.Nombre + 1) },
        { "organizacion", o => o.Evaluacion!.Organizacion = new string('x', Limites.Nombre + 1) },
        { "referencia", o => o.Evaluacion!.ActivoNuevo = new ActivoNuevoDto
            {
                Id = o.Evaluacion.ActivoId, TipoCodigo = "vivienda", Lat = 4.65, Lon = -74.1,
                Referencia = new string('x', Limites.Referencia + 1)
            } },
    };

    [Theory]
    [MemberData(nameof(Largos))]
    public void Texto_mas_largo_que_su_columna_es_invalido(string campo, Action<OperacionDto> alargar)
    {
        var op = Valida();
        alargar(op);
        Assert.Contains(campo, Validar(op));
    }

    [Fact]
    public void Textos_en_el_limite_son_validos()
    {
        var op = Valida();
        op.Evaluacion!.Descripcion = new string('x', Limites.Descripcion);
        op.Evaluacion.Evaluador = new string('x', Limites.Nombre);
        Assert.Null(Validar(op));
    }

    [Theory]
    [InlineData("0001-01-01T00:00:00Z")]
    [InlineData("1899-12-31T23:59:59Z")]
    [InlineData("9999-12-31T23:59:59Z")]
    public void Instante_de_campo_fuera_del_rango_razonable(string instante)
    {
        var op = Valida();
        op.Evaluacion!.InstanteCampo = DateTimeOffset.Parse(instante);
        Assert.Contains("instanteCampo", Validar(op));
    }

    [Fact]
    public void Reloj_del_celular_reiniciado_a_1970_sigue_siendo_valido()
    {
        // Un celular sin batería puede volver a 1970: se acepta y el desfase queda registrado.
        var op = Valida();
        op.Evaluacion!.InstanteCampo = DateTimeOffset.UnixEpoch;
        Assert.Null(Validar(op));
    }

    [Fact]
    public void Evidencia_con_instante_fuera_de_rango()
    {
        var op = Valida();
        op.Evaluacion!.Evidencias![0].InstanteCaptura = DateTimeOffset.MinValue;
        Assert.Contains("evidencia", Validar(op));
    }

    [Fact]
    public void Evidencia_con_hash_mal_formado()
    {
        var op = Valida();
        op.Evaluacion!.Evidencias![0].Sha256 = "xyz";
        Assert.Contains("evidencia", Validar(op));
    }
}
