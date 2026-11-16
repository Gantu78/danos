package com.example.danos.infraestructura.identidad

import android.net.Uri
import com.example.danos.BuildConfig
import net.openid.appauth.connectivity.ConnectionBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * En release exige estrictamente HTTPS (H-72).
 * En debug admite también HTTP para conectarse al Keycloak local del emulador (http://10.0.2.2:8180).
 */
class ConexionOidcBuilder(
    private val permitirHttpDesarrollo: Boolean = BuildConfig.DEBUG,
) : ConnectionBuilder {

    override fun openConnection(uri: Uri): HttpURLConnection {
        val esquema = uri.scheme?.lowercase()
        val permitido = if (permitirHttpDesarrollo) {
            esquema == "https" || esquema == "http"
        } else {
            esquema == "https"
        }
        if (!permitido) {
            throw IOException("Esquema no permitido para OIDC: $esquema")
        }
        val conexion = URL(uri.toString()).openConnection() as HttpURLConnection
        conexion.connectTimeout = TIEMPO_CONEXION_MS
        conexion.readTimeout = TIEMPO_LECTURA_MS
        conexion.instanceFollowRedirects = false
        return conexion
    }

    private companion object {
        val TIEMPO_CONEXION_MS = TimeUnit.SECONDS.toMillis(15).toInt()
        val TIEMPO_LECTURA_MS = TimeUnit.SECONDS.toMillis(10).toInt()
    }
}
