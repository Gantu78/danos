package com.example.danos.infraestructura.remoto

import com.example.danos.IdentidadFalsa
import com.example.danos.PreferenciasFalsas
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.MENSAJE_INICIAR_SESION
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConfiguracionInterceptorTest {
    private var capturada: Request? = null

    private val servidorFalso = Interceptor { cadena ->
        capturada = cadena.request()
        Response.Builder()
            .request(cadena.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()
    }

    private fun cliente(preferencias: PreferenciasFalsas) = OkHttpClient.Builder()
        .addInterceptor(ConfiguracionInterceptor(preferencias))
        .addInterceptor(servidorFalso)
        .build()

    private fun clienteOidc(
        preferencias: PreferenciasFalsas,
        identidad: IdentidadFalsa,
        permitirModoEncabezados: Boolean = false,
    ) = OkHttpClient.Builder()
        .addInterceptor(ConfiguracionInterceptor(preferencias, identidad, permitirModoEncabezados))
        .addInterceptor(servidorFalso)
        .build()

    private val peticion = Request.Builder().url("http://api.invalid/v1/descarga?desastreId=d1").build()

    @Test
    fun `reescribe esquema, host y puerto y conserva ruta y consulta`() {
        val preferencias = PreferenciasFalsas(Configuracion("Ana", "Org", "https://api.ejemplo.org:8443", "d"))

        cliente(preferencias).newCall(peticion).execute().close()

        assertEquals("https://api.ejemplo.org:8443/v1/descarga?desastreId=d1", capturada!!.url.toString())
    }

    @Test
    fun `envia los tres encabezados aunque lleven tildes`() {
        val preferencias = PreferenciasFalsas(Configuracion("José Núñez", "Organización", "http://10.0.2.2:5080", "d-1"))

        cliente(preferencias).newCall(peticion).execute().close()

        assertEquals("José Núñez", capturada!!.header("X-Usuario"))
        assertEquals("Organización", capturada!!.header("X-Organizacion"))
        assertEquals("d-1", capturada!!.header("X-Dispositivo"))
    }

    @Test
    fun `en modo OIDC envia Authorization Bearer y X-Dispositivo y no envia X-Usuario ni X-Organizacion`() {
        val preferencias = PreferenciasFalsas(
            Configuracion(
                evaluador = "evaluador.armenia",
                organizacion = "alcaldia-armenia",
                urlApi = "https://gateway.plataforma.co",
                idDispositivo = "disp-42",
                modoEncabezados = false,
            ),
        )
        val identidad = IdentidadFalsa(tokenActual = "jwt-acceso-123")

        clienteOidc(preferencias, identidad, permitirModoEncabezados = true).newCall(peticion).execute().close()

        assertEquals("Bearer jwt-acceso-123", capturada!!.header("Authorization"))
        assertEquals("disp-42", capturada!!.header("X-Dispositivo"))
        assertNull(capturada!!.header("X-Usuario"))
        assertNull(capturada!!.header("X-Organizacion"))
        assertNull(capturada!!.header("X-Roles"))
    }

    @Test
    fun `en release ignora modoEncabezados y fuerza OIDC con HTTPS`() {
        val preferencias = PreferenciasFalsas(
            Configuracion(
                evaluador = "Manual",
                organizacion = "ManualOrg",
                urlApi = "https://gateway.plataforma.co",
                idDispositivo = "disp-release",
                modoEncabezados = true,
            ),
        )
        val identidad = IdentidadFalsa(tokenActual = "jwt-release-999")

        clienteOidc(preferencias, identidad, permitirModoEncabezados = false).newCall(peticion).execute().close()

        assertEquals("Bearer jwt-release-999", capturada!!.header("Authorization"))
        assertEquals("disp-release", capturada!!.header("X-Dispositivo"))
        assertNull(capturada!!.header("X-Usuario"))
        assertNull(capturada!!.header("X-Organizacion"))
    }

    @Test
    fun `en release rechaza texto plano HTTP`() {
        val preferencias = PreferenciasFalsas(
            Configuracion("evaluador.armenia", "alcaldia-armenia", "http://10.0.2.2:8080", "disp-1"),
        )
        val identidad = IdentidadFalsa()

        assertThrows(IOException::class.java) {
            clienteOidc(preferencias, identidad, permitirModoEncabezados = false).newCall(peticion).execute()
        }
    }

    @Test
    fun `cuando el refresh token vencio lanza IOException con mensaje de iniciar sesion`() {
        val preferencias = PreferenciasFalsas(
            Configuracion("evaluador.armenia", "alcaldia-armenia", "https://gateway.plataforma.co", "disp-1"),
        )
        val identidad = IdentidadFalsa(refreshTokenVencido = true)

        val ex = assertThrows(IOException::class.java) {
            clienteOidc(preferencias, identidad, permitirModoEncabezados = false).newCall(peticion).execute()
        }
        assertTrue(ex.message.orEmpty().contains(MENSAJE_INICIAR_SESION))
    }

    @Test
    fun `una url guardada invalida es IOException y no tumba la app`() {
        val preferencias = PreferenciasFalsas(Configuracion("Ana", "Org", "no es url", "d"))
        assertThrows(IOException::class.java) { cliente(preferencias).newCall(peticion).execute() }
    }

    @Test
    fun `sin configuracion es IOException`() {
        assertThrows(IOException::class.java) { cliente(PreferenciasFalsas(null)).newCall(peticion).execute() }
    }
}
