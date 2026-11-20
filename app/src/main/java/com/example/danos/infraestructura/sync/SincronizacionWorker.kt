package com.example.danos.infraestructura.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SincronizacionWorker @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val motor: MotorSincronizacion,
    private val desastres: EnviadorDesastres,
) : CoroutineWorker(contexto, parametros) {

    /** Primero los desastres registrados en campo y el catálogo de tipos; después la cola de evaluaciones (AD-44). */
    override suspend fun doWork(): Result {
        val desastresResueltos = desastres.enviarPendientes()
        desastres.actualizarTipos()
        val evaluaciones = motor.ejecutarCiclo()
        return if (desastresResueltos && evaluaciones == DesenlaceCiclo.TERMINADO) Result.success() else Result.retry()
    }
}
