using System.Net;
using System.Net.Http.Json;
using Danos.Api.Acceso;
using Danos.Api.Dominio;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;
using static Danos.Api.Tests.Integracion.Lotes;

namespace Danos.Api.Tests.Integracion;

// Casos que salieron de la revisión de código: datos que antes bloqueaban el lote con 503,
// estados de conflicto que se perdían y carreras que no se cubrían.
[Collection(ColeccionApi.Nombre)]
public class RevisionHallazgosTests(FabricaApi api)
{
    [Fact]
    public async Task Descripcion_mas_larga_que_la_columna_es_invalida_y_no_bloquea_el_lote()
    {
        var larga = Crear(FabricaApi.TomarActivoSembrado(), 1);
        larga.Evaluacion!.Descripcion = new string('x', Limites.Descripcion + 1);
        var nombreLargo = Crear(FabricaApi.TomarActivoSembrado(), 1);
        nombreLargo.Evaluacion!.Evaluador = new string('a', Limites.Nombre + 1);
        var buena = Crear(FabricaApi.TomarActivoSembrado(), 1);

        var respuesta = await FabricaApi.Sincronizar(api.Cliente("largo"), Lote("largo", larga, nombreLargo, buena));

        Assert.Equal(ResultadoOperacion.INVALIDA, respuesta.Resultados[0].Resultado);
        Assert.Contains("descripcion", respuesta.Resultados[0].Motivo);
        Assert.Equal(ResultadoOperacion.INVALIDA, respuesta.Resultados[1].Resultado);
        Assert.Contains("evaluador", respuesta.Resultados[1].Motivo);
        Assert.Equal(ResultadoOperacion.APLICADA, respuesta.Resultados[2].Resultado);
    }

    [Fact]
    public async Task Instante_de_campo_por_defecto_es_invalida_y_no_bloquea_el_lote()
    {
        var (lat, lon) = FabricaApi.TomarZonaLibre();
        var op = CrearConActivoNuevo(lat, lon, instante: DateTimeOffset.MinValue);

        var r = await Uno("fecha-minima", op);

        Assert.Equal(ResultadoOperacion.INVALIDA, r.Resultado);
        Assert.Contains("instanteCampo", r.Motivo);
    }

    [Fact]
    public async Task IdDispositivo_mas_largo_que_la_columna_responde_400()
    {
        var dispositivo = new string('d', Limites.IdDispositivo + 1);
        var respuesta = await api.Cliente(dispositivo).PostAsJsonAsync("/v1/sincronizacion/evaluaciones",
            Lote(dispositivo, Crear(FabricaApi.TomarActivoSembrado(), 1)), Json.Opciones);
        Assert.Equal(HttpStatusCode.BadRequest, respuesta.StatusCode);
    }

    [Fact]
    public async Task Encabezado_y_cuerpo_con_dispositivos_distintos_responde_400()
    {
        var respuesta = await api.Cliente("celular-1").PostAsJsonAsync("/v1/sincronizacion/evaluaciones",
            Lote("celular-2", Crear(FabricaApi.TomarActivoSembrado(), 1)), Json.Opciones);
        Assert.Equal(HttpStatusCode.BadRequest, respuesta.StatusCode);
        Assert.Contains("X-Dispositivo", await respuesta.Content.ReadAsStringAsync());
    }

    [Theory]
    [InlineData(999)]
    [InlineData(-1)]
    public async Task Base_que_el_servidor_nunca_emitio_es_invalida(int versionBase)
    {
        var activoId = FabricaApi.TomarActivoSembrado();
        var r = await Uno("base-rara", Crear(activoId, versionBase));

        Assert.Equal(ResultadoOperacion.INVALIDA, r.Resultado);
        Assert.Contains("versionBaseActivo", r.Motivo);
        await using var db = api.NuevoContexto();
        Assert.Equal(1, (await db.Activos.SingleAsync(a => a.Id == activoId)).Version);
    }

    [Fact]
    public async Task Editar_un_posible_duplicado_no_le_quita_el_estado_en_conflicto()
    {
        var (lat, lon) = FabricaApi.TomarZonaLibre();
        Assert.Equal(ResultadoOperacion.APLICADA, (await Uno("dup-edit-A", CrearConActivoNuevo(lat, lon))).Resultado);

        var duplicado = CrearConActivoNuevo(lat + 8 / 111_195.0, lon);
        var edicion = Actualizar(duplicado.Evaluacion!, null, "GRAVE");
        var respuesta = await FabricaApi.Sincronizar(api.Cliente("dup-edit-B"), Lote("dup-edit-B", duplicado, edicion));

        Assert.Equal(ResultadoOperacion.POSIBLE_DUPLICADO, respuesta.Resultados[0].Resultado);
        Assert.Equal(ResultadoOperacion.APLICADA, respuesta.Resultados[1].Resultado);
        Assert.Equal(EstadoEvaluacion.EN_CONFLICTO, respuesta.Resultados[1].EstadoEvaluacion);
        await using var db = api.NuevoContexto();
        var evaluacion = await db.Evaluaciones.SingleAsync(e => e.Id == duplicado.Evaluacion!.Id);
        Assert.Equal(EstadoEvaluacion.EN_CONFLICTO, evaluacion.Estado);
        Assert.Equal(NivelDano.GRAVE, evaluacion.NivelDano);
    }

    [Fact]
    public async Task El_mismo_lote_enviado_dos_veces_a_la_vez_da_el_mismo_resultado_y_una_sola_evaluacion()
    {
        for (int intento = 0; intento < 5; intento++)
        {
            var op = Crear(FabricaApi.TomarActivoSembrado(), 1);
            var lote = Lote("doble", op);

            var respuestas = await Task.WhenAll(
                FabricaApi.Sincronizar(api.Cliente("doble"), lote),
                FabricaApi.Sincronizar(api.Cliente("doble"), lote));

            Assert.Equal(Json.Serializar(respuestas[0]), Json.Serializar(respuestas[1]));
            Assert.Equal(ResultadoOperacion.APLICADA, respuestas[0].Resultados[0].Resultado);
            await using var db = api.NuevoContexto();
            Assert.Equal(1, await db.Evaluaciones.CountAsync(e => e.Id == op.Evaluacion!.Id));
            Assert.Equal(1, await db.EvaluacionVersiones.CountAsync(v => v.EvaluacionId == op.Evaluacion!.Id));
        }
    }

    [Fact]
    public async Task Dos_activos_nuevos_cercanos_creados_a_la_vez_quedan_como_posible_duplicado()
    {
        for (int intento = 0; intento < 5; intento++)
        {
            var (lat, lon) = FabricaApi.TomarZonaLibre();
            var deA = CrearConActivoNuevo(lat, lon);
            var deB = CrearConActivoNuevo(lat + 10 / 111_195.0, lon);

            var respuestas = await Task.WhenAll(
                FabricaApi.Sincronizar(api.Cliente("nuevo-conc-A"), Lote("nuevo-conc-A", deA)),
                FabricaApi.Sincronizar(api.Cliente("nuevo-conc-B"), Lote("nuevo-conc-B", deB)));
            var resultados = respuestas.Select(x => x.Resultados[0].Resultado).OrderBy(x => x).ToList();

            Assert.Equal([ResultadoOperacion.APLICADA, ResultadoOperacion.POSIBLE_DUPLICADO], resultados);
        }
    }

    private async Task<ResultadoOperacionDto> Uno(string dispositivo, OperacionDto op)
    {
        var respuesta = await FabricaApi.Sincronizar(api.Cliente(dispositivo), Lote(dispositivo, op));
        return Assert.Single(respuesta.Resultados);
    }
}
