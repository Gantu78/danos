package com.example.danos.infraestructura.remoto

import kotlinx.serialization.decodeFromString
import com.example.danos.activoDePrueba
import com.example.danos.dominio.NivelDano
import com.example.danos.evaluacionDePrueba
import com.example.danos.evidenciaDePrueba
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DtosSerializacionTest {

    @Test
    fun `lee la descarga del contrato y la mapea a dominio`() {
        val json = """
            {
              "instanteServidor": "2026-03-01T10:15:30.1234567Z",
              "tiposActivo": [
                {"codigo":"VIV","nombre":"Vivienda","criterios":[
                  {"nivel":"LEVE","criterio":"Fisuras menores"},
                  {"nivel":"NIVEL_FUTURO","criterio":"Se ignora"}
                ]}
              ],
              "desastres": [{"id":"d1","nombre":"Sismo","tipo":"SISMO"}],
              "activos": [{"id":"a1","tipoCodigo":"VIV","lat":4.6,"lon":-74.1,"referencia":"CL 10","version":3}],
              "campoNuevoDelServidor": true
            }
        """.trimIndent()

        val dto = JsonDanos.decodeFromString<DescargaDto>(json)

        assertEquals(Instant.parse("2026-03-01T10:15:30.1234567Z"), instanteDesdeIso(dto.instanteServidor))
        val tipo = dto.tiposActivo.single()
        assertEquals("Vivienda", tipo.aDominio().nombre)
        assertEquals(NivelDano.LEVE, tipo.criteriosADominio().single().nivel)
        assertEquals("Sismo", dto.desastres.single().aDominio().nombre)
        assertEquals(3, dto.activos.single().aDominio().versionDescargada)
    }

    @Test
    fun `acepta fechas con desplazamiento`() {
        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), instanteDesdeIso("2026-03-01T10:00:00+00:00"))
    }

    @Test
    fun `lee la respuesta de sincronizacion`() {
        val json = """
            {"resultados":[
              {"idOperacion":"o1","resultado":"APLICADA","estadoEvaluacion":"REGISTRADA",
               "nuevaVersionActivo":4,"instanteServidor":"2026-03-01T10:00:00Z"},
              {"idOperacion":"o2","resultado":"INVALIDA","motivo":"Sin tipo","instanteServidor":"2026-03-01T10:00:00Z"}
            ]}
        """.trimIndent()

        val dto = JsonDanos.decodeFromString<RespuestaSincronizacionDto>(json)

        assertEquals(4, dto.resultados[0].nuevaVersionActivo)
        assertEquals("Sin tipo", dto.resultados[1].motivo)
    }

    @Test
    fun `la evaluacion serializada usa los nombres del contrato y omite los opcionales nulos`() {
        val json = evaluacionDePrueba(desastreId = null, evidencias = listOf(evidenciaDePrueba()))
            .aPayloadJson(activoDePrueba(version = 3))

        val objeto = JsonDanos.parseToJsonElement(json).jsonObject

        assertFalse("activoNuevo" in objeto)
        assertFalse("desastreId" in objeto)
        assertEquals("MODERADO", objeto.getValue("nivelDano").jsonPrimitive.content)
        assertEquals("2026-03-01T10:15:30.123Z", objeto.getValue("instanteCampo").jsonPrimitive.content)
        assertEquals(
            setOf("id", "lat", "lon", "instanteCaptura", "sha256", "tamanoBytes"),
            (JsonDanos.parseToJsonElement(json).jsonObject.getValue("evidencias") as kotlinx.serialization.json.JsonArray)
                .single().jsonObject.keys,
        )
    }

    @Test
    fun `activoNuevo solo viaja si el activo no tiene version`() {
        val conActivoNuevo = evaluacionDePrueba().aPayloadJson(activoDePrueba(version = null))
        val objeto = JsonDanos.parseToJsonElement(conActivoNuevo).jsonObject.getValue("activoNuevo").jsonObject
        assertEquals(setOf("id", "tipoCodigo", "lat", "lon", "referencia"), objeto.keys)
    }

    @Test
    fun `una evaluacion sin evidencias envia la lista vacia`() {
        val objeto: JsonObject = JsonDanos.parseToJsonElement(evaluacionDePrueba().aPayloadJson(null)).jsonObject
        assertTrue("evidencias" in objeto)
    }

    @Test
    fun `el lote envuelve la evaluacion sin alterarla y omite versionBaseActivo nula`() {
        val payload = evaluacionDePrueba().aPayloadJson(null)
        val lote = LoteSincronizacionDto(
            idDispositivo = "disp",
            instanteEnvioDispositivo = "2026-03-01T10:00:00Z",
            operaciones = listOf(
                OperacionDto("o1", "CREAR", null, JsonDanos.parseToJsonElement(payload)),
            ),
        )

        val objeto = JsonDanos.parseToJsonElement(JsonDanos.encodeToString(lote)).jsonObject
        val operacion = (objeto.getValue("operaciones") as kotlinx.serialization.json.JsonArray).single().jsonObject

        assertFalse("versionBaseActivo" in operacion)
        assertEquals(JsonDanos.parseToJsonElement(payload), operacion.getValue("evaluacion"))
    }
}
