package com.example.danos.infraestructura.remoto

import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.DateTimeException

suspend fun <T> llamarApi(llamada: suspend () -> T): Resultado<T> = try {
    Resultado.Exito(llamada())
} catch (e: CancellationException) {
    throw e
} catch (_: IOException) {
    Resultado.Error(TipoError.SIN_RED, "Sin conexión con el servidor")
} catch (e: HttpException) {
    errorHttp(e.code())
} catch (_: SerializationException) {
    Resultado.Error(TipoError.SERVIDOR, "El servidor envió una respuesta no reconocida")
} catch (_: DateTimeException) {
    Resultado.Error(TipoError.SERVIDOR, "El servidor envió una fecha no reconocida")
}

fun errorHttp(codigo: Int): Resultado.Error =
    if (codigo in 400..499) {
        Resultado.Error(TipoError.CLIENTE, "El servidor rechazó la solicitud (HTTP $codigo)")
    } else {
        Resultado.Error(TipoError.SERVIDOR, "Error del servidor (HTTP $codigo)")
    }
