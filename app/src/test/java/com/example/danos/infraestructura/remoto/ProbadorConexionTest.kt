package com.example.danos.infraestructura.remoto

import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoError
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProbadorConexionTest {
    private var urlPedida: String? = null
    private val rutasPedidas = mutableListOf<String>()

    private fun probador(codigo: Int = 200, cuerpo: String = """{"estado":"ok"}""", fallar: Boolean = false) =
        probadorPorRuta(fallar) { codigo to cuerpo }

    private fun probadorPorRuta(fallar: Boolean = false, responder: (String) -> Pair<Int, String>) =
        ProbadorConexion(
            OkHttpClient.Builder().addInterceptor(
                Interceptor { cadena ->
                    urlPedida = cadena.request().url.toString()
                    rutasPedidas += cadena.request().url.encodedPath
                    if (fallar) throw IOException("sin ruta")
                    val (codigo, cuerpo) = responder(cadena.request().url.encodedPath)
                    Response.Builder()
                        .request(cadena.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(codigo)
                        .message("x")
                        .body(cuerpo.toResponseBody())
                        .build()
                },
            ).build(),
        )

    @Test
    fun `contra el gateway basta salud listo aunque salud caiga en la consola web`() = runTest {
        val gateway = probadorPorRuta { ruta ->
            if (ruta == "/salud/listo") 200 to """{"servicio":"gateway","estado":"Healthy"}""" else 200 to "<html>consola</html>"
        }
        assertTrue(gateway.probar("http://10.0.2.2:8080") is Resultado.Exito)
        assertEquals(listOf("/salud/listo"), rutasPedidas)
    }

    @Test
    fun `contra el prototipo sin salud listo se usa salud`() = runTest {
        val prototipo = probadorPorRuta { ruta -> if (ruta == "/salud") 200 to """{"estado":"ok"}""" else 404 to "" }
        assertTrue(prototipo.probar("http://10.0.2.2:5080") is Resultado.Exito)
        assertEquals(listOf("/salud/listo", "/salud"), rutasPedidas)
    }

    @Test
    fun `una URL que responde salud es correcta y se prueba sin guardarla`() = runTest {
        assertTrue(probador().probar(" http://192.168.1.10:5080 ") is Resultado.Exito)
        assertEquals("http://192.168.1.10:5080/salud/listo", urlPedida)
    }

    @Test
    fun `sin respuesta explica que revisar`() = runTest {
        val error = probador(fallar = true).probar("http://192.168.1.10:5080") as Resultado.Error
        assertEquals(TipoError.SIN_RED, error.tipo)
        assertTrue(error.mensaje.contains("firewall"))
    }

    @Test
    fun `un servidor que no es el API se distingue`() = runTest {
        val error = probador(cuerpo = "<html>router</html>").probar("http://192.168.1.1") as Resultado.Error
        assertTrue(error.mensaje.contains("no es el API"))
    }

    @Test
    fun `un error HTTP se informa con su codigo`() = runTest {
        val error = probador(codigo = 500, cuerpo = "").probar("http://192.168.1.10:5080") as Resultado.Error
        assertTrue(error.mensaje.contains("500"))
    }

    @Test
    fun `una URL invalida no hace peticion`() = runTest {
        assertTrue(probador().probar("no es url") is Resultado.Error)
        assertEquals(null, urlPedida)
    }
}
