package com.example.danos.presentacion.lista

import com.example.danos.dominio.ResumenEvaluacion

data class ListaEstado(
    val evaluaciones: List<ResumenEvaluacion> = emptyList(),
    val conectado: Boolean = false,
    val operacionesPendientes: Int = 0,
    val errorSincronizacion: String? = null,
)

sealed interface ListaEfecto {
    data object SincronizacionSolicitada : ListaEfecto
}
