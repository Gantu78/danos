package com.example.danos.dominio

import com.example.danos.evaluacionDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorEvaluacionTest {

    private fun campos(evaluacion: Evaluacion) = ValidadorEvaluacion.validar(evaluacion).map { it.campo }

    @Test
    fun `una evaluacion completa no tiene errores`() {
        assertEquals(emptyList<CampoEvaluacion>(), campos(evaluacionDePrueba()))
    }

    @Test
    fun `desastre y evidencias son opcionales`() {
        assertTrue(campos(evaluacionDePrueba(desastreId = null, evidencias = emptyList())).isEmpty())
    }

    @Test
    fun `reporta cada obligatorio vacio en su campo`() {
        val errores = campos(
            evaluacionDePrueba().copy(
                activoId = "",
                evaluador = " ",
                organizacion = "",
                tipoActivoCodigo = "",
                nivelDano = null,
                descripcion = "   ",
            ),
        )
        assertEquals(
            setOf(
                CampoEvaluacion.ACTIVO,
                CampoEvaluacion.EVALUADOR,
                CampoEvaluacion.ORGANIZACION,
                CampoEvaluacion.TIPO_ACTIVO,
                CampoEvaluacion.NIVEL_DANO,
                CampoEvaluacion.DESCRIPCION,
            ),
            errores.toSet(),
        )
    }

    @Test
    fun `los limites de coordenadas son validos y fuera de ellos no`() {
        assertTrue(campos(evaluacionDePrueba().copy(lat = 90.0, lon = -180.0)).isEmpty())
        assertTrue(CampoEvaluacion.UBICACION in campos(evaluacionDePrueba().copy(lat = 90.01)))
        assertTrue(CampoEvaluacion.UBICACION in campos(evaluacionDePrueba().copy(lon = 180.01)))
        assertTrue(CampoEvaluacion.UBICACION in campos(evaluacionDePrueba().copy(lat = Double.NaN)))
    }

    @Test
    fun `respeta las longitudes maximas del servidor`() {
        assertTrue(campos(evaluacionDePrueba(descripcion = "x".repeat(Limites.DESCRIPCION))).isEmpty())
        assertTrue(CampoEvaluacion.DESCRIPCION in campos(evaluacionDePrueba(descripcion = "x".repeat(Limites.DESCRIPCION + 1))))
        assertTrue(CampoEvaluacion.EVALUADOR in campos(evaluacionDePrueba().copy(evaluador = "a".repeat(Limites.NOMBRE + 1))))
    }

    @Test
    fun `los niveles siguen la escala 1 a 5 del enunciado`() {
        assertEquals(listOf(1, 2, 3, 4, 5), NivelDano.entries.map { it.numero })
        assertEquals("5 · Destrucción total", NivelDano.DESTRUCCION_TOTAL.etiquetaConEscala)
    }

    @Test
    fun `sin desastre el estado inicial es preliminar`() {
        assertEquals(EstadoEvaluacion.PRELIMINAR, estadoInicial(null))
        assertEquals(EstadoEvaluacion.REGISTRADA, estadoInicial("d1"))
    }

    @Test
    fun `la url del api debe ser http o https con host`() {
        assertTrue(ValidadorIngreso.urlValida("http://10.0.2.2:5080"))
        assertTrue(ValidadorIngreso.urlValida(" https://api.ejemplo.org "))
        assertFalse(ValidadorIngreso.urlValida("10.0.2.2:5080"))
        assertFalse(ValidadorIngreso.urlValida("ftp://host"))
        assertFalse(ValidadorIngreso.urlValida("http://"))
        assertFalse(ValidadorIngreso.urlValida("no es url"))
    }
}
