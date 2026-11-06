package com.example.danos.acceso

import kotlinx.serialization.decodeFromString
import com.example.danos.ActivoDaoFalso
import com.example.danos.EvaluacionDaoFalso
import com.example.danos.EvidenciaDaoFalso
import com.example.danos.OperacionDaoFalso
import com.example.danos.PreferenciasFalsas
import com.example.danos.ProgramadorFalso
import com.example.danos.TransaccionDirecta
import com.example.danos.activoDePrueba
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoError
import com.example.danos.dominio.TipoOperacion
import com.example.danos.evaluacionDePrueba
import com.example.danos.evidenciaDePrueba
import com.example.danos.infraestructura.local.aEntidad
import com.example.danos.infraestructura.remoto.EvaluacionDto
import com.example.danos.infraestructura.remoto.JsonDanos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasColaTest {
    private val operaciones = OperacionDaoFalso()
    private val evaluaciones = EvaluacionDaoFalso()
    private val evidencias = EvidenciaDaoFalso(evaluaciones)
    private val activos = ActivoDaoFalso()
    private val preferencias = PreferenciasFalsas()
    private val programador = ProgramadorFalso()
    private val repositorio = EvaluacionRepositorioImpl(
        TransaccionDirecta(), evaluaciones, evidencias, operaciones, activos, preferencias, programador,
    )

    private fun payloadDe(indice: Int): EvaluacionDto =
        JsonDanos.decodeFromString(operaciones.filas[indice].payloadJson)

    @Test
    fun `nueva evaluacion encola CREAR con la version del activo y programa la sincronizacion`() = runTest {
        activos.guardar(activoDePrueba(version = 7).aEntidad())

        val resultado = repositorio.guardarNueva(evaluacionDePrueba(), null)

        assertTrue(resultado is Resultado.Exito)
        val operacion = operaciones.filas.single()
        assertEquals(TipoOperacion.CREAR, operacion.tipo)
        assertEquals(7, operacion.versionBaseActivo)
        assertEquals(0, operacion.intentos)
        assertEquals(EstadoEvaluacion.REGISTRADA, evaluaciones.filas.getValue("eval-1").estadoEvaluacion)
        assertEquals(EstadoSync.PENDIENTE, evaluaciones.filas.getValue("eval-1").estadoSync)
        assertNull(payloadDe(0).activoNuevo)
        assertEquals(1, programador.veces)
    }

    @Test
    fun `sin desastre queda preliminar`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(desastreId = null), activoDePrueba(version = null))
        assertEquals(EstadoEvaluacion.PRELIMINAR, evaluaciones.filas.getValue("eval-1").estadoEvaluacion)
    }

    @Test
    fun `el activo creado en campo viaja como activoNuevo sin version base`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba(version = null))

        assertNull(operaciones.filas.single().versionBaseActivo)
        assertEquals("activo-1", payloadDe(0).activoNuevo?.id)
    }

    @Test
    fun `la instantanea incluye los metadatos de las evidencias`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(evidencias = listOf(evidenciaDePrueba())), activoDePrueba())

        val foto = payloadDe(0).evidencias.single()
        assertEquals("foto-1", foto.id)
        assertEquals("abc123", foto.sha256)
        assertEquals(1024L, foto.tamanoBytes)
    }

    @Test
    fun `editar con intentos 0 reemplaza el payload sin crear otra operacion`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        val idOriginal = operaciones.filas.single().idOperacion

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Colapso parcial"))

        val operacion = operaciones.filas.single()
        assertEquals(idOriginal, operacion.idOperacion)
        assertEquals(TipoOperacion.CREAR, operacion.tipo)
        assertEquals("Colapso parcial", payloadDe(0).descripcion)
    }

    @Test
    fun `editar con intentos mayor que 0 crea ACTUALIZAR y no toca la intentada`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba(version = 3))
        val intentada = operaciones.filas.single()
        operaciones.incrementarIntentos(listOf(intentada.idOperacion))
        activos.actualizarVersion("activo-1", 9)

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Colapso parcial"))

        assertEquals(2, operaciones.filas.size)
        assertEquals(intentada.payloadJson, operaciones.filas[0].payloadJson)
        assertEquals(1, operaciones.filas[0].intentos)
        val nueva = operaciones.filas[1]
        assertEquals(TipoOperacion.ACTUALIZAR, nueva.tipo)
        assertEquals(9, nueva.versionBaseActivo)
        assertEquals(0, nueva.intentos)
        assertEquals("Colapso parcial", payloadDe(1).descripcion)
    }

    @Test
    fun `una segunda edicion reemplaza la ACTUALIZAR sin intentar`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        operaciones.incrementarIntentos(listOf(operaciones.filas.single().idOperacion))
        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Segunda"))

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Tercera"))

        assertEquals(2, operaciones.filas.size)
        assertEquals("Muro agrietado", payloadDe(0).descripcion)
        assertEquals("Tercera", payloadDe(1).descripcion)
    }

    @Test
    fun `la edicion vuelve a PENDIENTE y limpia el motivo`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        val idOperacion = operaciones.filas.single().idOperacion
        operaciones.borrar(idOperacion)
        evaluaciones.actualizarEstados("eval-1", EstadoSync.INVALIDA, EstadoEvaluacion.REGISTRADA, "Mal", false)

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Corregida"))

        val fila = evaluaciones.filas.getValue("eval-1")
        assertEquals(EstadoSync.PENDIENTE, fila.estadoSync)
        assertNull(fila.motivoInvalida)
        assertNotNull(operaciones.filas.singleOrNull())
    }

    @Test
    fun `corregir una INVALIDA que el servidor nunca acepto vuelve a enviar CREAR`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        operaciones.borrar(operaciones.filas.single().idOperacion)
        evaluaciones.actualizarEstados("eval-1", EstadoSync.INVALIDA, EstadoEvaluacion.REGISTRADA, "Mal", false)

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Corregida"))

        assertEquals(TipoOperacion.CREAR, operaciones.filas.single().tipo)
    }

    @Test
    fun `corregir una INVALIDA ya conocida por el servidor envia ACTUALIZAR`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        operaciones.borrar(operaciones.filas.single().idOperacion)
        evaluaciones.actualizarEstados("eval-1", EstadoSync.INVALIDA, EstadoEvaluacion.REGISTRADA, "Mal", true)

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Corregida"))

        assertEquals(TipoOperacion.ACTUALIZAR, operaciones.filas.single().tipo)
    }

    @Test
    fun `editar una EN_CONFLICTO no cambia su estado`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        operaciones.borrar(operaciones.filas.single().idOperacion)
        evaluaciones.actualizarEstados("eval-1", EstadoSync.CONFIRMADA, EstadoEvaluacion.EN_CONFLICTO, null, true)

        repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Nueva"))

        assertEquals(EstadoEvaluacion.EN_CONFLICTO, evaluaciones.filas.getValue("eval-1").estadoEvaluacion)
    }

    @Test
    fun `no se puede editar la evaluacion de otro evaluador`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
        preferencias.guardar("Otra Persona", "Defensa Civil", "http://x")
        val payloadAntes = operaciones.filas.single().payloadJson

        val resultado = repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Ajena"))

        assertEquals(TipoError.NO_PERMITIDO, (resultado as Resultado.Error).tipo)
        assertEquals(payloadAntes, operaciones.filas.single().payloadJson)
    }

    @Test
    fun `editar no reinicia la marca de subida de una evidencia guardada`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(evidencias = listOf(evidenciaDePrueba())), activoDePrueba())
        evidencias.marcarSubida("foto-1")

        repositorio.guardarEdicion(
            evaluacionDePrueba(
                evidencias = listOf(evidenciaDePrueba(), evidenciaDePrueba(id = "foto-2")),
            ),
        )

        assertTrue(evidencias.filas.getValue("foto-1").subida)
        assertEquals(setOf("foto-1", "foto-2"), payloadDe(0).evidencias.map { it.id }.toSet())
    }
}
