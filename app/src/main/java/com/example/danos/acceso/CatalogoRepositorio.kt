package com.example.danos.acceso

import com.example.danos.dominio.Activo
import com.example.danos.dominio.CriterioNivel
import com.example.danos.dominio.Desastre
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoActivo
import com.example.danos.infraestructura.local.ActivoDao
import com.example.danos.infraestructura.local.CatalogoDao
import com.example.danos.infraestructura.local.CriterioNivelEntity
import com.example.danos.infraestructura.local.DesastreEntity
import com.example.danos.infraestructura.local.EjecutorTransaccion
import com.example.danos.infraestructura.local.TipoActivoEntity
import com.example.danos.infraestructura.local.aDominio
import com.example.danos.infraestructura.local.aEntidad
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.remoto.DanosApi
import com.example.danos.infraestructura.remoto.DescargaDto
import com.example.danos.infraestructura.remoto.aDominio
import com.example.danos.infraestructura.remoto.criteriosADominio
import com.example.danos.infraestructura.remoto.instanteDesdeIso
import com.example.danos.infraestructura.remoto.llamarApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

interface CatalogoRepositorio {
    fun observarTipos(): Flow<List<TipoActivo>>
    fun observarCriterios(): Flow<List<CriterioNivel>>
    fun observarDesastres(): Flow<List<Desastre>>
    fun observarActivos(): Flow<List<Activo>>
    fun observarUltimaDescarga(): Flow<Instant?>

    /** Un fallo no toca nada de lo ya descargado. */
    suspend fun descargar(desastreId: String?): Resultado<Unit>
}

class CatalogoRepositorioImpl @Inject constructor(
    private val api: DanosApi,
    private val transaccion: EjecutorTransaccion,
    private val catalogoDao: CatalogoDao,
    private val activoDao: ActivoDao,
    private val preferencias: PreferenciasLocales,
) : CatalogoRepositorio {

    override fun observarTipos() = catalogoDao.observarTipos().map { filas -> filas.map { it.aDominio() } }
    override fun observarCriterios() = catalogoDao.observarCriterios().map { filas -> filas.map { it.aDominio() } }
    override fun observarDesastres() = catalogoDao.observarDesastres().map { filas -> filas.map { it.aDominio() } }
    override fun observarActivos() = activoDao.observarTodos().map { filas -> filas.map { it.aDominio() } }
    override fun observarUltimaDescarga() = preferencias.ultimaDescarga

    override suspend fun descargar(desastreId: String?): Resultado<Unit> {
        val respuesta = llamarApi {
            val dto = api.descargar(desastreId)
            dto to instanteDesdeIso(dto.instanteServidor)
        }
        return when (respuesta) {
            is Resultado.Error -> respuesta
            is Resultado.Exito -> {
                val (dto, instante) = respuesta.valor
                aplicar(dto)
                preferencias.guardarUltimaDescarga(instante)
                Resultado.Exito(Unit)
            }
        }
    }

    private suspend fun aplicar(dto: DescargaDto) = transaccion.enTransaccion {
        catalogoDao.borrarCriterios()
        catalogoDao.borrarTipos()
        catalogoDao.borrarDesastres()
        catalogoDao.insertarTipos(dto.tiposActivo.map { TipoActivoEntity(it.codigo, it.nombre) })
        catalogoDao.insertarCriterios(
            dto.tiposActivo.flatMap { it.criteriosADominio() }
                .map { CriterioNivelEntity(it.tipoCodigo, it.nivel, it.criterio) },
        )
        catalogoDao.insertarDesastres(dto.desastres.map { DesastreEntity(it.id, it.nombre, it.tipo) })

        activoDao.borrarDescargadosReemplazables()
        val protegidos = activoDao.idsProtegidos().toHashSet()
        activoDao.guardarTodos(
            dto.activos.filterNot { it.id in protegidos }.map { it.aDominio().aEntidad() },
        )
    }
}
