package com.example.danos.infraestructura.preferencias

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.danos.dominio.Configuracion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

interface PreferenciasLocales {
    val configuracion: Flow<Configuracion?>
    val ultimaDescarga: Flow<Instant?>
    suspend fun actual(): Configuracion?
    suspend fun guardar(evaluador: String, organizacion: String, urlApi: String)
    suspend fun guardarUltimaDescarga(instante: Instant)
}

class PreferenciasDataStore @Inject constructor(
    private val almacen: DataStore<Preferences>,
) : PreferenciasLocales {

    override val configuracion: Flow<Configuracion?> = almacen.data.map { datos ->
        val evaluador = datos[EVALUADOR]
        val organizacion = datos[ORGANIZACION]
        val url = datos[URL_API]
        val dispositivo = datos[ID_DISPOSITIVO]
        if (evaluador == null || organizacion == null || url == null || dispositivo == null) {
            null
        } else {
            Configuracion(evaluador, organizacion, url, dispositivo)
        }
    }

    override val ultimaDescarga: Flow<Instant?> =
        almacen.data.map { datos -> datos[ULTIMA_DESCARGA]?.let(Instant::ofEpochMilli) }

    override suspend fun actual(): Configuracion? = configuracion.first()

    /** El idDispositivo se genera una sola vez y sobrevive a los cambios de evaluador. */
    override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) {
        almacen.edit { datos ->
            datos[EVALUADOR] = evaluador
            datos[ORGANIZACION] = organizacion
            datos[URL_API] = urlApi
            if (datos[ID_DISPOSITIVO] == null) datos[ID_DISPOSITIVO] = UUID.randomUUID().toString()
        }
    }

    override suspend fun guardarUltimaDescarga(instante: Instant) {
        almacen.edit { datos -> datos[ULTIMA_DESCARGA] = instante.toEpochMilli() }
    }

    private companion object {
        val EVALUADOR = stringPreferencesKey("evaluador")
        val ORGANIZACION = stringPreferencesKey("organizacion")
        val URL_API = stringPreferencesKey("url_api")
        val ID_DISPOSITIVO = stringPreferencesKey("id_dispositivo")
        val ULTIMA_DESCARGA = longPreferencesKey("ultima_descarga")
    }
}
