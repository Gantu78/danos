package com.example.danos.acceso

import com.example.danos.BuildConfig
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.Resultado
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.ProbadorConexion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ConfiguracionRepositorio {
    val urlPorDefecto: String
    val configuracion: Flow<Configuracion?>
    suspend fun actual(): Configuracion?
    suspend fun guardar(evaluador: String, organizacion: String, urlApi: String)

    /** Prueba una URL todavía sin guardar contra el endpoint /salud del API. */
    suspend fun probarConexion(urlApi: String): Resultado<Unit>
}

class ConfiguracionRepositorioImpl @Inject constructor(
    private val preferencias: PreferenciasLocales,
    private val probador: ProbadorConexion,
) : ConfiguracionRepositorio {
    override val urlPorDefecto: String = BuildConfig.API_URL_DEFECTO
    override val configuracion: Flow<Configuracion?> = preferencias.configuracion
    override suspend fun actual(): Configuracion? = preferencias.actual()
    override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) =
        preferencias.guardar(evaluador, organizacion, urlApi)
    override suspend fun probarConexion(urlApi: String): Resultado<Unit> = probador.probar(urlApi)
}
