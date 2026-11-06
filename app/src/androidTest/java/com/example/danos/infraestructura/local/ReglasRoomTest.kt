package com.example.danos.infraestructura.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.danos.acceso.CatalogoRepositorioImpl
import com.example.danos.acceso.EvaluacionRepositorioImpl
import com.example.danos.dominio.Activo
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.Resultado
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.ActivoDto
import com.example.danos.infraestructura.remoto.DanosApi
import com.example.danos.infraestructura.remoto.DescargaDto
import com.example.danos.infraestructura.remoto.LoteSincronizacionDto
import com.example.danos.infraestructura.remoto.RespuestaSincronizacionDto
import com.example.danos.infraestructura.remoto.ResultadoOperacionDto
import com.example.danos.infraestructura.sync.DesenlaceCiclo
import com.example.danos.infraestructura.sync.MotorSincronizacion
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ReglasRoomTest {
    private lateinit var bd: AppDatabase
    private lateinit var evaluaciones: EvaluacionRepositorioImpl
    private lateinit var catalogo: CatalogoRepositorioImpl
    private lateinit var motor: MotorSincronizacion
    private var descarga = DescargaDto("2026-01-01T00:00:00Z")
    private var alSincronizar: suspend (LoteSincronizacionDto) -> RespuestaSincronizacionDto =
        { RespuestaSincronizacionDto() }

    private val preferencias = object : PreferenciasLocales {
        private val config = Configuracion("Ana", "Org", "http://localhost", "disp")
        override val configuracion: Flow<Configuracion?> = flowOf(config)
        override val ultimaDescarga: Flow<Instant?> = flowOf(null)
        override suspend fun actual() = config
        override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) = Unit
        override suspend fun guardarUltimaDescarga(instante: Instant) = Unit
    }

    private val api = object : DanosApi {
        override suspend fun descargar(desastreId: String?) = descarga
        override suspend fun sincronizar(lote: LoteSincronizacionDto) = alSincronizar(lote)
        override suspend fun subirEvidencia(id: String, archivo: MultipartBody.Part) = Response.success(Unit)
    }

    private val programador = object : ProgramadorSincronizacion {
        override fun programar() = Unit
    }

    @Before
    fun crear() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        bd = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        val transaccion = EjecutorTransaccionRoom(bd)
        evaluaciones = EvaluacionRepositorioImpl(
            transaccion, bd.evaluacionDao(), bd.evidenciaDao(), bd.operacionDao(), bd.activoDao(), preferencias, programador,
        )
        catalogo = CatalogoRepositorioImpl(api, transaccion, bd.catalogoDao(), bd.activoDao(), preferencias)
        motor = MotorSincronizacion(
            transaccion, bd.operacionDao(), bd.evaluacionDao(), bd.evidenciaDao(), bd.activoDao(), api, preferencias,
        )
    }

    @After
    fun cerrar() = bd.close()

    private fun evaluacion(id: String = "e1", activoId: String = "a1", evidencias: List<Evidencia> = emptyList()) = Evaluacion(
        id = id,
        activoId = activoId,
        desastreId = null,
        instanteCampo = Instant.parse("2026-03-01T10:00:00.123Z"),
        evaluador = "Ana",
        organizacion = "Org",
        lat = 4.6,
        lon = -74.1,
        precisionM = 5.0,
        tipoActivoCodigo = "VIV",
        nivelDano = NivelDano.LEVE,
        descripcion = "Fisuras",
        evidencias = evidencias,
    )

    private fun activo(id: String = "a1", version: Int? = null) = Activo(id, "VIV", 4.6, -74.1, "Ref $id", version)

    @Test
    fun guardarEvaluacionYOperacionEsAtomico() = runTest {
        val evidenciaHuerfana = Evidencia(
            id = "f1",
            evaluacionId = "no-existe",
            rutaArchivo = "/x.jpg",
            lat = 0.0,
            lon = 0.0,
            instanteCaptura = Instant.EPOCH,
            sha256 = "h",
            tamanoBytes = 1,
        )

        val resultado = evaluaciones.guardarNueva(evaluacion(evidencias = listOf(evidenciaHuerfana)), activo())

        assertTrue(resultado is Resultado.Error)
        assertNull(bd.evaluacionDao().obtener("e1"))
        assertNull(bd.activoDao().obtener("a1"))
        assertEquals(0, bd.operacionDao().contar())
    }

    @Test
    fun guardarDejaEvaluacionYOperacionJuntas() = runTest {
        assertTrue(evaluaciones.guardarNueva(evaluacion(), activo()) is Resultado.Exito)
        assertNotNull(bd.evaluacionDao().obtener("e1"))
        assertEquals(1, bd.operacionDao().contar())
    }

    @Test
    fun unaOperacionSinRespuestaNoSeBorra() = runTest {
        evaluaciones.guardarNueva(evaluacion(), activo())
        val id = bd.operacionDao().primeras(1).single().idOperacion

        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())
        assertEquals(1, bd.operacionDao().obtener(id)?.intentos)

        alSincronizar = { throw IOException("sin red") }
        assertEquals(DesenlaceCiclo.REINTENTAR, motor.ejecutarCiclo())
        assertEquals(2, bd.operacionDao().obtener(id)?.intentos)
    }

    @Test
    fun laAplicadaBorraSoloLaOperacionRespondida() = runTest {
        evaluaciones.guardarNueva(evaluacion("e1"), activo("a1"))
        evaluaciones.guardarNueva(evaluacion("e2", "a2"), activo("a2"))
        val respondida = bd.operacionDao().primeras(1).single().idOperacion
        alSincronizar = {
            RespuestaSincronizacionDto(listOf(ResultadoOperacionDto(respondida, "APLICADA", "REGISTRADA", null, 5)))
        }

        motor.enviarSiguienteLote()

        assertEquals(1, bd.operacionDao().contar())
        assertNull(bd.operacionDao().obtener(respondida))
        assertEquals(5, bd.activoDao().obtener("a1")?.versionDescargada)
    }

    @Test
    fun laDescargaNoBorraEvaluacionesNiActivosDeCampo() = runTest {
        evaluaciones.guardarNueva(evaluacion("e1", "campo"), activo("campo", version = null))
        bd.activoDao().guardar(activo("viejo", version = 1).aEntidad())
        descarga = DescargaDto(
            instanteServidor = "2026-01-01T00:00:00Z",
            activos = listOf(ActivoDto("nuevo", "VIV", 1.0, 2.0, "Nueva", 1)),
        )

        assertTrue(catalogo.descargar(null) is Resultado.Exito)

        assertNotNull(bd.evaluacionDao().obtener("e1"))
        assertNotNull(bd.activoDao().obtener("campo"))
        assertNotNull(bd.activoDao().obtener("nuevo"))
        assertNull(bd.activoDao().obtener("viejo"))
    }

    @Test
    fun laDescargaNoTocaActivosConOperacionesPendientes() = runTest {
        bd.activoDao().guardar(activo("a1", version = 2).aEntidad())
        evaluaciones.guardarNueva(evaluacion("e1", "a1"), null)
        descarga = DescargaDto(
            instanteServidor = "2026-01-01T00:00:00Z",
            activos = listOf(ActivoDto("a1", "VIV", 1.0, 2.0, "Cambiada", 99)),
        )

        catalogo.descargar(null)

        assertEquals(2, bd.activoDao().obtener("a1")?.versionDescargada)
    }

    @Test
    fun laDescargaFallidaConservaLoDescargado() = runTest {
        bd.catalogoDao().insertarTipos(listOf(TipoActivoEntity("VIV", "Vivienda")))
        val fallida = object : DanosApi by api {
            override suspend fun descargar(desastreId: String?): DescargaDto = throw IOException("sin red")
        }
        val repo = CatalogoRepositorioImpl(fallida, EjecutorTransaccionRoom(bd), bd.catalogoDao(), bd.activoDao(), preferencias)

        assertTrue(repo.descargar(null) is Resultado.Error)
        assertEquals(1, repo.observarTipos().first().size)
    }
}
