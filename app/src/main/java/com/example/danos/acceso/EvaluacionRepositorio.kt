package com.example.danos.acceso

import com.example.danos.dominio.Activo
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResumenEvaluacion
import com.example.danos.dominio.TipoError
import com.example.danos.dominio.TipoOperacion
import com.example.danos.dominio.estadoInicial
import com.example.danos.infraestructura.local.ActivoDao
import com.example.danos.infraestructura.local.EjecutorTransaccion
import com.example.danos.infraestructura.local.EvaluacionDao
import com.example.danos.infraestructura.local.EvaluacionEntity
import com.example.danos.infraestructura.local.EvidenciaDao
import com.example.danos.infraestructura.local.OperacionDao
import com.example.danos.infraestructura.local.OperacionEntity
import com.example.danos.infraestructura.local.aDominio
import com.example.danos.infraestructura.local.aEntidad
import com.example.danos.infraestructura.local.aResumen
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.aPayloadJson
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

interface EvaluacionRepositorio {
    fun observarResumenes(): Flow<List<ResumenEvaluacion>>
    suspend fun obtener(id: String): Evaluacion?
    suspend fun guardarNueva(evaluacion: Evaluacion, activoNuevo: Activo?): Resultado<Unit>
    suspend fun guardarEdicion(evaluacion: Evaluacion): Resultado<Unit>
}

class EvaluacionRepositorioImpl @Inject constructor(
    private val transaccion: EjecutorTransaccion,
    private val evaluacionDao: EvaluacionDao,
    private val evidenciaDao: EvidenciaDao,
    private val operacionDao: OperacionDao,
    private val activoDao: ActivoDao,
    private val preferencias: PreferenciasLocales,
    private val programador: ProgramadorSincronizacion,
) : EvaluacionRepositorio {

    override fun observarResumenes(): Flow<List<ResumenEvaluacion>> =
        evaluacionDao.observarLista().map { filas -> filas.map { it.aResumen() } }

    override suspend fun obtener(id: String): Evaluacion? =
        evaluacionDao.obtenerFila(id)?.let { it.evaluacion.aDominio(it.evidencias) }

    override suspend fun guardarNueva(evaluacion: Evaluacion, activoNuevo: Activo?): Resultado<Unit> =
        protegido {
            transaccion.enTransaccion {
                activoNuevo?.let { activoDao.guardar(it.aEntidad()) }
                val activo = activoDao.obtener(evaluacion.activoId)?.aDominio()
                val nueva = evaluacion.copy(
                    estadoEvaluacion = estadoInicial(evaluacion.desastreId),
                    estadoSync = EstadoSync.PENDIENTE,
                    motivoInvalida = null,
                )
                evaluacionDao.insertar(nueva.aEntidad(conocidaPorServidor = false))
                evidenciaDao.guardarTodas(nueva.evidencias.map { it.aEntidad() })
                operacionDao.insertar(
                    operacionNueva(TipoOperacion.CREAR, nueva, activo),
                )
            }
            programador.programar()
            Resultado.Exito(Unit)
        }

    override suspend fun guardarEdicion(evaluacion: Evaluacion): Resultado<Unit> {
        val evaluador = preferencias.actual()?.evaluador
        return protegido {
            val resultado: Resultado<Unit> = transaccion.enTransaccion {
                val actual = evaluacionDao.obtener(evaluacion.id)
                when {
                    actual == null ->
                        Resultado.Error(TipoError.NO_PERMITIDO, "La evaluación ya no existe")
                    actual.evaluador != evaluador ->
                        Resultado.Error(TipoError.NO_PERMITIDO, "Solo puedes editar tus propias evaluaciones")
                    else -> {
                        aplicarEdicion(actual, evaluacion)
                        Resultado.Exito(Unit)
                    }
                }
            }
            if (resultado is Resultado.Exito) programador.programar()
            resultado
        }
    }

    private suspend fun aplicarEdicion(actual: EvaluacionEntity, cambios: Evaluacion) {
        // Las evidencias ya guardadas nunca se pisan: conservan su marca de subida.
        val idsGuardadas = evidenciaDao.deEvaluacion(actual.id).mapTo(HashSet()) { it.id }
        evidenciaDao.guardarTodas(cambios.evidencias.filterNot { it.id in idsGuardadas }.map { it.aEntidad() })
        val evidencias = evidenciaDao.deEvaluacion(actual.id)

        val estado = when (actual.estadoEvaluacion) {
            EstadoEvaluacion.PRELIMINAR, EstadoEvaluacion.REGISTRADA -> estadoInicial(cambios.desastreId)
            else -> actual.estadoEvaluacion
        }
        val editada = actual.copy(
            desastreId = cambios.desastreId,
            lat = cambios.lat,
            lon = cambios.lon,
            precisionM = cambios.precisionM,
            tipoActivoCodigo = cambios.tipoActivoCodigo,
            nivelDano = requireNotNull(cambios.nivelDano) { "Una evaluación validada siempre tiene nivel" },
            descripcion = cambios.descripcion,
            estadoEvaluacion = estado,
            estadoSync = EstadoSync.PENDIENTE,
            motivoInvalida = null,
        )
        evaluacionDao.guardar(editada)

        val snapshot = editada.aDominio(evidencias)
        val activo = activoDao.obtener(actual.activoId)?.aDominio()
        val operaciones = operacionDao.deEvaluacion(actual.id)
        val sinIntentar = operaciones.lastOrNull { it.intentos == 0 }
        if (sinIntentar != null) {
            operacionDao.reemplazarPayloadSiNoIntentada(sinIntentar.idOperacion, snapshot.aPayloadJson(activo))
        } else {
            // Si el servidor nunca la aceptó y ya no hay envíos en curso, no tiene nada que actualizar.
            val tipo = if (!actual.conocidaPorServidor && operaciones.isEmpty()) {
                TipoOperacion.CREAR
            } else {
                TipoOperacion.ACTUALIZAR
            }
            operacionDao.insertar(operacionNueva(tipo, snapshot, activo))
        }
    }

    private fun operacionNueva(tipo: TipoOperacion, evaluacion: Evaluacion, activo: Activo?) = OperacionEntity(
        idOperacion = UUID.randomUUID().toString(),
        tipo = tipo,
        evaluacionId = evaluacion.id,
        activoId = evaluacion.activoId,
        versionBaseActivo = activo?.versionDescargada,
        payloadJson = evaluacion.aPayloadJson(activo),
        creada = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    )

    private fun Evaluacion.aEntidad(conocidaPorServidor: Boolean) = EvaluacionEntity(
        id = id,
        activoId = activoId,
        desastreId = desastreId,
        instanteCampo = instanteCampo,
        evaluador = evaluador,
        organizacion = organizacion,
        lat = lat,
        lon = lon,
        precisionM = precisionM,
        tipoActivoCodigo = tipoActivoCodigo,
        nivelDano = requireNotNull(nivelDano) { "Una evaluación validada siempre tiene nivel" },
        descripcion = descripcion,
        estadoEvaluacion = estadoEvaluacion,
        estadoSync = estadoSync,
        motivoInvalida = motivoInvalida,
        conocidaPorServidor = conocidaPorServidor,
    )

    /** Si la escritura falla, la transacción se revierte y el formulario sigue con sus datos. */
    private suspend fun protegido(bloque: suspend () -> Resultado<Unit>): Resultado<Unit> = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Resultado.Error(TipoError.LOCAL, "No se pudo guardar en el teléfono. Inténtalo de nuevo.")
    }
}
