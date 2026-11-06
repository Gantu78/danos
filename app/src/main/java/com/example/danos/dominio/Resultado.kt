package com.example.danos.dominio

sealed class Resultado<out T> {
    data class Exito<T>(val valor: T) : Resultado<T>()
    data class Error(val tipo: TipoError, val mensaje: String) : Resultado<Nothing>()
}

enum class TipoError {
    /** Sin red o tiempo agotado: reintentar. */
    SIN_RED,

    /** 5xx o respuesta ilegible: reintentar. */
    SERVIDOR,

    /** 4xx: reintentar no ayuda, hace falta otra acción. */
    CLIENTE,

    /** La acción no corresponde a este usuario. */
    NO_PERMITIDO,

    /** Falló el almacenamiento del teléfono. */
    LOCAL,
}
