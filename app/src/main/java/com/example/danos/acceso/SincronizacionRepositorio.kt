package com.example.danos.acceso

import com.example.danos.dominio.MENSAJE_INICIAR_SESION
import com.example.danos.infraestructura.local.EvidenciaDao
import com.example.danos.infraestructura.local.OperacionDao
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val identidad: IdentidadRepositorio,
) : SincronizacionRepositorio {

    override fun observarOperacionesPendientes(): Flow<Int> = operacionDao.observarConteo()

    override fun observarUltimoError(): Flow<String?> = combine(
        operacionDao.observarUltimoError(),
        identidad.requiereInicioSesion,
    ) { errorOperacion, requiereLogin ->
        if (requiereLogin) MENSAJE_INICIAR_SESION else errorOperacion
    }

    override fun sincronizar() = programador.programar()

    override suspend fun sincronizarSiHayPendientes() {
        if (operacionDao.contar() > 0 || evidenciaDao.pendientesDeSubir().isNotEmpty()) {
            programador.programar()
        }
    }
}
