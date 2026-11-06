package com.example.danos.infraestructura.remoto

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface DanosApi {
    @GET("v1/descarga")
    suspend fun descargar(@Query("desastreId") desastreId: String?): DescargaDto

    @POST("v1/sincronizacion/evaluaciones")
    suspend fun sincronizar(@Body lote: LoteSincronizacionDto): RespuestaSincronizacionDto

    @Multipart
    @PUT("v1/evidencias/{id}")
    suspend fun subirEvidencia(
        @Path("id") id: String,
        @Part archivo: MultipartBody.Part,
    ): Response<Unit>
}
