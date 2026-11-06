package com.example.danos.presentacion.ingreso

data class IngresoEstado(
    val evaluador: String = "",
    val organizacion: String = "",
    val urlApi: String = "",
    val errorEvaluador: String? = null,
    val errorOrganizacion: String? = null,
    val errorUrl: String? = null,
    val guardando: Boolean = false,
)

sealed interface IngresoEfecto {
    data object Guardado : IngresoEfecto
}
