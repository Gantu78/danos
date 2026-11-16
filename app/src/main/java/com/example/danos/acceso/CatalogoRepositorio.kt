package com.example.danos.acceso

import com.example.danos.BuildConfig
import com.example.danos.dominio.Activo
import com.example.danos.dominio.CriterioNivel
import com.example.danos.dominio.Desastre
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResultadoToken
import com.example.danos.dominio.TipoActivo
import com.example.danos.dominio.TipoError
import com.example.danos.infraestructura.local.ActivoDao
import com.example.danos.infraestructura.local.ActivoEntity
import com.example.danos.infraestructura.local.CatalogoDao
import com.example.danos.infraestructura.local.EjecutorTransaccion
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

interface CatalogoRepositorio {
    fun observarTipos(): Flow<List<TipoActivo>>
    fun observarCriterios(): Flow<List<CriterioNivel>>
    fun observarDesastres(): Flow<List<Desastre>>
    fun observarActivos(): Flow<List<Activo>>
    fun observarUltimaDescarga(): Flow<Instant?>
    fun observarMarcadorDesde(): Flow<String?> = flowOf(null)

    /**
     * Descarga el catálogo y los activos (H-71, DA-19).
     * - Si [forzarCompleta] es true, borra el marcador `desde` y solicita una descarga completa.
     * - Si la respuesta trae `incremental = true`, fusiona los activos por `id` y `version` sin borrar los ausentes.
     * - Un fallo no toca nada de lo ya descargado.
     */
    suspend fun descargar(
        desastreId: String?,
        zona: String? = null,
        forzarCompleta: Boolean = false,
    ): Resultado<Unit>
}

class CatalogoRepositorioImpl @Inject constructor(
    private val api: DanosApi,
    private val transaccion: EjecutorTransaccion,
    private val catalogoDao: CatalogoDao,
    private val activoDao: ActivoDao,
    private val preferencias: PreferenciasLocales,
    private val identidad: IdentidadRepositorio? = null,
) : CatalogoRepositorio {

    override fun observarTipos() = catalogoDao.observarTipos().map { filas -> filas.map { it.aDominio() } }
    override fun observarCriterios() = catalogoDao.observarCriterios().map { filas -> filas.map { it.aDominio() } }
    override fun observarDesastres() = catalogoDao.observarDesastres().map { filas -> filas.map { it.aDominio() } }
    override fun observarActivos() = activoDao.observarTodos().map { filas -> filas.map { it.aDominio() } }
    override fun observarUltimaDescarga() = preferencias.ultimaDescarga
    override fun observarMarcadorDesde(): Flow<String?> = preferencias.marcadorDesde

    override suspend fun descargar(
        desastreId: String?,
        zona: String?,
        forzarCompleta: Boolean,
    ): Resultado<Unit> {
        if (identidad != null) {
            val config = preferencias.actual()
            if (!(BuildConfig.DEBUG && config?.modoEncabezados == true)) {
                when (val token = identidad.obtenerTokenFresco()) {
                    is ResultadoToken.SesionExpirada -> {
                        identidad.marcarReautenticacionRequerida()
                        return Resultado.Error(TipoError.NO_PERMITIDO, token.mensaje)
                    }
                    is ResultadoToken.ErrorRed -> return Resultado.Error(TipoError.SIN_RED, token.mensaje)
                    is ResultadoToken.Exito, ResultadoToken.ModoEncabezados -> Unit
                }
            }
        }

        if (forzarCompleta) {
            preferencias.guardarMarcadorDesde(null)
        }
        val desde = if (forzarCompleta) null else preferencias.obtenerMarcadorDesde()?.takeIf { it.isNotBlank() }
        val zonaFiltro = zona?.trim()?.takeIf { it.isNotEmpty() }

        val respuesta = llamarApi {
            val dto = api.descargar(desastreId = desastreId, zona = zonaFiltro, desde = desde)
            dto to instanteDesdeIso(dto.instanteServidor)
        }
        return when (respuesta) {
            is Resultado.Error -> {
                if (respuesta.tipo == TipoError.NO_PERMITIDO) {
                    identidad?.marcarReautenticacionRequerida()
                }
                respuesta
            }
            is Resultado.Exito -> {
                val (dto, instante) = respuesta.valor
                aplicar(dto)
                preferencias.guardarUltimaDescarga(instante)
                preferencias.guardarMarcadorDesde(dto.instanteServidor)
                Resultado.Exito(Unit)
            }
        }
    }

    private suspend fun aplicar(dto: DescargaDto) = transaccion.enTransaccion {
        if (dto.incremental) {
            aplicarIncremental(dto)
        } else {
            aplicarCompleta(dto)
        }
    }

    private suspend fun aplicarCompleta(dto: DescargaDto) {
        catalogoDao.borrarCriterios()
        catalogoDao.borrarTipos()
        catalogoDao.insertarTipos(dto.tiposActivo.map { it.aDominio().aEntidad() })
        catalogoDao.insertarCriterios(
            dto.tiposActivo.flatMap { it.criteriosADominio() }.map { it.aEntidad() },
        )

        val desastresActivos = dto.desastres.filterNot { it.estado.equals("CERRADO", ignoreCase = true) }
        val idsEntrantes = desastresActivos.mapTo(HashSet()) { it.id }
        val idsConPendientes = catalogoDao.idsDesastresConEvaluacionesPendientes().toHashSet()
        val idsARetirar = catalogoDao.obtenerDesastres()
            .map { it.id }
            .filter { it !in idsEntrantes && it !in idsConPendientes }
        if (idsARetirar.isNotEmpty()) {
            catalogoDao.borrarDesastresPorIds(idsARetirar)
        }
        catalogoDao.insertarDesastres(desastresActivos.map { it.aDominio().aEntidad() })

        activoDao.borrarDescargadosReemplazables()
        fusionarActivos(dto)
    }

    private suspend fun aplicarIncremental(dto: DescargaDto) {
        for (tipoDto in dto.tiposActivo) {
            val existente = catalogoDao.obtenerTipo(tipoDto.codigo)
            if (existente == null || tipoDto.version >= existente.version) {
                catalogoDao.insertarTipos(listOf(tipoDto.aDominio().aEntidad()))
                catalogoDao.borrarCriteriosDeTipo(tipoDto.codigo)
                catalogoDao.insertarCriterios(tipoDto.criteriosADominio().map { it.aEntidad() })
            }
        }

        val desastresActivos = dto.desastres.filterNot { it.estado.equals("CERRADO", ignoreCase = true) }
        if (desastresActivos.isNotEmpty()) {
            catalogoDao.insertarDesastres(desastresActivos.map { it.aDominio().aEntidad() })
        }
        val idsCerrados = dto.desastres
            .filter { it.estado.equals("CERRADO", ignoreCase = true) }
            .map { it.id }
        if (idsCerrados.isNotEmpty()) {
            val idsConPendientes = catalogoDao.idsDesastresConEvaluacionesPendientes().toHashSet()
            val cerradosSinPendientes = idsCerrados.filterNot { it in idsConPendientes }
            if (cerradosSinPendientes.isNotEmpty()) {
                catalogoDao.borrarDesastresPorIds(cerradosSinPendientes)
            }
        }

        fusionarActivos(dto)
    }

    /**
     * Inserta o actualiza activos por id y versión, respetando los protegidos (creados en campo o con
     * operaciones pendientes) y sin retroceder nunca una versión local más reciente (DA-19).
     */
    private suspend fun fusionarActivos(dto: DescargaDto) {
        val protegidos = activoDao.idsProtegidos().toHashSet()
        val aGuardar = mutableListOf<ActivoEntity>()
        for (dtoActivo in dto.activos) {
            if (dtoActivo.id in protegidos) continue
            val existente = activoDao.obtener(dtoActivo.id)
            val versionLocal = existente?.versionDescargada
            if (existente == null || (versionLocal != null && dtoActivo.version >= versionLocal)) {
                aGuardar += dtoActivo.aDominio().aEntidad()
            }
        }
        if (aGuardar.isNotEmpty()) {
            activoDao.guardarTodos(aGuardar)
        }
    }
}
