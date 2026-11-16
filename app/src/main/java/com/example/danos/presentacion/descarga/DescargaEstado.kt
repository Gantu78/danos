package com.example.danos.presentacion.descarga

import com.example.danos.dominio.Desastre
import java.time.Instant

data class DescargaEstado(
    val desastres: List<Desastre> = emptyList(),
    val desastreId: String? = null,
    val zona: String? = null,
    val ultimaDescarga: Instant? = null,
    val marcadorDesde: String? = null,
    val numActivos: Int = 0,
    val numTipos: Int = 0,
    val descargando: Boolean = false,
) {
    /** Municipios DIVIPOLA asociados al desastre elegido (DA-19). */
    val municipiosDisponibles: List<String>
        get() = desastres.firstOrNull { it.id == desastreId }?.codigosMunicipio.orEmpty()
}

sealed interface DescargaEfecto {
    data class Mensaje(val texto: String) : DescargaEfecto
}
