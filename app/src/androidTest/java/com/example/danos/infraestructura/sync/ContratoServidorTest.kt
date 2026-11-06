package com.example.danos.infraestructura.sync

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.danos.acceso.CatalogoRepositorioImpl
import com.example.danos.acceso.EvaluacionRepositorioImpl
import com.example.danos.dominio.Activo
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.Resultado
import com.example.danos.infraestructura.di.ModuloRed
import com.example.danos.infraestructura.local.AppDatabase
import com.example.danos.infraestructura.local.EjecutorTransaccionRoom
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.ConfiguracionInterceptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Prueba de contrato contra el servidor .NET real en http://10.0.2.2:5080, con la misma pila de red,
 * Room y sincronización que usa la app. Si el servidor no responde, se omite.
 */
@RunWith(AndroidJUnit4::class)
class ContratoServidorTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val dispositivos = mutableListOf<Dispositivo>()

    /** Un celular: su propia base local y su propio idDispositivo. */
    private inner class Dispositivo(evaluador: String) {
        val config = Configuracion(evaluador, "Defensa Civil", URL, "contrato-${UUID.randomUUID()}")
        val bd = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        private val preferencias = object : PreferenciasLocales {
            override val configuracion: Flow<Configuracion?> = flowOf(config)
            override val ultimaDescarga: Flow<Instant?> = flowOf(null)
            override suspend fun actual() = config
            override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) = Unit
            override suspend fun guardarUltimaDescarga(instante: Instant) = Unit
        }
        private val api = ModuloRed.api(ModuloRed.retrofit(ModuloRed.clienteHttp(ConfiguracionInterceptor(preferencias))))
        private val transaccion = EjecutorTransaccionRoom(bd)
        val catalogo = CatalogoRepositorioImpl(api, transaccion, bd.catalogoDao(), bd.activoDao(), preferencias)
        val evaluaciones = EvaluacionRepositorioImpl(
            transaccion, bd.evaluacionDao(), bd.evidenciaDao(), bd.operacionDao(), bd.activoDao(), preferencias,
            object : ProgramadorSincronizacion {
                override fun programar() = Unit
            },
        )
        val motor = MotorSincronizacion(
            transaccion, bd.operacionDao(), bd.evaluacionDao(), bd.evidenciaDao(), bd.activoDao(), api, preferencias,
        )

        init {
            dispositivos += this
        }

        fun evaluacion(activoId: String, lat: Double, lon: Double, evidencias: List<Evidencia> = emptyList(), id: String = UUID.randomUUID().toString()) =
            Evaluacion(
                id = id,
                activoId = activoId,
                desastreId = null,
                instanteCampo = Instant.now().truncatedTo(ChronoUnit.MILLIS),
                evaluador = config.evaluador,
                organizacion = config.organizacion,
                lat = lat,
                lon = lon,
                precisionM = 6.0,
                tipoActivoCodigo = TIPO,
                nivelDano = NivelDano.MODERADO,
                descripcion = "Prueba de contrato",
                evidencias = evidencias,
            )
    }

    @Before
    fun servidorDisponible() {
        val disponible = runCatching {
            OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url("$URL/salud").build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        assumeTrue("El servidor no está corriendo en $URL", disponible)
    }

    @After
    fun cerrar() = dispositivos.forEach { it.bd.close() }

    /** Coordenadas al azar para que corridas repetidas no se detecten como duplicados entre sí. */
    private fun puntoAlAzar() = Pair(4.0 + Random.nextDouble(0.0, 1.0), -74.5 + Random.nextDouble(0.0, 1.0))

    private fun fotoReal(evaluacionId: String, lat: Double, lon: Double): Evidencia {
        val id = UUID.randomUUID().toString()
        val archivo = File(contexto.filesDir, "evidencias/$id.jpg").apply { parentFile?.mkdirs() }
        val mapa = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        archivo.outputStream().use { mapa.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        val hash = MessageDigest.getInstance("SHA-256").digest(archivo.readBytes()).joinToString("") { "%02x".format(it) }
        return Evidencia(id, evaluacionId, archivo.path, lat, lon, Instant.now().truncatedTo(ChronoUnit.MILLIS), hash, archivo.length())
    }

    @Test
    fun descargaCatalogoConEncabezadosConTildes() = runTest {
        val celular = Dispositivo("José Núñez")

        assertTrue(celular.catalogo.descargar(null) is Resultado.Exito)

        assertTrue(celular.catalogo.observarTipos().first().any { it.codigo == TIPO })
        assertTrue(celular.catalogo.observarCriterios().first().any { it.tipoCodigo == TIPO })
        assertTrue(celular.catalogo.observarDesastres().first().isNotEmpty())
    }

    @Test
    fun activoNuevoConFotoSeAplicaYLaFotoSeSube() = runTest {
        val celular = Dispositivo("Ana Pérez")
        val (lat, lon) = puntoAlAzar()
        val activo = Activo(UUID.randomUUID().toString(), TIPO, lat, lon, "Vía de prueba", null)
        val evaluacionId = UUID.randomUUID().toString()
        val foto = fotoReal(evaluacionId, lat, lon)

        celular.evaluaciones.guardarNueva(celular.evaluacion(activo.id, lat, lon, listOf(foto), evaluacionId), activo)
        assertEquals(DesenlaceCiclo.TERMINADO, celular.motor.ejecutarCiclo())

        val guardada = celular.evaluaciones.obtener(evaluacionId)!!
        assertEquals(EstadoSync.CONFIRMADA, guardada.estadoSync)
        assertEquals(EstadoEvaluacion.PRELIMINAR, guardada.estadoEvaluacion)
        assertTrue(guardada.evidencias.single().subida)
        assertNotNull(celular.bd.activoDao().obtener(activo.id)?.versionDescargada)
        assertEquals(0, celular.bd.operacionDao().contar())

        // Editar después de confirmada envía ACTUALIZAR con la versión que devolvió el servidor.
        celular.evaluaciones.guardarEdicion(guardada.copy(descripcion = "Editada", nivelDano = NivelDano.GRAVE))
        assertEquals(DesenlaceCiclo.TERMINADO, celular.motor.ejecutarCiclo())
        assertEquals(EstadoSync.CONFIRMADA, celular.evaluaciones.obtener(evaluacionId)!!.estadoSync)
    }

    @Test
    fun crearYEditarSinRedNoChocaConsigoMismo() = runTest {
        val celular = Dispositivo("Ana Pérez")
        val (lat, lon) = puntoAlAzar()
        val activo = Activo(UUID.randomUUID().toString(), TIPO, lat, lon, "Puente", null)
        val evaluacion = celular.evaluacion(activo.id, lat, lon)

        celular.evaluaciones.guardarNueva(evaluacion, activo)
        // Simula que el primer envío salió pero no llegó respuesta: la edición crea una ACTUALIZAR aparte.
        celular.bd.operacionDao().incrementarIntentos(celular.bd.operacionDao().primeras(1).map { it.idOperacion })
        celular.evaluaciones.guardarEdicion(evaluacion.copy(descripcion = "Editada sin red"))
        assertEquals(2, celular.bd.operacionDao().contar())

        assertEquals(DesenlaceCiclo.TERMINADO, celular.motor.ejecutarCiclo())

        val final = celular.evaluaciones.obtener(evaluacion.id)!!
        assertEquals(EstadoSync.CONFIRMADA, final.estadoSync)
        assertEquals(EstadoEvaluacion.PRELIMINAR, final.estadoEvaluacion)
    }

    @Test
    fun otroDispositivoCambiaElActivoYLaEdicionQuedaEnConflicto() = runTest {
        val a = Dispositivo("Ana Pérez")
        val b = Dispositivo("Luis Gómez")
        val (lat, lon) = puntoAlAzar()
        val activo = Activo(UUID.randomUUID().toString(), TIPO, lat, lon, "Escuela", null)

        val deA = a.evaluacion(activo.id, lat, lon)
        a.evaluaciones.guardarNueva(deA, activo)
        a.motor.ejecutarCiclo()

        // B descarga el activo ya registrado y lo evalúa con la versión vigente.
        assertTrue(b.catalogo.descargar(null) is Resultado.Exito)
        assertNotNull(b.bd.activoDao().obtener(activo.id))
        b.evaluaciones.guardarNueva(b.evaluacion(activo.id, lat, lon), null)
        b.motor.ejecutarCiclo()

        // A edita con su versión vieja: el servidor detecta el cambio de B y no sobrescribe.
        a.evaluaciones.guardarEdicion(a.evaluaciones.obtener(deA.id)!!.copy(descripcion = "Edición tardía"))
        a.motor.ejecutarCiclo()

        val final = a.evaluaciones.obtener(deA.id)!!
        assertEquals(EstadoEvaluacion.EN_CONFLICTO, final.estadoEvaluacion)
        assertEquals(EstadoSync.CONFIRMADA, final.estadoSync)
        assertEquals(0, a.bd.operacionDao().contar())
    }

    @Test
    fun unaInvalidaCorregidaSeVuelveACrearYSeAplica() = runTest {
        val celular = Dispositivo("Ana Pérez")
        val (lat, lon) = puntoAlAzar()
        val activo = Activo(UUID.randomUUID().toString(), TIPO, lat, lon, "Hospital", null)
        val mala = celular.evaluacion(activo.id, lat, lon).copy(desastreId = UUID.randomUUID().toString())

        celular.evaluaciones.guardarNueva(mala, activo)
        celular.motor.ejecutarCiclo()

        val invalida = celular.evaluaciones.obtener(mala.id)!!
        assertEquals(EstadoSync.INVALIDA, invalida.estadoSync)
        assertNotNull(invalida.motivoInvalida)

        celular.evaluaciones.guardarEdicion(invalida.copy(desastreId = null))
        celular.motor.ejecutarCiclo()

        assertEquals(EstadoSync.CONFIRMADA, celular.evaluaciones.obtener(mala.id)!!.estadoSync)
    }

    private companion object {
        const val URL = "http://10.0.2.2:5080"
        const val TIPO = "carretera"
    }
}
