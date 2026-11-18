package com.example.danos.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class RegistroDesastreTest {
    private val ahora = Instant.parse("2026-11-18T15:00:00Z")

    private fun validar(
        tipo: String? = "INUNDACION",
        nombre: String = "Creciente del río Quindío",
        descripcion: String = "Viviendas inundadas en la ribera",
        severidad: SeveridadDesastre? = SeveridadDesastre.NIVEL_2,
        responsable: String = "bomberos",
        lat: Double? = 4.53,
        lon: Double? = -75.68,
        radio: Int? = 500,
        municipios: List<String> = emptyList(),
        inicio: Instant = ahora.minus(Duration.ofHours(1)),
    ) = ValidadorRegistroDesastre.validar(tipo, nombre, descripcion, severidad, responsable, lat, lon, radio, municipios, inicio, ahora)

    @Test
    fun unRegistroCompletoNoTieneErrores() {
        assertTrue(validar().isEmpty())
    }

    @Test
    fun losAtributosObligatoriosDelCuadro7SeExigen() {
        val errores = validar(tipo = null, nombre = " ", descripcion = "", severidad = null, responsable = "", lat = null)
        assertEquals(
            setOf(
                CampoDesastre.TIPO, CampoDesastre.NOMBRE, CampoDesastre.DESCRIPCION,
                CampoDesastre.SEVERIDAD, CampoDesastre.RESPONSABLE, CampoDesastre.UBICACION,
            ),
            errores.keys,
        )
    }

    @Test
    fun laZonaNecesitaRadioOMunicipiosRfGd08() {
        assertTrue(CampoDesastre.ZONA in validar(radio = null))
        assertTrue(validar(radio = null, municipios = listOf("63001")).isEmpty())
    }

    @Test
    fun unMunicipioQueNoEsDivipolaSeRechaza() {
        assertTrue(CampoDesastre.MUNICIPIOS in validar(municipios = listOf("6300")))
        assertTrue(CampoDesastre.MUNICIPIOS in validar(municipios = listOf("ABCDE")))
    }

    @Test
    fun elInicioNoPuedeSerFuturo() {
        assertTrue(CampoDesastre.INICIO in validar(inicio = ahora.plus(Duration.ofMinutes(30))))
        assertFalse(CampoDesastre.INICIO in validar(inicio = ahora.plus(Duration.ofMinutes(2))))
    }

    @Test
    fun separarMunicipiosAceptaComasYEspacios() {
        assertEquals(listOf("63001", "63130"), ValidadorRegistroDesastre.separarMunicipios("63001, 63130;63001"))
    }

    @Test
    fun soloOrganismoOAutoridadPuedenRegistrar() {
        assertTrue(puedeRegistrarDesastres(setOf("organismo")))
        assertTrue(puedeRegistrarDesastres(setOf("evaluador", "autoridad")))
        assertFalse(puedeRegistrarDesastres(setOf("evaluador")))
    }

    @Test
    fun elPoligonoEsUnAnilloCerradoAlRadioPedido() {
        val lat = 4.5339
        val lon = -75.6811
        val anillo = poligonoAlrededor(lat, lon, 1_000)
        assertEquals(17, anillo.size)
        assertEquals(anillo.first(), anillo.last())
        anillo.dropLast(1).forEach { (x, y) ->
            val dxM = (x - lon) * 111_195.0 * cos(Math.toRadians(lat))
            val dyM = (y - lat) * 111_195.0
            assertTrue("vértice a ${sqrt(dxM * dxM + dyM * dyM)} m", abs(sqrt(dxM * dxM + dyM * dyM) - 1_000) < 5)
        }
    }
}
