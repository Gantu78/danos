package com.example.danos.infraestructura.local

import androidx.room.withTransaction
import javax.inject.Inject

/** Permite agrupar escrituras de varios DAO en una sola transacción de Room. */
interface EjecutorTransaccion {
    suspend fun <T> enTransaccion(bloque: suspend () -> T): T
}

class EjecutorTransaccionRoom @Inject constructor(
    private val baseDatos: AppDatabase,
) : EjecutorTransaccion {
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T =
        baseDatos.withTransaction { bloque() }
}
