package com.example.danos.infraestructura.di

import android.content.Context
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloDispositivo {

    @Provides
    @Singleton
    fun clienteUbicacion(@ApplicationContext contexto: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(contexto)

    @Provides
    @Singleton
    fun workManager(@ApplicationContext contexto: Context): WorkManager = WorkManager.getInstance(contexto)
}
