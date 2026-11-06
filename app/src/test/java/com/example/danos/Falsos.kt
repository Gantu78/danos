package com.example.danos

import com.example.danos.dominio.Activo
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.NivelDano
import com.example.danos.infraestructura.local.ActivoDao
import com.example.danos.infraestructura.local.ActivoEntity
import com.example.danos.infraestructura.local.EjecutorTransaccion
import com.example.danos.infraestructura.local.EvaluacionDao
import com.example.danos.infraestructura.local.EvaluacionEntity
import com.example.danos.infraestructura.local.EvidenciaDao
import com.example.danos.infraestructura.local.EvidenciaEntity
import com.example.danos.infraestructura.local.FilaEvaluacion
import com.example.danos.infraestructura.local.OperacionDao
import com.example.danos.infraestructura.local.OperacionEntity
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.DanosApi
import com.example.danos.infraestructura.remoto.DescargaDto
import com.example.danos.infraestructura.remoto.LoteSincronizacionDto
import com.example.danos.infraestructura.remoto.RespuestaSincronizacionDto
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MultipartBody
import retrofit2.Response
import java.time.Instant

class TransaccionDirecta : EjecutorTransaccion {
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T = bloque()
}

class ProgramadorFalso : ProgramadorSincronizacion {
    var veces = 0
    override fun programar() {
        veces++
    }
}

class PreferenciasFalsas(
    var config: Configuracion? = Configuracion("Ana Núñez", "Defensa Civil", "http://10.0.2.2:5080", "disp-1"),
) : PreferenciasLocales {
    override val configuracion: Flow<Configuracion?> get() = flowOf(config)
    override val ultimaDescarga = MutableStateFlow<Instant?>(null)
    override suspend fun actual(): Configuracion? = config
    override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) {
        config = Configuracion(evaluador, organizacion, urlApi, config?.idDispositivo ?: "disp-1")
    }
    override suspend fun guardarUltimaDescarga(instante: Instant) {
        ultimaDescarga.value = instante
    }
}

class OperacionDaoFalso : OperacionDao {
    val filas = mutableListOf<OperacionEntity>()

    override fun observarConteo(): Flow<Int> = flowOf(filas.size)
    override fun observarUltimoError(): Flow<String?> = flowOf(filas.firstNotNullOfOrNull { it.ultimoError })
    override suspend fun contar(): Int = filas.size
    override suspend fun primeras(limite: Int): List<OperacionEntity> = filas.sortedBy { it.creada }.take(limite)
    override suspend fun deEvaluacion(evaluacionId: String) = filas.filter { it.evaluacionId == evaluacionId }
    override suspend fun obtener(id: String) = filas.firstOrNull { it.idOperacion == id }
    override suspend fun insertar(operacion: OperacionEntity) {
        filas += operacion
    }
    override suspend fun reemplazarPayloadSiNoIntentada(id: String, payload: String): Int {
        val indice = filas.indexOfFirst { it.idOperacion == id && it.intentos == 0 }
        if (indice < 0) return 0
        filas[indice] = filas[indice].copy(payloadJson = payload)
        return 1
    }
    override suspend fun incrementarIntentos(ids: List<String>) {
        filas.replaceAll { if (it.idOperacion in ids) it.copy(intentos = it.intentos + 1) else it }
    }
    override suspend fun guardarError(ids: List<String>, error: String) {
        filas.replaceAll { if (it.idOperacion in ids) it.copy(ultimoError = error) else it }
    }
    override suspend fun borrar(id: String) {
        filas.removeAll { it.idOperacion == id }
    }
}

class EvaluacionDaoFalso : EvaluacionDao {
    val filas = linkedMapOf<String, EvaluacionEntity>()

    override fun observarLista(): Flow<List<FilaEvaluacion>> = flowOf(emptyList())
    override suspend fun obtenerFila(id: String): FilaEvaluacion? = null
    override suspend fun obtener(id: String) = filas[id]
    override suspend fun insertar(evaluacion: EvaluacionEntity) {
        check(evaluacion.id !in filas)
        filas[evaluacion.id] = evaluacion
    }
    override suspend fun guardar(evaluacion: EvaluacionEntity) {
        filas[evaluacion.id] = evaluacion
    }
    override suspend fun actualizarEstados(
        id: String,
        estadoSync: EstadoSync,
        estadoEvaluacion: EstadoEvaluacion,
        motivo: String?,
        conocida: Boolean,
    ) {
        filas[id] = filas.getValue(id).copy(
            estadoSync = estadoSync,
            estadoEvaluacion = estadoEvaluacion,
            motivoInvalida = motivo,
            conocidaPorServidor = conocida,
        )
    }
}

class EvidenciaDaoFalso(private val evaluaciones: EvaluacionDaoFalso) : EvidenciaDao {
    val filas = linkedMapOf<String, EvidenciaEntity>()

    override suspend fun guardarTodas(evidencias: List<EvidenciaEntity>) {
        evidencias.forEach { filas[it.id] = it }
    }
    override suspend fun deEvaluacion(evaluacionId: String) = filas.values.filter { it.evaluacionId == evaluacionId }
    override suspend fun pendientesDeSubir() = filas.values.filter {
        !it.subida && evaluaciones.filas[it.evaluacionId]?.estadoSync == EstadoSync.CONFIRMADA
    }
    override suspend fun marcarSubida(id: String) {
        filas[id] = filas.getValue(id).copy(subida = true)
    }
}

class ActivoDaoFalso : ActivoDao {
    val filas = linkedMapOf<String, ActivoEntity>()
    private val cambios = MutableStateFlow(0)

    override fun observarTodos(): Flow<List<ActivoEntity>> = cambios.map { filas.values.toList() }
    override suspend fun obtener(id: String) = filas[id]
    override suspend fun guardar(activo: ActivoEntity) {
        filas[activo.id] = activo
    }
    override suspend fun guardarTodos(activos: List<ActivoEntity>) {
        activos.forEach { filas[it.id] = it }
    }
    override suspend fun idsProtegidos(): List<String> = filas.values.filter { it.versionDescargada == null }.map { it.id }
    override suspend fun borrarDescargadosReemplazables() = Unit
    override suspend fun actualizarVersion(id: String, version: Int) {
        filas[id] = filas.getValue(id).copy(versionDescargada = version)
    }
}

class ApiFalsa(
    var alSincronizar: suspend (LoteSincronizacionDto) -> RespuestaSincronizacionDto = {
        RespuestaSincronizacionDto()
    },
    var alSubir: suspend (String) -> Response<Unit> = { Response.success(Unit) },
) : DanosApi {
    val lotes = mutableListOf<LoteSincronizacionDto>()
    val subidas = mutableListOf<String>()

    override suspend fun descargar(desastreId: String?): DescargaDto = DescargaDto("2026-01-01T00:00:00Z")

    override suspend fun sincronizar(lote: LoteSincronizacionDto): RespuestaSincronizacionDto {
        lotes += lote
        return alSincronizar(lote)
    }

    override suspend fun subirEvidencia(id: String, archivo: MultipartBody.Part): Response<Unit> {
        subidas += id
        return alSubir(id)
    }
}

val INSTANTE_FIJO: Instant = Instant.parse("2026-03-01T10:15:30.123Z")

fun evaluacionDePrueba(
    id: String = "eval-1",
    activoId: String = "activo-1",
    desastreId: String? = "desastre-1",
    descripcion: String = "Muro agrietado",
    evidencias: List<Evidencia> = emptyList(),
) = Evaluacion(
    id = id,
    activoId = activoId,
    desastreId = desastreId,
    instanteCampo = INSTANTE_FIJO,
    evaluador = "Ana Núñez",
    organizacion = "Defensa Civil",
    lat = 4.6,
    lon = -74.1,
    precisionM = 8.5,
    tipoActivoCodigo = "VIV",
    nivelDano = NivelDano.MODERADO,
    descripcion = descripcion,
    evidencias = evidencias,
)

fun evidenciaDePrueba(id: String = "foto-1", evaluacionId: String = "eval-1", ruta: String = "/no/existe.jpg") = Evidencia(
    id = id,
    evaluacionId = evaluacionId,
    rutaArchivo = ruta,
    lat = 4.6,
    lon = -74.1,
    instanteCaptura = INSTANTE_FIJO,
    sha256 = "abc123",
    tamanoBytes = 1024,
)

fun activoDePrueba(id: String = "activo-1", version: Int? = 3) = Activo(id, "VIV", 4.6, -74.1, "CL 10 # 5-20", version)
