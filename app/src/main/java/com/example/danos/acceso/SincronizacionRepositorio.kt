package com.example.danos.acceso

import com.example.danos.infraestructura.local.EvidenciaDao
import com.example.danos.infraestructura.local.OperacionDao
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface SincronizacionRepositorio {
    fun observarOperacionesPendientes(): Flow<Int>
    fun observarUltimoError(): Flow<String?>
    fun sincronizar()
    suspend fun sincronizarSiHayPendientes()
}

class SincronizacionRepositorioImpl @Inject constructor(
    private val operacionDao: OperacionDao,
    private val evidenciaDao: EvidenciaDao,
    private val programador: ProgramadorSincronizacion,
) : SincronizacionRepositorio {

    override fun observarOperacionesPendientes(): Flow<Int> = operacionDao.observarConteo()

    override fun observarUltimoError(): Flow<String?> = operacionDao.observarUltimoError()

    override fun sincronizar() = programador.programar()

    override suspend fun sincronizarSiHayPendientes() {
        if (operacionDao.contar() > 0 || evidenciaDao.pendientesDeSubir().isNotEmpty()) {
            programador.programar()
        }
    }
}
