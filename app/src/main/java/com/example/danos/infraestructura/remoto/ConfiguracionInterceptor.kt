package com.example.danos.infraestructura.remoto

import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Inyecta los encabezados y dirige cada petición a la URL guardada, sin reiniciar la app.
 * Los fallos se lanzan como IOException: otra excepción en un interceptor tumbaría el proceso.
 * Los nombres en español llevan tildes, por eso los valores se envían en UTF-8 sin validar ASCII.
 */
class ConfiguracionInterceptor @Inject constructor(
    private val preferencias: PreferenciasLocales,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val configuracion = runBlocking { preferencias.actual() }
            ?: throw IOException("La app aún no tiene configuración")
        val base = configuracion.urlApi.toHttpUrlOrNull()
            ?: throw IOException("La URL del API no es válida")
        val original = chain.request()
        val url = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        val encabezados = original.headers.newBuilder()
            .addUnsafeNonAscii("X-Usuario", limpiar(configuracion.evaluador))
            .addUnsafeNonAscii("X-Organizacion", limpiar(configuracion.organizacion))
            .addUnsafeNonAscii("X-Dispositivo", limpiar(configuracion.idDispositivo))
            .build()
        return chain.proceed(original.newBuilder().url(url).headers(encabezados).build())
    }

    private fun limpiar(valor: String): String = valor.replace(Regex("[\\r\\n]+"), " ").trim()
}
