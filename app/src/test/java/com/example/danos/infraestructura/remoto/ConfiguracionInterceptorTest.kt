package com.example.danos.infraestructura.remoto

import com.example.danos.PreferenciasFalsas
import com.example.danos.dominio.Configuracion
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun `una url guardada invalida es IOException y no tumba la app`() {
        val preferencias = PreferenciasFalsas(Configuracion("Ana", "Org", "no es url", "d"))
        assertThrows(IOException::class.java) { cliente(preferencias).newCall(peticion).execute() }
    }

    @Test
    fun `sin configuracion es IOException`() {
        assertThrows(IOException::class.java) { cliente(PreferenciasFalsas(null)).newCall(peticion).execute() }
    }
}
