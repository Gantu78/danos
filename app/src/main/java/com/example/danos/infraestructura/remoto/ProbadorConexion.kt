package com.example.danos.infraestructura.remoto

import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Comprueba que una URL escrita en Ingreso llega al API, antes de guardarla. Usa su propio cliente:
 * el de la app reescribe el host con la URL ya guardada y exige configuración.
 */
class ProbadorConexion(private val cliente: OkHttpClient) {

    @Inject
    constructor() : this(
        OkHttpClient.Builder()
            .connectTimeout(TIEMPO_MAXIMO_S, TimeUnit.SECONDS)
            .readTimeout(TIEMPO_MAXIMO_S, TimeUnit.SECONDS)
            .callTimeout(TIEMPO_MAXIMO_S * 2, TimeUnit.SECONDS)
            .build(),
    )

    suspend fun probar(urlApi: String): Resultado<Unit> = withContext(Dispatchers.IO) {
        val salud = urlApi.trim().toHttpUrlOrNull()?.newBuilder()?.encodedPath("/salud")?.build()
            ?: return@withContext Resultado.Error(TipoError.CLIENTE, "La URL no es válida")
        try {
            cliente.newCall(Request.Builder().url(salud).build()).execute().use { respuesta ->
                val cuerpo = respuesta.body.string()
                when {
                    respuesta.isSuccessful && cuerpo.contains("\"estado\"") -> Resultado.Exito(Unit)
                    respuesta.isSuccessful ->
                        Resultado.Error(TipoError.CLIENTE, "Esa dirección responde, pero no es el API de evaluación de daños")
                    else -> Resultado.Error(TipoError.SERVIDOR, "El servidor respondió con error (HTTP ${respuesta.code})")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Resultado.Error(
                TipoError.SIN_RED,
                "No hay respuesta. Revisa la IP del PC, que el servidor esté encendido, que estén en la misma Wi-Fi y el firewall del PC.",
            )
        }
    }

    private companion object {
        const val TIEMPO_MAXIMO_S = 5L
    }
}
