package com.example.danos.infraestructura.sync

import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoError
import com.example.danos.infraestructura.local.ActivoDao
import com.example.danos.infraestructura.local.EjecutorTransaccion
import com.example.danos.infraestructura.local.EvaluacionDao
import com.example.danos.infraestructura.local.EvidenciaDao
import com.example.danos.infraestructura.local.EvidenciaEntity
import com.example.danos.infraestructura.local.OperacionDao
import com.example.danos.infraestructura.local.OperacionEntity
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.DanosApi
import com.example.danos.infraestructura.remoto.JsonDanos
import com.example.danos.infraestructura.remoto.LoteSincronizacionDto
import com.example.danos.infraestructura.remoto.OperacionDto
import com.example.danos.infraestructura.remoto.ResultadoOperacionDto
import com.example.danos.infraestructura.remoto.errorHttp
import com.example.danos.infraestructura.remoto.llamarApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class DesenlaceCiclo { TERMINADO, REINTENTAR }

sealed interface ResultadoLote {
    /** No quedan operaciones por enviar. */
    data object ColaVacia : ResultadoLote

    /** El servidor respondió por al menos una operación: puede seguir con el siguiente lote. */
    data object Avance : ResultadoLote

    /** El servidor respondió sin decir nada de estas operaciones: se quedan en la cola. */
    data object SinAvance : ResultadoLote

    /** Sin red o 5xx: el servidor es idempotente por idOperacion, se reenvía después. */
    data object Reintentar : ResultadoLote

    /** 4xx: las operaciones se quedan en la cola con su error y no se insiste en bucle. */
    data object Detenido : ResultadoLote
}

/** Envía la cola y las evidencias. Aplica los invariantes 1 a 4 del caso de uso FP-06. */
class MotorSincronizacion @Inject constructor(
    private val transaccion: EjecutorTransaccion,
    private val operacionDao: OperacionDao,
    private val evaluacionDao: EvaluacionDao,
    private val evidenciaDao: EvidenciaDao,
    private val activoDao: ActivoDao,
    private val api: DanosApi,
    private val preferencias: PreferenciasLocales,
) {

    suspend fun ejecutarCiclo(): DesenlaceCiclo {
        var reintentar = false
        enviar@ while (true) {
            when (enviarSiguienteLote()) {
                ResultadoLote.Avance -> Unit
                ResultadoLote.ColaVacia, ResultadoLote.Detenido -> break@enviar
                ResultadoLote.SinAvance -> {
                    reintentar = true
                    break@enviar
                }
                ResultadoLote.Reintentar -> return DesenlaceCiclo.REINTENTAR
            }
        }
        if (!subirEvidenciasPendientes()) reintentar = true
        return if (reintentar) DesenlaceCiclo.REINTENTAR else DesenlaceCiclo.TERMINADO
    }

    suspend fun enviarSiguienteLote(): ResultadoLote {
        val configuracion = preferencias.actual() ?: return ResultadoLote.Detenido
        val lote = tomarLote()
        if (lote.isEmpty()) return ResultadoLote.ColaVacia

        val cuerpo = LoteSincronizacionDto(
            idDispositivo = configuracion.idDispositivo,
            instanteEnvioDispositivo = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
            operaciones = lote.map { operacion ->
                OperacionDto(
                    idOperacion = operacion.idOperacion,
                    tipo = operacion.tipo.name,
                    versionBaseActivo = operacion.versionBaseActivo,
                    evaluacion = JsonDanos.parseToJsonElement(operacion.payloadJson),
                )
            },
        )

        return when (val respuesta = llamarApi { api.sincronizar(cuerpo) }) {
            is Resultado.Error -> {
                if (respuesta.tipo == TipoError.CLIENTE) {
                    operacionDao.guardarError(lote.map { it.idOperacion }, respuesta.mensaje)
                    ResultadoLote.Detenido
                } else {
                    ResultadoLote.Reintentar
                }
            }
            is Resultado.Exito -> {
                val idsDelLote = lote.mapTo(HashSet()) { it.idOperacion }
                val aplicadas = respuesta.valor.resultados
                    .filter { it.idOperacion in idsDelLote }
                    .count { aplicarResultado(it) }
                if (aplicadas > 0) ResultadoLote.Avance else ResultadoLote.SinAvance
            }
        }
    }

    /**
     * Tomar e incrementar en una sola transacción: desde ese momento la edición ya no puede
     * reemplazar el payload que está a punto de salir.
     */
    private suspend fun tomarLote(): List<OperacionEntity> = transaccion.enTransaccion {
        val lote = operacionDao.primeras(TAMANO_LOTE)
        if (lote.isNotEmpty()) operacionDao.incrementarIntentos(lote.map { it.idOperacion })
        lote
    }

    /** Devuelve true si la operación salió de la cola. Un resultado desconocido la deja en ella. */
    suspend fun aplicarResultado(resultado: ResultadoOperacionDto): Boolean =
        transaccion.enTransaccion {
            val operacion = operacionDao.obtener(resultado.idOperacion) ?: return@enTransaccion false
            val evaluacion = evaluacionDao.obtener(operacion.evaluacionId)
            val tipoResultado = resultado.resultado
            if (tipoResultado !in RESULTADOS_CONOCIDOS) return@enTransaccion false

            operacionDao.borrar(operacion.idOperacion)
            if (evaluacion == null) return@enTransaccion true

            // Si la evaluación se editó mientras tanto, sigue pendiente de su operación más nueva.
            val quedanOperaciones = operacionDao.deEvaluacion(evaluacion.id).isNotEmpty()
            val syncAceptada = if (quedanOperaciones) EstadoSync.PENDIENTE else EstadoSync.CONFIRMADA

            when (tipoResultado) {
                APLICADA -> {
                    val estadoServidor = EstadoEvaluacion.entries
                        .firstOrNull { it.name == resultado.estadoEvaluacion }
                    evaluacionDao.actualizarEstados(
                        id = evaluacion.id,
                        estadoSync = syncAceptada,
                        estadoEvaluacion = estadoServidor ?: evaluacion.estadoEvaluacion,
                        motivo = null,
                        conocida = true,
                    )
                    resultado.nuevaVersionActivo?.let { activoDao.actualizarVersion(operacion.activoId, it) }
                }
                EN_CONFLICTO, POSIBLE_DUPLICADO -> evaluacionDao.actualizarEstados(
                    id = evaluacion.id,
                    estadoSync = syncAceptada,
                    estadoEvaluacion = EstadoEvaluacion.EN_CONFLICTO,
                    motivo = null,
                    conocida = true,
                )
                INVALIDA -> evaluacionDao.actualizarEstados(
                    id = evaluacion.id,
                    estadoSync = if (quedanOperaciones) EstadoSync.PENDIENTE else EstadoSync.INVALIDA,
                    estadoEvaluacion = evaluacion.estadoEvaluacion,
                    motivo = resultado.motivo ?: "El servidor no indicó el motivo",
                    conocida = evaluacion.conocidaPorServidor,
                )
            }
            true
        }

    /** Devuelve false si conviene reintentar (sin red o 5xx). Un 4xx no se reintenta en bucle. */
    suspend fun subirEvidenciasPendientes(): Boolean {
        var completo = true
        for (evidencia in evidenciaDao.pendientesDeSubir()) {
            val archivo = File(evidencia.rutaArchivo)
            if (!archivo.isFile) continue
            when (subir(evidencia, archivo)) {
                DesenlaceSubida.SUBIDA -> evidenciaDao.marcarSubida(evidencia.id)
                DesenlaceSubida.RECHAZADA -> Unit
                DesenlaceSubida.ERROR_SERVIDOR -> completo = false
                DesenlaceSubida.SIN_RED -> return false
            }
        }
        return completo
    }

    private suspend fun subir(evidencia: EvidenciaEntity, archivo: File): DesenlaceSubida {
        val parte = MultipartBody.Part.createFormData(
            "archivo",
            archivo.name,
            archivo.asRequestBody(TIPO_JPEG),
        )
        val respuesta = llamarApi { api.subirEvidencia(evidencia.id, parte) }
        return when (respuesta) {
            is Resultado.Error ->
                if (respuesta.tipo == TipoError.SIN_RED) DesenlaceSubida.SIN_RED else DesenlaceSubida.ERROR_SERVIDOR
            is Resultado.Exito -> when {
                respuesta.valor.isSuccessful -> DesenlaceSubida.SUBIDA
                errorHttp(respuesta.valor.code()).tipo == TipoError.CLIENTE -> DesenlaceSubida.RECHAZADA
                else -> DesenlaceSubida.ERROR_SERVIDOR
            }
        }
    }

    private enum class DesenlaceSubida { SUBIDA, RECHAZADA, ERROR_SERVIDOR, SIN_RED }

    private companion object {
        const val TAMANO_LOTE = 20
        const val APLICADA = "APLICADA"
        const val EN_CONFLICTO = "EN_CONFLICTO"
        const val POSIBLE_DUPLICADO = "POSIBLE_DUPLICADO"
        const val INVALIDA = "INVALIDA"
        val RESULTADOS_CONOCIDOS = setOf(APLICADA, EN_CONFLICTO, POSIBLE_DUPLICADO, INVALIDA)
        val TIPO_JPEG = "image/jpeg".toMediaType()
    }
}
