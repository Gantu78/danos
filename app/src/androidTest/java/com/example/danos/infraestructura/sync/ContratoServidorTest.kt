package com.example.danos.infraestructura.sync

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.danos.acceso.CatalogoRepositorioImpl
import com.example.danos.acceso.EvaluacionRepositorioImpl
import com.example.danos.acceso.IdentidadRepositorio
import com.example.danos.dominio.Activo
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResultadoToken
import com.example.danos.dominio.SesionIdentidad
import com.example.danos.dominio.TipoError
import com.example.danos.infraestructura.di.ModuloRed
import com.example.danos.infraestructura.identidad.ExtractorClaimsJwt
import com.example.danos.infraestructura.local.AppDatabase
import com.example.danos.infraestructura.local.EjecutorTransaccionRoom
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.ConfiguracionInterceptor
import com.example.danos.infraestructura.remoto.JsonDanos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
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
 * Prueba de contrato contra el servidor real (gateway en :8080 o API directa en :5080).
 * Parametrizable por argumentos de instrumentación:
 * - `-e urlApi http://10.0.2.2:8080`
 * - `-e urlEmisor http://10.0.2.2:8180/realms/plataforma`
 * - `-e modoIdentidad auto|oidc|encabezados`
 *
 * En modo OIDC obtiene un token de prueba por Resource Owner Password **solo dentro de esta prueba**
 * con el usuario `evaluador.armenia` y clave `evaluador.armenia_local` (cliente `movil`).
 * Si el servidor no responde, la prueba se omite con `assumeTrue`.
 */
@RunWith(AndroidJUnit4::class)
class ContratoServidorTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val dispositivos = mutableListOf<Dispositivo>()

    private lateinit var urlBaseEfectiva: String
    private var usarModoEncabezados: Boolean = false
    private var tokenOidcPrueba: String? = null
    private var sesionOidcPrueba: SesionIdentidad? = null

    /** Un celular: su propia base local y su propio idDispositivo. */
    private inner class Dispositivo(evaluadorPorDefecto: String) {
        private val usuarioEfectivo = if (usarModoEncabezados) {
            evaluadorPorDefecto
        } else {
            sesionOidcPrueba?.usuario ?: USUARIO_OIDC_PRUEBA
        }
        private val organizacionEfectiva = if (usarModoEncabezados) {
            "Defensa Civil"
        } else {
            sesionOidcPrueba?.organizacion ?: ORGANIZACION_OIDC_PRUEBA
        }

        val config = Configuracion(
            evaluador = usuarioEfectivo,
            organizacion = organizacionEfectiva,
            urlApi = urlBaseEfectiva,
            idDispositivo = "contrato-${UUID.randomUUID()}",
            modoEncabezados = usarModoEncabezados,
        )
        val bd = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        private var marcadorDesdeLocal: String? = null
        private val preferencias = object : PreferenciasLocales {
            override val configuracion: Flow<Configuracion?> = flowOf(config)
            override val ultimaDescarga: Flow<Instant?> = flowOf(null)
            override suspend fun actual() = config
            override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) = Unit
            override suspend fun guardarUltimaDescarga(instante: Instant) = Unit
            override suspend fun obtenerMarcadorDesde(): String? = marcadorDesdeLocal
            override suspend fun guardarMarcadorDesde(desde: String?) {
                marcadorDesdeLocal = desde
            }
        }
        private val identidadPrueba = object : IdentidadRepositorio {
            override val sesion: Flow<SesionIdentidad?> = flowOf(sesionOidcPrueba)
            override val requiereInicioSesion: Flow<Boolean> = flowOf(false)
            override suspend fun sesionActual(): SesionIdentidad? = sesionOidcPrueba
            override suspend fun prepararInicioSesion(urlEmisor: String): Resultado<Intent> =
                Resultado.Error(TipoError.CLIENTE, "No usado en prueba")
            override suspend fun procesarRespuestaAutorizacion(datosResultado: Intent?): Resultado<SesionIdentidad> =
                Resultado.Error(TipoError.CLIENTE, "No usado en prueba")
            override suspend fun obtenerTokenFresco(): ResultadoToken {
                val token = tokenOidcPrueba
                val sesion = sesionOidcPrueba
                return if (!usarModoEncabezados && token != null && sesion != null) {
                    ResultadoToken.Exito(token, sesion)
                } else {
                    ResultadoToken.ModoEncabezados
                }
            }
            override suspend fun marcarReautenticacionRequerida() = Unit
            override suspend fun cerrarSesion(): Resultado<Unit> = Resultado.Exito(Unit)
        }
        private val interceptor = ConfiguracionInterceptor(
            preferencias = preferencias,
            identidad = identidadPrueba,
            permitirModoEncabezados = true,
        )
        private val api = ModuloRed.api(ModuloRed.retrofit(ModuloRed.clienteHttp(interceptor)))
        private val transaccion = EjecutorTransaccionRoom(bd)
        val catalogo = CatalogoRepositorioImpl(api, transaccion, bd.catalogoDao(), bd.activoDao(), preferencias, identidadPrueba)
        val evaluaciones = EvaluacionRepositorioImpl(
            transaccion, bd.evaluacionDao(), bd.evidenciaDao(), bd.operacionDao(), bd.activoDao(), preferencias,
            object : ProgramadorSincronizacion {
                override fun programar() = Unit
            },
            identidadPrueba,
        )
        val motor = MotorSincronizacion(
            transaccion, bd.operacionDao(), bd.evaluacionDao(), bd.evidenciaDao(), bd.activoDao(), api, preferencias, identidadPrueba,
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
        val args = InstrumentationRegistry.getArguments()
        val urlArg = args.getString("urlApi")?.trim()?.takeIf { it.isNotEmpty() }
        val urlEmisor = args.getString("urlEmisor")?.trim()?.takeIf { it.isNotEmpty() } ?: URL_EMISOR_DEFECTO
        val modo = (args.getString("modoIdentidad") ?: "auto").trim().lowercase()
        val usuario = args.getString("usuarioOidc") ?: USUARIO_OIDC_PRUEBA
        val clave = args.getString("claveOidc") ?: CLAVE_OIDC_PRUEBA

        val cliente = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
        val candidatas = if (urlArg != null) listOf(urlArg) else listOf(URL_GATEWAY_DEFECTO, URL_API_DIRECTA_DEFECTO)
        val encontrada = candidatas.firstOrNull { candidata -> respondeSalud(cliente, candidata) }
        urlBaseEfectiva = encontrada ?: candidatas.first()
        assumeTrue("El servidor no está corriendo en $urlBaseEfectiva", encontrada != null)

        when (modo) {
            "encabezados" -> {
                usarModoEncabezados = true
            }
            "oidc" -> {
                val obtenido = obtenerTokenDirectAccessGrant(cliente, urlEmisor, usuario, clave)
                assumeTrue("Keycloak no está disponible en $urlEmisor para modo oidc", obtenido != null)
                tokenOidcPrueba = obtenido!!.first
                sesionOidcPrueba = obtenido.second
                usarModoEncabezados = false
            }
            else -> {
                val obtenido = obtenerTokenDirectAccessGrant(cliente, urlEmisor, usuario, clave)
                if (obtenido != null) {
                    tokenOidcPrueba = obtenido.first
                    sesionOidcPrueba = obtenido.second
                    usarModoEncabezados = false
                } else {
                    usarModoEncabezados = true
                }
            }
        }
    }

    private fun respondeSalud(cliente: OkHttpClient, base: String): Boolean {
        val urlLimpia = base.trimEnd('/')
        for (ruta in listOf("/salud", "/salud/listo", "/salud/vivo")) {
            val ok = runCatching {
                cliente.newCall(Request.Builder().url("$urlLimpia$ruta").build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /**
     * Obtiene un token por Resource Owner Password solo para pruebas automáticas (convenciones.md).
     */
    private fun obtenerTokenDirectAccessGrant(
        cliente: OkHttpClient,
        urlEmisor: String,
        usuario: String,
        clave: String,
    ): Pair<String, SesionIdentidad>? = runCatching {
        val endpoint = "${urlEmisor.trimEnd('/')}/protocol/openid-connect/token"
        val formulario = FormBody.Builder()
            .add("grant_type", "password")
            .add("client_id", "movil")
            .add("username", usuario)
            .add("password", clave)
            .add("scope", "openid profile offline_access")
            .build()
        val peticion = Request.Builder().url(endpoint).post(formulario).build()
        cliente.newCall(peticion).execute().use { respuesta ->
            if (!respuesta.isSuccessful) return null
            val json = JsonDanos.parseToJsonElement(respuesta.body.string()).jsonObject
            val accessToken = json["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
            val idToken = json["id_token"]?.jsonPrimitive?.contentOrNull
            val sesion = ExtractorClaimsJwt.extraerSesion(accessToken, idToken)
                ?: SesionIdentidad(usuario, ORGANIZACION_OIDC_PRUEBA, setOf("evaluador"))
            accessToken to sesion
        }
    }.getOrNull()

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
        assertTrue(b.catalogo.descargar(null, forzarCompleta = true) is Resultado.Exito)
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
        const val URL_GATEWAY_DEFECTO = "http://10.0.2.2:8080"
        const val URL_API_DIRECTA_DEFECTO = "http://10.0.2.2:5080"
        const val URL_EMISOR_DEFECTO = "http://10.0.2.2:8180/realms/plataforma"
        const val USUARIO_OIDC_PRUEBA = "evaluador.armenia"
        const val CLAVE_OIDC_PRUEBA = "evaluador.armenia_local"
        const val ORGANIZACION_OIDC_PRUEBA = "alcaldia-armenia"
        const val TIPO = "carretera"
    }
}
