package com.example.danos.infraestructura.remoto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Los campos opcionales del contrato se omiten cuando son null. */
val JsonDanos: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class DescargaDto(
    val instanteServidor: String,
    val tiposActivo: List<TipoActivoDto> = emptyList(),
    val desastres: List<DesastreDto> = emptyList(),
    val activos: List<ActivoDto> = emptyList(),
)

@Serializable
data class TipoActivoDto(
    val codigo: String,
    val nombre: String,
    val criterios: List<CriterioDto> = emptyList(),
)

@Serializable
data class CriterioDto(val nivel: String, val criterio: String)

@Serializable
data class DesastreDto(val id: String, val nombre: String, val tipo: String)

@Serializable
data class ActivoDto(
    val id: String,
    val tipoCodigo: String,
    val lat: Double,
    val lon: Double,
    val referencia: String,
    val version: Int,
)

@Serializable
data class LoteSincronizacionDto(
    val idDispositivo: String,
    val instanteEnvioDispositivo: String,
    val operaciones: List<OperacionDto>,
)

/** [evaluacion] es el payloadJson guardado: viaja sin volver a mapearse. */
@Serializable
data class OperacionDto(
    val idOperacion: String,
    val tipo: String,
    val versionBaseActivo: Int? = null,
    val evaluacion: JsonElement,
)

@Serializable
data class EvaluacionDto(
    val id: String,
    val activoId: String,
    val activoNuevo: ActivoNuevoDto? = null,
    val desastreId: String? = null,
    val instanteCampo: String,
    val evaluador: String,
    val organizacion: String,
    val lat: Double,
    val lon: Double,
    val precisionM: Double,
    val tipoActivoCodigo: String,
    val nivelDano: String,
    val descripcion: String,
    val evidencias: List<EvidenciaDto> = emptyList(),
)

@Serializable
data class ActivoNuevoDto(
    val id: String,
    val tipoCodigo: String,
    val lat: Double,
    val lon: Double,
    val referencia: String,
)

@Serializable
data class EvidenciaDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val instanteCaptura: String,
    val sha256: String,
    val tamanoBytes: Long,
)

@Serializable
data class RespuestaSincronizacionDto(val resultados: List<ResultadoOperacionDto> = emptyList())

/**
 * Los enums llegan como texto y se interpretan después: un valor desconocido deja esa operación
 * en la cola en lugar de hacer fallar todo el lote.
 */
@Serializable
data class ResultadoOperacionDto(
    val idOperacion: String,
    val resultado: String,
    val estadoEvaluacion: String? = null,
    val motivo: String? = null,
    val nuevaVersionActivo: Int? = null,
    val instanteServidor: String? = null,
)
