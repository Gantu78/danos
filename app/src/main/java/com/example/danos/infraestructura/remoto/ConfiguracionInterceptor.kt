package com.example.danos.infraestructura.remoto

import com.example.danos.BuildConfig
import com.example.danos.acceso.IdentidadRepositorio
import com.example.danos.dominio.ResultadoToken
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Dirige cada petición a la URL guardada e inyecta la identidad según el modo (H-70, DA-15):
 * - En modo OIDC (único modo en release, y modo por defecto en debug): agrega Authorization: Bearer <token>
 *   y X-Dispositivo, y NO envía X-Usuario ni X-Organizacion.
 * - En modo de transición (solo en debug cuando está activo): envía X-Usuario, X-Organizacion y X-Dispositivo.
 * Los fallos se lanzan como IOException: otra excepción en un interceptor tumbaría el proceso.
 */
class ConfiguracionInterceptor(
    private val preferencias: PreferenciasLocales,
    private val identidad: IdentidadRepositorio?,
    private val permitirModoEncabezados: Boolean = BuildConfig.DEBUG,
) : Interceptor {

    @Inject
    constructor(
        preferencias: PreferenciasLocales,
        identidad: IdentidadRepositorio,
    ) : this(preferencias, identidad, BuildConfig.DEBUG)

    /** Constructor de compatibilidad para pruebas que ejercitan directamente el modo de encabezados. */
    constructor(preferencias: PreferenciasLocales) : this(preferencias, null, true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val configuracion = runBlocking { preferencias.actual() }
            ?: throw IOException("La app aún no tiene configuración")
        val base = configuracion.urlApi.toHttpUrlOrNull()
            ?: throw IOException("La URL del API no es válida")
        if (!permitirModoEncabezados && base.scheme != "https") {
            throw IOException("En producción la URL del servidor debe usar HTTPS")
        }

        val original = chain.request()
        val url = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()

        val usarEncabezados = permitirModoEncabezados && (identidad == null || configuracion.modoEncabezados)
        val encabezados = if (usarEncabezados) {
            original.headers.newBuilder()
                .removeAll("Authorization")
                .addUnsafeNonAscii("X-Usuario", limpiar(configuracion.evaluador))
                .addUnsafeNonAscii("X-Organizacion", limpiar(configuracion.organizacion))
                .addUnsafeNonAscii("X-Roles", "evaluador")
                .addUnsafeNonAscii("X-Dispositivo", limpiar(configuracion.idDispositivo))
                .build()
        } else {
            val repoIdentidad = identidad ?: throw IOException("No hay proveedor de identidad OIDC configurado")
            val token = when (val resultado = runBlocking { repoIdentidad.obtenerTokenFresco() }) {
                is ResultadoToken.Exito -> resultado.accessToken
                is ResultadoToken.SesionExpirada -> throw IOException(resultado.mensaje)
                is ResultadoToken.ErrorRed -> throw IOException(resultado.mensaje)
                ResultadoToken.ModoEncabezados -> throw IOException("Modo de identidad no permitido")
            }
            original.headers.newBuilder()
                .removeAll("X-Usuario")
                .removeAll("X-Organizacion")
                .removeAll("X-Roles")
                .set("Authorization", "Bearer ${limpiar(token)}")
                .addUnsafeNonAscii("X-Dispositivo", limpiar(configuracion.idDispositivo))
                .build()
        }

        return chain.proceed(original.newBuilder().url(url).headers(encabezados).build())
    }

    private fun limpiar(valor: String): String = valor.replace(Regex("[\\r\\n]+"), " ").trim()
}
