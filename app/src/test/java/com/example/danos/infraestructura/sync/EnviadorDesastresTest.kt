package com.example.danos.infraestructura.sync

import com.example.danos.IdentidadFalsa
import com.example.danos.dominio.EstadoEnvioDesastre
import com.example.danos.dominio.RegistroDesastre
import com.example.danos.dominio.SeveridadDesastre
import com.example.danos.infraestructura.local.RegistroDesastreDao
import com.example.danos.infraestructura.local.RegistroDesastreEntity
import com.example.danos.infraestructura.local.TipoDesastreEntity
import com.example.danos.infraestructura.local.aEntidad
import com.example.danos.infraestructura.remoto.DesastresApi
import com.example.danos.infraestructura.remoto.JsonDanos
import com.example.danos.infraestructura.remoto.RegistroDesastreDto
import com.example.danos.infraestructura.remoto.TipoDesastreDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class EnviadorDesastresTest {

    private class DaoFalso : RegistroDesastreDao {
        val filas = MutableStateFlow<Map<String, RegistroDesastreEntity>>(emptyMap())
        val tipos = MutableStateFlow<List<TipoDesastreEntity>>(emptyList())
        override fun observarTodos(): Flow<List<RegistroDesastreEntity>> = filas.map { it.values.toList() }
        override fun observarPendientes(): Flow<Int> = filas.map { m -> m.values.count { it.estadoEnvio == "PENDIENTE" } }
        override suspend fun pendientes() = filas.value.values.filter { it.estadoEnvio == "PENDIENTE" }
        override suspend fun insertar(registro: RegistroDesastreEntity) {
            filas.value = filas.value + (registro.id to registro)
        }
        override suspend fun incrementarIntentos(id: String) {
            filas.value[id]?.let { filas.value = filas.value + (id to it.copy(intentos = it.intentos + 1)) }
        }
        override suspend fun actualizarEstado(id: String, estado: String, motivo: String?) {
            filas.value[id]?.let { filas.value = filas.value + (id to it.copy(estadoEnvio = estado, motivoRechazo = motivo)) }
        }
        override fun observarTipos(): Flow<List<TipoDesastreEntity>> = tipos
        override suspend fun borrarTipos() { tipos.value = emptyList() }
        override suspend fun insertarTipos(tipos: List<TipoDesastreEntity>) { this.tipos.value = this.tipos.value + tipos }
    }

    private class ApiFalsaDesastres : DesastresApi {
        var respuesta: () -> Response<ResponseBody> = { Response.success("{}".toResponseBody()) }
        val enviados = mutableListOf<RegistroDesastreDto>()
        var catalogo: List<TipoDesastreDto> = emptyList()
        override suspend fun tiposDesastre() = catalogo
        override suspend fun registrar(registro: RegistroDesastreDto): Response<ResponseBody> {
            enviados += registro
            return respuesta()
        }
    }

    private val dao = DaoFalso()
    private val api = ApiFalsaDesastres()
    private val identidad = IdentidadFalsa()
    private val enviador = EnviadorDesastres(dao, api, identidad)

    private fun registro(id: String = "11111111-1111-1111-1111-111111111111") = RegistroDesastre(
        id = id, tipo = "INUNDACION", nombre = "Creciente", descripcion = "Ribera inundada",
        instanteInicio = Instant.parse("2026-11-18T13:00:00Z"), lat = 4.53, lon = -75.68, precisionM = 8.0,
        severidad = SeveridadDesastre.NIVEL_2, responsable = "bomberos", radioZonaM = 500, codigosMunicipio = listOf("63001"),
        autor = "bomberos.operador", organizacion = "bomberos", instanteRegistro = Instant.parse("2026-11-18T14:00:00Z"),
    )

    private fun error(codigo: Int, cuerpo: String) = Response.error<ResponseBody>(codigo, cuerpo.toResponseBody())

    @Test
    fun unRegistroAceptadoQuedaEnviadoYViajaConSuIdYSuPoligono() = runTest {
        dao.insertar(registro().aEntidad())

        assertTrue(enviador.enviarPendientes())

        val fila = dao.filas.value.values.single()
        assertEquals(EstadoEnvioDesastre.ENVIADO.name, fila.estadoEnvio)
        val dto = api.enviados.single()
        assertEquals(registro().id, dto.id)
        assertEquals("EVALUACION_CAMPO", dto.zona.fuente)
        assertEquals(17, dto.zona.geometria!!.coordinates.single().size)
        assertTrue(JsonDanos.encodeToString(RegistroDesastreDto.serializer(), dto).contains("\"type\":\"Polygon\""))
    }

    @Test
    fun unRechazoDeValidacionGuardaElMotivoDelServidorYNoSeReintenta() = runTest {
        dao.insertar(registro().aEntidad())
        api.respuesta = { error(422, """{"codigo":"TIPO_DESCONOCIDO","mensaje":"El tipo X no está en el catálogo"}""") }

        assertTrue(enviador.enviarPendientes())
        assertTrue(enviador.enviarPendientes())

        val fila = dao.filas.value.values.single()
        assertEquals(EstadoEnvioDesastre.RECHAZADO.name, fila.estadoEnvio)
        assertEquals("El tipo X no está en el catálogo", fila.motivoRechazo)
        assertEquals(1, api.enviados.size)
    }

    @Test
    fun sinElRolOrganismoElRegistroSeRechazaConUnMotivoClaro() = runTest {
        dao.insertar(registro().aEntidad())
        api.respuesta = { error(403, """{"codigo":"PROHIBIDO","mensaje":"x"}""") }

        enviador.enviarPendientes()

        assertTrue(dao.filas.value.values.single().motivoRechazo!!.contains("organismo o autoridad"))
    }

    @Test
    fun sinRedOCon5xxElRegistroSigueEnLaColaParaReintentar() = runTest {
        dao.insertar(registro().aEntidad())
        api.respuesta = { throw IOException("sin red") }
        assertFalse(enviador.enviarPendientes())
        api.respuesta = { error(503, "{}") }
        assertFalse(enviador.enviarPendientes())

        val fila = dao.filas.value.values.single()
        assertEquals(EstadoEnvioDesastre.PENDIENTE.name, fila.estadoEnvio)
        assertNull(fila.motivoRechazo)
        assertEquals(2, fila.intentos)
    }

    @Test
    fun un401MarcaReautenticacionSinPerderElRegistro() = runTest {
        dao.insertar(registro().aEntidad())
        api.respuesta = { error(401, "{}") }

        assertFalse(enviador.enviarPendientes())

        assertTrue(identidad.requiereInicioSesion.first())
        assertEquals(EstadoEnvioDesastre.PENDIENTE.name, dao.filas.value.values.single().estadoEnvio)
    }

    @Test
    fun elCatalogoDescargadoReemplazaAlLocalSoloConTiposVigentes() = runTest {
        api.catalogo = listOf(TipoDesastreDto("TSUNAMI", "Tsunami"), TipoDesastreDto("VIEJO", "Viejo", vigente = false))

        assertTrue(enviador.actualizarTipos())

        assertEquals(listOf("TSUNAMI"), dao.tipos.value.map { it.codigo })
    }
}
