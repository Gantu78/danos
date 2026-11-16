package com.example.danos.infraestructura.preferencias

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.danos.BuildConfig
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.EMISOR_OIDC_POR_DEFECTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

interface PreferenciasLocales {
    val configuracion: Flow<Configuracion?>
    val ultimaDescarga: Flow<Instant?>
    val marcadorDesde: Flow<String?> get() = flowOf(null)
    val authStateCifrado: Flow<String?> get() = flowOf(null)
    val requiereReautenticacion: Flow<Boolean> get() = flowOf(false)

    suspend fun actual(): Configuracion?
    suspend fun guardar(evaluador: String, organizacion: String, urlApi: String)
    suspend fun guardarConfiguracionCompleta(
        evaluador: String,
        organizacion: String,
        urlApi: String,
        urlEmisor: String = EMISOR_OIDC_POR_DEFECTO,
        modoEncabezados: Boolean = false,
    ) = guardar(evaluador, organizacion, urlApi)

    suspend fun guardarUltimaDescarga(instante: Instant)
    suspend fun obtenerMarcadorDesde(): String? = null
    suspend fun guardarMarcadorDesde(desde: String?) = Unit
    suspend fun obtenerAuthStateCifrado(): String? = null
    suspend fun guardarAuthStateCifrado(
        cifrado: String?,
        evaluador: String? = null,
        organizacion: String? = null,
    ) = Unit

    suspend fun guardarRequiereReautenticacion(requiere: Boolean) = Unit
}

class PreferenciasDataStore @Inject constructor(
    private val almacen: DataStore<Preferences>,
) : PreferenciasLocales {

    override val configuracion: Flow<Configuracion?> = almacen.data.map { datos ->
        val evaluador = datos[EVALUADOR]
        val organizacion = datos[ORGANIZACION]
        val url = datos[URL_API]
        val dispositivo = datos[ID_DISPOSITIVO]
        val emisor = datos[URL_EMISOR] ?: EMISOR_OIDC_POR_DEFECTO
        val modoEncabezados = BuildConfig.DEBUG && (datos[MODO_ENCABEZADOS] ?: false)
        if (evaluador == null || organizacion == null || url == null || dispositivo == null) {
            null
        } else {
            Configuracion(
                evaluador = evaluador,
                organizacion = organizacion,
                urlApi = url,
                idDispositivo = dispositivo,
                urlEmisor = emisor,
                modoEncabezados = modoEncabezados,
            )
        }
    }

    override val ultimaDescarga: Flow<Instant?> =
        almacen.data.map { datos -> datos[ULTIMA_DESCARGA]?.let(Instant::ofEpochMilli) }

    override val marcadorDesde: Flow<String?> =
        almacen.data.map { datos -> datos[MARCADOR_DESDE] }

    override val authStateCifrado: Flow<String?> =
        almacen.data.map { datos -> datos[AUTH_STATE_CIFRADO] }

    override val requiereReautenticacion: Flow<Boolean> =
        almacen.data.map { datos -> datos[REQUIERE_REAUTENTICACION] ?: false }

    override suspend fun actual(): Configuracion? = configuracion.first()

    /** El idDispositivo se genera una sola vez y sobrevive a los cambios de evaluador o cierres de sesión. */
    override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) {
        almacen.edit { datos ->
            datos[EVALUADOR] = evaluador
            datos[ORGANIZACION] = organizacion
            datos[URL_API] = urlApi
            if (datos[URL_EMISOR] == null) datos[URL_EMISOR] = EMISOR_OIDC_POR_DEFECTO
            if (datos[ID_DISPOSITIVO] == null) datos[ID_DISPOSITIVO] = UUID.randomUUID().toString()
        }
    }

    override suspend fun guardarConfiguracionCompleta(
        evaluador: String,
        organizacion: String,
        urlApi: String,
        urlEmisor: String,
        modoEncabezados: Boolean,
    ) {
        almacen.edit { datos ->
            datos[EVALUADOR] = evaluador
            datos[ORGANIZACION] = organizacion
            datos[URL_API] = urlApi
            datos[URL_EMISOR] = urlEmisor
            datos[MODO_ENCABEZADOS] = BuildConfig.DEBUG && modoEncabezados
            if (datos[ID_DISPOSITIVO] == null) datos[ID_DISPOSITIVO] = UUID.randomUUID().toString()
        }
    }

    override suspend fun guardarUltimaDescarga(instante: Instant) {
        almacen.edit { datos -> datos[ULTIMA_DESCARGA] = instante.toEpochMilli() }
    }

    override suspend fun obtenerMarcadorDesde(): String? = marcadorDesde.first()

    override suspend fun guardarMarcadorDesde(desde: String?) {
        almacen.edit { datos ->
            if (desde.isNullOrBlank()) {
                datos.remove(MARCADOR_DESDE)
            } else {
                datos[MARCADOR_DESDE] = desde
            }
        }
    }

    override suspend fun obtenerAuthStateCifrado(): String? = authStateCifrado.first()

    override suspend fun guardarAuthStateCifrado(
        cifrado: String?,
        evaluador: String?,
        organizacion: String?,
    ) {
        almacen.edit { datos ->
            if (cifrado == null) {
                datos.remove(AUTH_STATE_CIFRADO)
            } else {
                datos[AUTH_STATE_CIFRADO] = cifrado
            }
            if (evaluador != null) datos[EVALUADOR] = evaluador
            if (organizacion != null) datos[ORGANIZACION] = organizacion
            if (datos[ID_DISPOSITIVO] == null) datos[ID_DISPOSITIVO] = UUID.randomUUID().toString()
        }
    }

    override suspend fun guardarRequiereReautenticacion(requiere: Boolean) {
        almacen.edit { datos -> datos[REQUIERE_REAUTENTICACION] = requiere }
    }

    private companion object {
        val EVALUADOR = stringPreferencesKey("evaluador")
        val ORGANIZACION = stringPreferencesKey("organizacion")
        val URL_API = stringPreferencesKey("url_api")
        val URL_EMISOR = stringPreferencesKey("url_emisor")
        val MODO_ENCABEZADOS = booleanPreferencesKey("modo_encabezados")
        val ID_DISPOSITIVO = stringPreferencesKey("id_dispositivo")
        val ULTIMA_DESCARGA = longPreferencesKey("ultima_descarga")
        val MARCADOR_DESDE = stringPreferencesKey("marcador_desde")
        val AUTH_STATE_CIFRADO = stringPreferencesKey("auth_state_cifrado")
        val REQUIERE_REAUTENTICACION = booleanPreferencesKey("requiere_reautenticacion")
    }
}
