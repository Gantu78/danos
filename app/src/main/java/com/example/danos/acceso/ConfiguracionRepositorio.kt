package com.example.danos.acceso

import com.example.danos.BuildConfig
import com.example.danos.dominio.Configuracion
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ConfiguracionRepositorio {
    val urlPorDefecto: String
    val configuracion: Flow<Configuracion?>
    suspend fun actual(): Configuracion?
    suspend fun guardar(evaluador: String, organizacion: String, urlApi: String)
}

class ConfiguracionRepositorioImpl @Inject constructor(
    private val preferencias: PreferenciasLocales,
) : ConfiguracionRepositorio {
    override val urlPorDefecto: String = BuildConfig.API_URL_DEFECTO
    override val configuracion: Flow<Configuracion?> = preferencias.configuracion
    override suspend fun actual(): Configuracion? = preferencias.actual()
    override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) =
        preferencias.guardar(evaluador, organizacion, urlApi)
}
