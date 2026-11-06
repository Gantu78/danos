package com.example.danos.infraestructura.dispositivo

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.danos.dominio.ResultadoUbicacion
import com.example.danos.dominio.Ubicacion
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class ServicioUbicacion @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val cliente: FusedLocationProviderClient,
) {

    @SuppressLint("MissingPermission")
    suspend fun obtener(): ResultadoUbicacion {
        val conPermiso = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED }
        if (!conPermiso) return ResultadoUbicacion.SinPermiso

        val ubicacion = pedirActual() ?: pedirUltima()
        return ubicacion
            ?.let { ResultadoUbicacion.Exito(Ubicacion(it.latitude, it.longitude, it.accuracy.toDouble())) }
            ?: ResultadoUbicacion.NoDisponible
    }

    @SuppressLint("MissingPermission")
    private suspend fun pedirActual(): Location? = protegido {
        val cancelacion = CancellationTokenSource()
        try {
            withTimeoutOrNull(TIEMPO_MAXIMO_MS) {
                cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacion.token).await()
            }
        } finally {
            cancelacion.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun pedirUltima(): Location? = protegido { cliente.lastLocation.await() }

    /** Sin servicios de ubicación la Task falla: se trata igual que "no disponible". */
    private suspend fun protegido(bloque: suspend () -> Location?): Location? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val TIEMPO_MAXIMO_MS = 30_000L
    }
}
