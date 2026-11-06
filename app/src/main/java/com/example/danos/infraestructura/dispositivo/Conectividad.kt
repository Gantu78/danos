package com.example.danos.infraestructura.dispositivo

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

class Conectividad @Inject constructor(
    @ApplicationContext private val contexto: Context,
) {
    val conectado: Flow<Boolean> = callbackFlow {
        val gestor = contexto.getSystemService(ConnectivityManager::class.java)
        val retorno = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(red: Network, capacidades: NetworkCapabilities) {
                trySend(capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(red: Network) {
                trySend(false)
            }
        }
        val capacidades = gestor.getNetworkCapabilities(gestor.activeNetwork)
        trySend(capacidades?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        gestor.registerDefaultNetworkCallback(retorno)
        awaitClose { gestor.unregisterNetworkCallback(retorno) }
    }.distinctUntilChanged()
}
