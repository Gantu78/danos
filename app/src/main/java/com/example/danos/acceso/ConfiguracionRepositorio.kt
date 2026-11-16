package com.example.danos.acceso

import com.example.danos.BuildConfig
import com.example.danos.dominio.Configuracion
import com.example.danos.dominio.EMISOR_OIDC_POR_DEFECTO
import com.example.danos.dominio.Resultado
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.ProbadorConexion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ConfiguracionRepositorio {
    val urlPorDefecto: String
    val emisorPorDefecto: String get() = EMISOR_OIDC_POR_DEFECTO
    val permiteModoEncabezados: Boolean get() = BuildConfig.DEBUG
    val configuracion: Flow<Configuracion?>
    suspend fun actual(): Configuracion?
    suspend fun guardar(evaluador: String, organizacion: String, urlApi: String)
    suspend fun guardarConfiguracion(
        evaluador: String,
        organizacion: String,
        urlApi: String,
        urlEmisor: String = EMISOR_OIDC_POR_DEFECTO,
        modoEncabezados: Boolean = false,
    ) = guardar(evaluador, organizacion, urlApi)

    /** Prueba una URL todavía sin guardar contra el endpoint de salud del API/gateway. */
    suspend fun probarConexion(urlApi: String): Resultado<Unit>
}

class ConfiguracionRepositorioImpl @Inject constructor(
    private val preferencias: PreferenciasLocales,
    private val probador: ProbadorConexion,
) : ConfiguracionRepositorio {
    override val urlPorDefecto: String = BuildConfig.API_URL_DEFECTO
    override val emisorPorDefecto: String = BuildConfig.EMISOR_OIDC_DEFECTO
    override val permiteModoEncabezados: Boolean = BuildConfig.DEBUG
    override val configuracion: Flow<Configuracion?> = preferencias.configuracion
    override suspend fun actual(): Configuracion? = preferencias.actual()

    override suspend fun guardar(evaluador: String, organizacion: String, urlApi: String) =
        preferencias.guardar(evaluador, organizacion, urlApi)

    override suspend fun guardarConfiguracion(
        evaluador: String,
        organizacion: String,
        urlApi: String,
        urlEmisor: String,
        modoEncabezados: Boolean,
    ) = preferencias.guardarConfiguracionCompleta(
        evaluador = evaluador,
        organizacion = organizacion,
        urlApi = urlApi,
        urlEmisor = urlEmisor,
        modoEncabezados = BuildConfig.DEBUG && modoEncabezados,
    )

    override suspend fun probarConexion(urlApi: String): Resultado<Unit> = probador.probar(urlApi)
}
