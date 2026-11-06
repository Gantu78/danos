package com.example.danos.presentacion.navegacion

import kotlinx.serialization.Serializable

@Serializable
data object RutaIngreso

@Serializable
data object RutaDescarga

@Serializable
data object RutaLista

/** Sin [evaluacionId] es una evaluación nueva; con él, una edición. */
@Serializable
data class RutaEvaluar(val evaluacionId: String? = null)
