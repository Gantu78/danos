package com.example.danos.infraestructura.remoto

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Rutas de Gestión de Desastres que usa la app de campo, a través del gateway (rutas gd-tipos y gd-registrar).
 * Registrar exige el rol organismo o autoridad (RF-GD-02); el catálogo lo puede leer cualquier usuario autenticado.
 */
interface DesastresApi {
    @GET("v1/catalogos/tipos-desastre")
    suspend fun tiposDesastre(): List<TipoDesastreDto>

    @POST("v1/desastres")
    suspend fun registrar(@Body registro: RegistroDesastreDto): Response<ResponseBody>
}

@Serializable
data class TipoDesastreDto(
    val codigo: String,
    val nombre: String,
    val vigente: Boolean = true,
)

@Serializable
data class GeometriaDto(
    val type: String = "Polygon",
    val coordinates: List<List<List<Double>>>,
)

@Serializable
data class ZonaRegistroDto(
    val geometria: GeometriaDto?,
    val codigosMunicipio: List<String>,
    val fuente: String = "EVALUACION_CAMPO",
)

@Serializable
data class ParticipanteDto(val organismo: String, val responsabilidad: String)

/** Cuerpo de POST /v1/desastres (CuerpoRegistro del servicio). El [id] hace idempotente el reenvío. */
@Serializable
data class RegistroDesastreDto(
    val id: String,
    val tipo: String,
    val nombre: String,
    val descripcion: String,
    val instanteInicio: String,
    val lat: Double,
    val lon: Double,
    val severidad: String,
    val responsable: String,
    val participantes: List<ParticipanteDto> = emptyList(),
    val zona: ZonaRegistroDto,
)

/** Cuerpo de error del servicio y del gateway: {codigo, mensaje}. */
@Serializable
data class ErrorApiDto(val codigo: String? = null, val mensaje: String? = null, val error: String? = null)
