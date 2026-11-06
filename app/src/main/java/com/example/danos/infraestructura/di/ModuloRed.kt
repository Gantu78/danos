package com.example.danos.infraestructura.di

import com.example.danos.BuildConfig
import com.example.danos.infraestructura.remoto.ConfiguracionInterceptor
import com.example.danos.infraestructura.remoto.DanosApi
import com.example.danos.infraestructura.remoto.JsonDanos
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloRed {

    private const val TIEMPO_MAXIMO_S = 15L

    /** El host real lo pone ConfiguracionInterceptor con la URL guardada. */
    private const val URL_FICTICIA = "http://api.invalid/"

    @Provides
    @Singleton
    fun clienteHttp(configuracion: ConfiguracionInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(TIEMPO_MAXIMO_S, TimeUnit.SECONDS)
            .readTimeout(TIEMPO_MAXIMO_S, TimeUnit.SECONDS)
            .writeTimeout(TIEMPO_MAXIMO_S, TimeUnit.SECONDS)
            .addInterceptor(configuracion)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .build()

    @Provides
    @Singleton
    fun retrofit(cliente: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(URL_FICTICIA)
            .client(cliente)
            .addConverterFactory(JsonDanos.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun api(retrofit: Retrofit): DanosApi = retrofit.create(DanosApi::class.java)
}
