using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Danos.Api.Acceso;
using Danos.Api.Dominio;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;
using static Danos.Api.Tests.Integracion.Lotes;

namespace Danos.Api.Tests.Integracion;

[Collection(ColeccionApi.Nombre)]
public class EvidenciasYMapaTests(FabricaApi api)
{
    [Fact]
    public async Task Foto_con_el_hash_declarado_se_guarda_y_el_reenvio_no_reescribe()
    {
        var foto = FotoJpeg();
        var op = Crear(FabricaApi.TomarActivoSembrado(), 1, foto: foto);
        var cliente = api.Cliente("foto-A");
        await FabricaApi.Sincronizar(cliente, Lote("foto-A", op));
        var evidenciaId = op.Evaluacion!.Evidencias![0].Id!.Value;

        var primera = await Subir(cliente, evidenciaId, foto);
        await using var db = api.NuevoContexto();
        var guardada = await db.Evidencias.SingleAsync(e => e.Id == evidenciaId);
        var ruta = guardada.RutaArchivo!;
        var escritura = File.GetLastWriteTimeUtc(ruta);
        var segunda = await Subir(cliente, evidenciaId, foto);

        Assert.Equal(HttpStatusCode.OK, primera.StatusCode);
        Assert.Equal(HttpStatusCode.OK, segunda.StatusCode);
        Assert.True(guardada.Subida);
        Assert.NotNull(guardada.InstanteSubida);
        Assert.Equal(Path.Combine(api.RutaEvidencias, op.Evaluacion.Id.ToString()!, evidenciaId + ".jpg"), ruta);
        Assert.Equal(foto, await File.ReadAllBytesAsync(ruta));
        Assert.Equal(escritura, File.GetLastWriteTimeUtc(ruta));
    }

    [Fact]
    public async Task Foto_con_hash_distinto_responde_422_y_no_guarda()
    {
        var foto = FotoJpeg();
        var op = Crear(FabricaApi.TomarActivoSembrado(), 1, foto: foto);
        var cliente = api.Cliente("foto-B");
        await FabricaApi.Sincronizar(cliente, Lote("foto-B", op));
        var evidenciaId = op.Evaluacion!.Evidencias![0].Id!.Value;

        var otra = FotoJpeg();
        var respuesta = await Subir(cliente, evidenciaId, otra);

        Assert.Equal(HttpStatusCode.UnprocessableEntity, respuesta.StatusCode);
        await using var db = api.NuevoContexto();
        Assert.False((await db.Evidencias.SingleAsync(e => e.Id == evidenciaId)).Subida);
        Assert.False(File.Exists(Path.Combine(api.RutaEvidencias, op.Evaluacion.Id.ToString()!, evidenciaId + ".jpg")));
    }

    [Fact]
    public async Task Foto_no_declarada_responde_404()
    {
        var respuesta = await Subir(api.Cliente("foto-C"), Guid.NewGuid(), FotoJpeg());
        Assert.Equal(HttpStatusCode.NotFound, respuesta.StatusCode);
    }

    [Fact]
    public async Task Foto_mas_grande_que_el_maximo_responde_413()
    {
        var grande = FotoJpeg(11 * 1024 * 1024);
        var op = Crear(FabricaApi.TomarActivoSembrado(), 1, foto: grande);
        var cliente = api.Cliente("foto-D");
        await FabricaApi.Sincronizar(cliente, Lote("foto-D", op));

        var respuesta = await Subir(cliente, op.Evaluacion!.Evidencias![0].Id!.Value, grande);

        Assert.Equal(HttpStatusCode.RequestEntityTooLarge, respuesta.StatusCode);
    }

    [Fact]
    public async Task Mapa_de_calor_pinta_la_evaluacion_vigente_de_cada_activo()
    {
        var activoId = FabricaApi.TomarActivoSembrado();
        var desastre = Semilla.DesastreDeslizamiento;
        var vigente = Crear(activoId, 1, nivel: "GRAVE", desastreId: desastre);
        var enConflicto = Crear(activoId, 1, nivel: "SIN_DANO", desastreId: desastre);
        await FabricaApi.Sincronizar(api.Cliente("calor-A"), Lote("calor-A", vigente));
        await FabricaApi.Sincronizar(api.Cliente("calor-B"), Lote("calor-B", enConflicto));

        var mapa = await api.Cliente("consola").GetFromJsonAsync<MapaCalorDto>(
            $"/v1/mapa-calor?desastreId={desastre}", Json.Opciones);

        var punto = Assert.Single(mapa!.Puntos, p => p.ActivoId == activoId);
        Assert.Equal(NivelDano.GRAVE, punto.Nivel);
        Assert.Equal(0.75, punto.Peso);
        Assert.True(punto.EnConflicto);
        Assert.NotNull(mapa.InstanteDato);
        Assert.True(mapa.ConflictosPendientes >= 1);

        var soloDestruccion = await api.Cliente("consola").GetFromJsonAsync<MapaCalorDto>(
            $"/v1/mapa-calor?desastreId={desastre}&nivelMinimo=DESTRUCCION_TOTAL", Json.Opciones);
        Assert.DoesNotContain(soloDestruccion!.Puntos, p => p.ActivoId == activoId);
    }

    [Fact]
    public async Task Mapa_de_calor_sin_desastre_responde_400()
    {
        var respuesta = await api.Cliente("consola").GetAsync("/v1/mapa-calor");
        Assert.Equal(HttpStatusCode.BadRequest, respuesta.StatusCode);
    }

    [Fact]
    public async Task La_pagina_del_mapa_carga()
    {
        var respuesta = await api.CreateClient().GetAsync("/mapa");
        var html = await respuesta.Content.ReadAsStringAsync();
        Assert.Equal(HttpStatusCode.OK, respuesta.StatusCode);
        Assert.Contains("leaflet-heat", html);
        Assert.Contains("Inundación río Bogotá", html);
    }

    private static async Task<HttpResponseMessage> Subir(HttpClient cliente, Guid evidenciaId, byte[] foto)
    {
        using var contenido = new MultipartFormDataContent();
        var archivo = new ByteArrayContent(foto);
        archivo.Headers.ContentType = new MediaTypeHeaderValue("image/jpeg");
        contenido.Add(archivo, "archivo", "foto.jpg");
        return await cliente.PutAsync($"/v1/evidencias/{evidenciaId}", contenido);
    }
}
