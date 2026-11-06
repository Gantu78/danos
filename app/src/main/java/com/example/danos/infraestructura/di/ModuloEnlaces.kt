package com.example.danos.infraestructura.di

import com.example.danos.acceso.CatalogoRepositorio
import com.example.danos.acceso.CatalogoRepositorioImpl
import com.example.danos.acceso.ConfiguracionRepositorio
import com.example.danos.acceso.ConfiguracionRepositorioImpl
import com.example.danos.acceso.DispositivoRepositorio
import com.example.danos.acceso.DispositivoRepositorioImpl
import com.example.danos.acceso.EvaluacionRepositorio
import com.example.danos.acceso.EvaluacionRepositorioImpl
import com.example.danos.acceso.SincronizacionRepositorio
import com.example.danos.acceso.SincronizacionRepositorioImpl
import com.example.danos.infraestructura.local.EjecutorTransaccion
import com.example.danos.infraestructura.local.EjecutorTransaccionRoom
import com.example.danos.infraestructura.preferencias.PreferenciasDataStore
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import com.example.danos.infraestructura.sync.ProgramadorWorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloEnlaces {
    @Binds @Singleton abstract fun preferencias(impl: PreferenciasDataStore): PreferenciasLocales
    @Binds abstract fun transaccion(impl: EjecutorTransaccionRoom): EjecutorTransaccion
    @Binds abstract fun programador(impl: ProgramadorWorkManager): ProgramadorSincronizacion
    @Binds abstract fun configuracion(impl: ConfiguracionRepositorioImpl): ConfiguracionRepositorio
    @Binds abstract fun catalogo(impl: CatalogoRepositorioImpl): CatalogoRepositorio
    @Binds abstract fun evaluacion(impl: EvaluacionRepositorioImpl): EvaluacionRepositorio
    @Binds abstract fun sincronizacion(impl: SincronizacionRepositorioImpl): SincronizacionRepositorio
    @Binds abstract fun dispositivo(impl: DispositivoRepositorioImpl): DispositivoRepositorio
}
