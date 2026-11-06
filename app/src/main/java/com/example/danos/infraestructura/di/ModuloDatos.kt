package com.example.danos.infraestructura.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.danos.infraestructura.local.ActivoDao
import com.example.danos.infraestructura.local.AppDatabase
import com.example.danos.infraestructura.local.CatalogoDao
import com.example.danos.infraestructura.local.EvaluacionDao
import com.example.danos.infraestructura.local.EvidenciaDao
import com.example.danos.infraestructura.local.OperacionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloDatos {

    /** Sin migración destructiva: la réplica local nunca puede perder evaluaciones. */
    @Provides
    @Singleton
    fun baseDatos(@ApplicationContext contexto: Context): AppDatabase =
        Room.databaseBuilder(contexto, AppDatabase::class.java, "danos.db").build()

    @Provides fun catalogoDao(baseDatos: AppDatabase): CatalogoDao = baseDatos.catalogoDao()
    @Provides fun activoDao(baseDatos: AppDatabase): ActivoDao = baseDatos.activoDao()
    @Provides fun evaluacionDao(baseDatos: AppDatabase): EvaluacionDao = baseDatos.evaluacionDao()
    @Provides fun evidenciaDao(baseDatos: AppDatabase): EvidenciaDao = baseDatos.evidenciaDao()
    @Provides fun operacionDao(baseDatos: AppDatabase): OperacionDao = baseDatos.operacionDao()

    @Provides
    @Singleton
    fun almacenPreferencias(@ApplicationContext contexto: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { contexto.preferencesDataStoreFile("configuracion") }
}
