package com.example.danos.acceso

import com.example.danos.dominio.RegistroDesastre
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TIPOS_DESASTRE_RN1
import com.example.danos.dominio.TipoDesastre
import com.example.danos.dominio.TipoError
import com.example.danos.infraestructura.local.RegistroDesastreDao
import com.example.danos.infraestructura.local.aDominio
import com.example.danos.infraestructura.local.aEntidad
import com.example.danos.infraestructura.sync.EnviadorDesastres
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Puerta de la presentación al registro de desastres en campo (RF-GD-02) y a su cola local (AD-44). */
interface DesastreRepositorio {
    fun observarRegistros(): Flow<List<RegistroDesastre>>
    fun observarPendientes(): Flow<Int>

    /** Catálogo descargado o, si todavía no hay, los tipos de RN-1. */
    fun observarTipos(): Flow<List<TipoDesastre>>

    /** Guarda el registro en la cola local y programa el envío. No necesita red. */
    suspend fun registrar(registro: RegistroDesastre): Resultado<Unit>

    suspend fun actualizarTipos(): Boolean
}

class DesastreRepositorioImpl @Inject constructor(
    private val dao: RegistroDesastreDao,
    private val enviador: EnviadorDesastres,
    private val programador: ProgramadorSincronizacion,
) : DesastreRepositorio {

    override fun observarRegistros(): Flow<List<RegistroDesastre>> =
        dao.observarTodos().map { lista -> lista.map { it.aDominio() } }

    override fun observarPendientes(): Flow<Int> = dao.observarPendientes()

    override fun observarTipos(): Flow<List<TipoDesastre>> =
        dao.observarTipos().map { lista -> lista.map { it.aDominio() }.ifEmpty { TIPOS_DESASTRE_RN1 } }

    override suspend fun registrar(registro: RegistroDesastre): Resultado<Unit> = try {
        dao.insertar(registro.aEntidad())
        programador.programar()
        Resultado.Exito(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(TipoError.LOCAL, "No se pudo guardar el registro en el teléfono: ${e.message}")
    }

    override suspend fun actualizarTipos(): Boolean = enviador.actualizarTipos()
}
