package com.example.danos.presentacion.ingreso

data class PruebaConexion(val exitosa: Boolean, val mensaje: String)

data class IngresoEstado(
    val evaluador: String = "",
    val organizacion: String = "",
    val urlApi: String = "",
    val errorEvaluador: String? = null,
    val errorOrganizacion: String? = null,
    val errorUrl: String? = null,
    val guardando: Boolean = false,
    val probando: Boolean = false,
    val pruebaConexion: PruebaConexion? = null,
)

sealed interface IngresoEfecto {
    data object Guardado : IngresoEfecto
}
