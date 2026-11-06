package com.example.danos.infraestructura.sync

import com.example.danos.ActivoDaoFalso
import com.example.danos.ApiFalsa
import com.example.danos.EvaluacionDaoFalso
import com.example.danos.EvidenciaDaoFalso
import com.example.danos.OperacionDaoFalso
import com.example.danos.PreferenciasFalsas
import com.example.danos.ProgramadorFalso
import com.example.danos.TransaccionDirecta
import com.example.danos.acceso.EvaluacionRepositorioImpl
import com.example.danos.activoDePrueba
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.evaluacionDePrueba
import com.example.danos.evidenciaDePrueba
import com.example.danos.infraestructura.remoto.JsonDanos
import com.example.danos.infraestructura.remoto.LoteSincronizacionDto
import com.example.danos.infraestructura.remoto.RespuestaSincronizacionDto
import com.example.danos.infraestructura.remoto.ResultadoOperacionDto
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MotorSincronizacionTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    private val operaciones = OperacionDaoFalso()
    private val evaluaciones = EvaluacionDaoFalso()
    private val evidencias = EvidenciaDaoFalso(evaluaciones)
    private val activos = ActivoDaoFalso()
    private val preferencias = PreferenciasFalsas()
    private val api = ApiFalsa()
    private val repositorio = EvaluacionRepositorioImpl(
        TransaccionDirecta(), evaluaciones, evidencias, operaciones, activos, preferencias, ProgramadorFalso(),
    )
    private val motor = MotorSincronizacion(
        TransaccionDirecta(), operaciones, evaluaciones, evidencias, activos, api, preferencias,
    )

    private fun responderTodas(
        resultado: String,
        estado: String? = "REGISTRADA",
        motivo: String? = null,
        version: Int? = null,
    ) {
        api.alSincronizar = { lote ->
            RespuestaSincronizacionDto(
                lote.operaciones.map { ResultadoOperacionDto(it.idOperacion, resultado, estado, motivo, version) },
            )
        }
    }

    private fun errorHttp(codigo: Int) = HttpException(Response.error<Any>(codigo, "".toResponseBody()))

    private suspend fun encolarUna() {
        repositorio.guardarNueva(evaluacionDePrueba(), activoDePrueba())
    }

    @Test
    fun `APLICADA borra la operacion y refleja estado y version del servidor`() = runTest {
        encolarUna()
        responderTodas("APLICADA", estado = "VALIDADA", version = 8)

        assertEquals(DesenlaceCiclo.TERMINADO, motor.ejecutarCiclo())

        assertTrue(operaciones.filas.isEmpty())
        val fila = evaluaciones.filas.getValue("eval-1")
        assertEquals(EstadoSync.CONFIRMADA, fila.estadoSync)
        assertEquals(EstadoEvaluacion.VALIDADA, fila.estadoEvaluacion)
        assertEquals(8, activos.filas.getValue("activo-1").versionDescargada)
    }

    @Test
    fun `EN_CONFLICTO borra la operacion, confirma y marca en conflicto`() = runTest {
        encolarUna()
        responderTodas("EN_CONFLICTO", estado = "EN_CONFLICTO")

        motor.ejecutarCiclo()

        assertTrue(operaciones.filas.isEmpty())
        val fila = evaluaciones.filas.getValue("eval-1")
        assertEquals(EstadoSync.CONFIRMADA, fila.estadoSync)
        assertEquals(EstadoEvaluacion.EN_CONFLICTO, fila.estadoEvaluacion)
    }

    @Test
    fun `POSIBLE_DUPLICADO se trata como conflicto`() = runTest {
        encolarUna()
        responderTodas("POSIBLE_DUPLICADO", estado = null)

        motor.ejecutarCiclo()

        assertTrue(operaciones.filas.isEmpty())
        assertEquals(EstadoEvaluacion.EN_CONFLICTO, evaluaciones.filas.getValue("eval-1").estadoEvaluacion)
    }

    @Test
    fun `INVALIDA borra la operacion y guarda el motivo sin borrar la evaluacion`() = runTest {
        encolarUna()
        responderTodas("INVALIDA", estado = null, motivo = "Tipo desconocido")

        motor.ejecutarCiclo()

        assertTrue(operaciones.filas.isEmpty())
        val fila = evaluaciones.filas.getValue("eval-1")
        assertEquals(EstadoSync.INVALIDA, fila.estadoSync)
        assertEquals("Tipo desconocido", fila.motivoInvalida)
    }

    @Test
    fun `una operacion sin resultado se queda y el ciclo termina pidiendo reintento`() = runTest {
        encolarUna()
        val idOperacion = operaciones.filas.single().idOperacion

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())

        assertEquals(idOperacion, operaciones.filas.single().idOperacion)
        assertEquals(EstadoSync.PENDIENTE, evaluaciones.filas.getValue("eval-1").estadoSync)
        assertEquals(1, api.lotes.size)
    }

    @Test
    fun `un resultado desconocido deja la operacion en la cola`() = runTest {
        encolarUna()
        responderTodas("OTRA_COSA")

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())

        assertEquals(1, operaciones.filas.size)
    }

    @Test
    fun `el orden de los resultados no importa porque se cruzan por idOperacion`() = runTest {
        repositorio.guardarNueva(evaluacionDePrueba(id = "a"), activoDePrueba())
        repositorio.guardarNueva(evaluacionDePrueba(id = "b"), activoDePrueba())
        val idA = operaciones.filas[0].idOperacion
        val idB = operaciones.filas[1].idOperacion
        api.alSincronizar = {
            RespuestaSincronizacionDto(
                listOf(
                    ResultadoOperacionDto(idB, "INVALIDA", motivo = "Mal"),
                    ResultadoOperacionDto(idA, "APLICADA", "REGISTRADA"),
                ),
            )
        }

        motor.ejecutarCiclo()

        assertEquals(EstadoSync.CONFIRMADA, evaluaciones.filas.getValue("a").estadoSync)
        assertEquals(EstadoSync.INVALIDA, evaluaciones.filas.getValue("b").estadoSync)
    }

    @Test
    fun `sin red pide reintento y no pierde la operacion`() = runTest {
        encolarUna()
        api.alSincronizar = { throw IOException("sin red") }

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())

        val operacion = operaciones.filas.single()
        assertEquals(1, operacion.intentos)
        assertNull(operacion.ultimoError)
    }

    @Test
    fun `un 500 pide reintento`() = runTest {
        encolarUna()
        api.alSincronizar = { throw errorHttp(503) }

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())
        assertEquals(1, operaciones.filas.size)
    }

    @Test
    fun `un 400 guarda ultimoError y no reintenta en bucle`() = runTest {
        encolarUna()
        api.alSincronizar = { throw errorHttp(400) }

        assertEquals(DesenlaceCiclo.TERMINADO, motor.ejecutarCiclo())

        assertEquals(1, api.lotes.size)
        val operacion = operaciones.filas.single()
        assertTrue(operacion.ultimoError!!.contains("400"))
    }

    @Test
    fun `el lote envia el payload guardado tal cual y lleva hasta 20 operaciones`() = runTest {
        repeat(45) { repositorio.guardarNueva(evaluacionDePrueba(id = "e$it"), activoDePrueba()) }
        val payloads = operaciones.filas.associate { it.idOperacion to it.payloadJson }
        responderTodas("APLICADA")

        motor.ejecutarCiclo()

        assertEquals(listOf(20, 20, 5), api.lotes.map { it.operaciones.size })
        api.lotes.flatMap { it.operaciones }.forEach { enviada ->
            assertEquals(JsonDanos.parseToJsonElement(payloads.getValue(enviada.idOperacion)), enviada.evaluacion)
        }
        assertEquals("disp-1", api.lotes.first().idDispositivo)
    }

    @Test
    fun `una edicion hecha durante el envio queda pendiente aunque llegue la respuesta de la anterior`() = runTest {
        encolarUna()
        val estadosAlEnviar = mutableListOf<EstadoSync>()
        api.alSincronizar = { lote: LoteSincronizacionDto ->
            if (api.lotes.size == 1) {
                // El lote ya salió con la CREAR intentada: esta edición debe crear una ACTUALIZAR aparte.
                repositorio.guardarEdicion(evaluacionDePrueba(descripcion = "Editada en vuelo"))
                assertEquals(2, operaciones.filas.size)
            } else {
                // La CREAR ya se aplicó, pero queda la ACTUALIZAR: la evaluación no puede verse confirmada.
                estadosAlEnviar += evaluaciones.filas.getValue("eval-1").estadoSync
            }
            RespuestaSincronizacionDto(
                lote.operaciones.map { ResultadoOperacionDto(it.idOperacion, "APLICADA", "REGISTRADA") },
            )
        }

        assertEquals(DesenlaceCiclo.TERMINADO, motor.ejecutarCiclo())

        assertEquals(listOf(EstadoSync.PENDIENTE), estadosAlEnviar)
        assertEquals(2, api.lotes.size)
        assertTrue(operaciones.filas.isEmpty())
        assertEquals(EstadoSync.CONFIRMADA, evaluaciones.filas.getValue("eval-1").estadoSync)
    }

    @Test
    fun `QA-14 120 evaluaciones con 5 conflictos y una respuesta perdida`() = runTest {
        repeat(120) { repositorio.guardarNueva(evaluacionDePrueba(id = "e$it"), activoDePrueba()) }
        val conflictos = (0 until 5).map { operaciones.filas[it * 20].idOperacion }.toSet()
        val procesadasServidor = HashMap<String, Int>()
        var perderRespuesta = true
        api.alSincronizar = { lote ->
            lote.operaciones.forEach { procesadasServidor.merge(it.idOperacion, 1, Int::plus) }
            if (perderRespuesta) {
                perderRespuesta = false
                throw IOException("Respuesta perdida")
            }
            RespuestaSincronizacionDto(
                lote.operaciones.map {
                    val conflicto = it.idOperacion in conflictos
                    ResultadoOperacionDto(
                        it.idOperacion,
                        if (conflicto) "EN_CONFLICTO" else "APLICADA",
                        if (conflicto) "EN_CONFLICTO" else "REGISTRADA",
                    )
                },
            )
        }

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())
        assertEquals(DesenlaceCiclo.TERMINADO, motor.ejecutarCiclo())

        assertTrue(operaciones.filas.isEmpty())
        assertEquals(120, evaluaciones.filas.size)
        assertEquals(120, procesadasServidor.size)
        assertEquals(5, evaluaciones.filas.values.count { it.estadoEvaluacion == EstadoEvaluacion.EN_CONFLICTO })
        assertTrue(evaluaciones.filas.values.all { it.estadoSync == EstadoSync.CONFIRMADA })
    }

    @Test
    fun `solo se suben fotos de evaluaciones confirmadas y se marcan con 2xx`() = runTest {
        val archivo = carpeta.newFile("foto.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        repositorio.guardarNueva(
            evaluacionDePrueba(evidencias = listOf(evidenciaDePrueba(ruta = archivo.path))),
            activoDePrueba(),
        )

        assertTrue(motor.subirEvidenciasPendientes())
        assertTrue(api.subidas.isEmpty())

        responderTodas("APLICADA")
        motor.ejecutarCiclo()

        assertEquals(listOf("foto-1"), api.subidas)
        assertTrue(evidencias.filas.getValue("foto-1").subida)
    }

    @Test
    fun `un 422 no marca la foto como subida y no pide reintento`() = runTest {
        val archivo = carpeta.newFile("foto.jpg").apply { writeBytes(byteArrayOf(1)) }
        repositorio.guardarNueva(
            evaluacionDePrueba(evidencias = listOf(evidenciaDePrueba(ruta = archivo.path))),
            activoDePrueba(),
        )
        responderTodas("APLICADA")
        api.alSubir = { Response.error(422, "".toResponseBody()) }

        assertEquals(DesenlaceCiclo.TERMINADO, motor.ejecutarCiclo())

        assertFalse(evidencias.filas.getValue("foto-1").subida)
    }

    @Test
    fun `un 500 al subir pide reintento y conserva la foto pendiente`() = runTest {
        val archivo = carpeta.newFile("foto.jpg").apply { writeBytes(byteArrayOf(1)) }
        repositorio.guardarNueva(
            evaluacionDePrueba(evidencias = listOf(evidenciaDePrueba(ruta = archivo.path))),
            activoDePrueba(),
        )
        responderTodas("APLICADA")
        api.alSubir = { Response.error(500, "".toResponseBody()) }

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())

        assertFalse(evidencias.filas.getValue("foto-1").subida)
    }
}
