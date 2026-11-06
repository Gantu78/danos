package com.example.danos.infraestructura.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface ProgramadorSincronizacion {
    fun programar()
}

class ProgramadorWorkManager @Inject constructor(
    private val workManager: WorkManager,
) : ProgramadorSincronizacion {

    override fun programar() {
        val solicitud = OneTimeWorkRequestBuilder<SincronizacionWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOMBRE_TRABAJO, ExistingWorkPolicy.APPEND_OR_REPLACE, solicitud)
    }

    private companion object {
        const val NOMBRE_TRABAJO = "sincronizacion"
    }
}
