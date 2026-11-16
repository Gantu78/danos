package com.example.danos.presentacion.ingreso

import android.content.Intent
import com.example.danos.BuildConfig
import com.example.danos.dominio.EMISOR_OIDC_POR_DEFECTO
import com.example.danos.dominio.SesionIdentidad

data class PruebaConexion(val exitosa: Boolean, val mensaje: String)

data class IngresoEstado(
    val evaluador: String = "",
    val organizacion: String = "",
    val urlApi: String = "",
    val urlEmisor: String = EMISOR_OIDC_POR_DEFECTO,
    val modoEncabezados: Boolean = false,
    val permiteModoEncabezados: Boolean = BuildConfig.DEBUG,
    val sesionActiva: SesionIdentidad? = null,
    val requiereInicioSesion: Boolean = false,
    val errorEvaluador: String? = null,
    val errorOrganizacion: String? = null,
    val errorUrl: String? = null,
    val errorEmisor: String? = null,
    val errorOidc: String? = null,
    val guardando: Boolean = false,
    val probando: Boolean = false,
    val pruebaConexion: PruebaConexion? = null,
)

sealed interface IngresoEfecto {
    data object Guardado : IngresoEfecto
    data class LanzarOidc(val intent: Intent) : IngresoEfecto
}
