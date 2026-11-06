package com.example.danos.presentacion.descarga

import com.example.danos.dominio.Desastre
import java.time.Instant

data class DescargaEstado(
    val desastres: List<Desastre> = emptyList(),
    val desastreId: String? = null,
    val ultimaDescarga: Instant? = null,
    val numActivos: Int = 0,
    val numTipos: Int = 0,
    val descargando: Boolean = false,
)

sealed interface DescargaEfecto {
    data class Mensaje(val texto: String) : DescargaEfecto
}
