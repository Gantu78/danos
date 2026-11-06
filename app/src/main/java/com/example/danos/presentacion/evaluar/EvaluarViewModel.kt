package com.example.danos.presentacion.evaluar

import kotlinx.serialization.decodeFromString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.danos.acceso.CatalogoRepositorio
import com.example.danos.acceso.ConfiguracionRepositorio
import com.example.danos.acceso.DispositivoRepositorio
import com.example.danos.acceso.EvaluacionRepositorio
import com.example.danos.dominio.Activo
import com.example.danos.dominio.CampoEvaluacion
import com.example.danos.dominio.ErrorCampo
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.FotoEnCurso
import com.example.danos.dominio.Limites
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResultadoUbicacion
import com.example.danos.dominio.ValidadorEvaluacion
import com.example.danos.presentacion.navegacion.RutaEvaluar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EvaluarViewModel @Inject constructor(
    private val estadoGuardado: SavedStateHandle,
    private val evaluaciones: EvaluacionRepositorio,
    private val configuracion: ConfiguracionRepositorio,
    private val dispositivo: DispositivoRepositorio,
    catalogo: CatalogoRepositorio,
) : ViewModel() {

    private val evaluacionId: String? = estadoGuardado.toRoute<RutaEvaluar>().evaluacionId
    private val idNueva: String = estadoGuardado[CLAVE_ID_NUEVA]
        ?: UUID.randomUUID().toString().also { estadoGuardado[CLAVE_ID_NUEVA] = it }
    private var original: Evaluacion? = null
    private var guardadaConExito = false

    private val _estado = MutableStateFlow(EvaluarEstado(esEdicion = evaluacionId != null))
    val estado: StateFlow<EvaluarEstado> = _estado.asStateFlow()

    private val _efectos = Channel<EvaluarEfecto>(Channel.BUFFERED)
    val efectos: Flow<EvaluarEfecto> = _efectos.receiveAsFlow()

    init {
        combine(
            catalogo.observarTipos(),
            catalogo.observarCriterios(),
            catalogo.observarDesastres(),
            catalogo.observarActivos(),
        ) { tipos, criterios, desastres, activos ->
            _estado.update { it.copy(tipos = tipos, criterios = criterios, desastres = desastres, activos = activos) }
        }.launchIn(viewModelScope)
        viewModelScope.launch { cargar() }
    }

    private suspend fun cargar() {
        val existente = evaluacionId?.let { evaluaciones.obtener(it) }
        original = existente
        val restaurado = estadoGuardado.get<String>(CLAVE_FORMULARIO)
            ?.let { Json.decodeFromString<FormularioEvaluar>(it) }
        val inicial = restaurado
            ?: existente?.aFormulario()
            ?: FormularioEvaluar(instanteCampoMs = Instant.now().toEpochMilli())
        val soloLectura = existente != null && existente.evaluador != configuracion.actual()?.evaluador
        _estado.update { it.copy(cargando = false, formulario = inicial, soloLectura = soloLectura) }
        guardarFormulario()
    }

    private fun Evaluacion.aFormulario() = FormularioEvaluar(
        instanteCampoMs = instanteCampo.toEpochMilli(),
        activoId = activoId,
        lat = lat,
        lon = lon,
        precisionM = precisionM,
        tipoCodigo = tipoActivoCodigo,
        nivel = nivelDano,
        desastreId = desastreId,
        descripcion = descripcion,
        ubicacionSolicitada = true,
        fotos = evidencias.map {
            FotoFormulario(
                id = it.id,
                rutaArchivo = it.rutaArchivo,
                lat = it.lat,
                lon = it.lon,
                instanteCapturaMs = it.instanteCaptura.toEpochMilli(),
                sha256 = it.sha256,
                tamanoBytes = it.tamanoBytes,
                guardada = true,
            )
        },
    )

    private fun actualizarFormulario(cambio: (FormularioEvaluar) -> FormularioEvaluar) {
        _estado.update { it.copy(formulario = cambio(it.formulario)) }
        guardarFormulario()
    }

    private fun guardarFormulario() {
        estadoGuardado[CLAVE_FORMULARIO] = Json.encodeToString(_estado.value.formulario)
    }

    private fun limpiarError(campo: CampoEvaluacion) = _estado.update { it.copy(errores = it.errores - campo) }

    fun alCambiarBusqueda(texto: String) = actualizarFormulario { it.copy(busqueda = texto) }

    fun alElegirModoActivoNuevo(nuevo: Boolean) {
        actualizarFormulario { it.copy(activoNuevo = nuevo, activoId = if (nuevo) null else it.activoId) }
        limpiarError(CampoEvaluacion.ACTIVO)
    }

    fun alElegirActivo(activo: Activo) {
        actualizarFormulario { it.copy(activoId = activo.id, tipoCodigo = activo.tipoCodigo) }
        limpiarError(CampoEvaluacion.ACTIVO)
        limpiarError(CampoEvaluacion.TIPO_ACTIVO)
    }

    fun alCambiarReferencia(texto: String) {
        actualizarFormulario { it.copy(referenciaNueva = texto) }
        _estado.update { it.copy(errorReferencia = null) }
    }

    fun alElegirTipo(codigo: String?) {
        actualizarFormulario { it.copy(tipoCodigo = codigo) }
        limpiarError(CampoEvaluacion.TIPO_ACTIVO)
    }

    fun alElegirNivel(nivel: NivelDano) {
        actualizarFormulario { it.copy(nivel = nivel) }
        limpiarError(CampoEvaluacion.NIVEL_DANO)
    }

    fun alElegirDesastre(id: String?) = actualizarFormulario { it.copy(desastreId = id) }

    fun alCambiarDescripcion(texto: String) {
        actualizarFormulario { it.copy(descripcion = texto) }
        limpiarError(CampoEvaluacion.DESCRIPCION)
    }

    fun alSolicitarUbicacion() = actualizarFormulario { it.copy(ubicacionSolicitada = true) }

    fun alNegarPermisoUbicacion() = _estado.update {
        it.copy(
            obteniendoUbicacion = false,
            errorUbicacion = "Sin permiso de ubicación no se puede registrar la evaluación. Actívalo y reintenta.",
        )
    }

    fun obtenerUbicacion() {
        _estado.update { it.copy(obteniendoUbicacion = true, errorUbicacion = null) }
        viewModelScope.launch {
            when (val resultado = dispositivo.obtenerUbicacion()) {
                is ResultadoUbicacion.Exito -> {
                    actualizarFormulario {
                        it.copy(
                            lat = resultado.ubicacion.lat,
                            lon = resultado.ubicacion.lon,
                            precisionM = resultado.ubicacion.precisionM,
                        )
                    }
                    _estado.update {
                        it.copy(
                            obteniendoUbicacion = false,
                            errores = it.errores - CampoEvaluacion.UBICACION - CampoEvaluacion.PRECISION,
                        )
                    }
                }
                ResultadoUbicacion.SinPermiso -> alNegarPermisoUbicacion()
                ResultadoUbicacion.NoDisponible -> _estado.update {
                    it.copy(
                        obteniendoUbicacion = false,
                        errorUbicacion = "No se pudo obtener la ubicación. Sal a cielo abierto y reintenta.",
                    )
                }
            }
        }
    }

    fun tomarFoto() {
        if (_estado.value.formulario.ubicacion == null) {
            viewModelScope.launch {
                _efectos.send(EvaluarEfecto.Mensaje("Obtén la ubicación antes de tomar la foto"))
            }
            return
        }
        val foto = dispositivo.prepararFoto()
        actualizarFormulario {
            it.copy(fotoEnCursoId = foto.id, fotoEnCursoRuta = foto.rutaArchivo, fotoEnCursoUri = foto.uri)
        }
        viewModelScope.launch { _efectos.send(EvaluarEfecto.AbrirCamara(foto.uri)) }
    }

    fun alVolverDeCamara(tomada: Boolean) {
        val formulario = _estado.value.formulario
        val id = formulario.fotoEnCursoId ?: return
        val ruta = formulario.fotoEnCursoRuta ?: return
        val uri = formulario.fotoEnCursoUri ?: return
        actualizarFormulario { it.copy(fotoEnCursoId = null, fotoEnCursoRuta = null, fotoEnCursoUri = null) }
        if (!tomada) {
            dispositivo.descartarFoto(ruta)
            return
        }
        _estado.update { it.copy(procesandoFoto = true) }
        viewModelScope.launch {
            val procesada = dispositivo.procesarFoto(FotoEnCurso(id, ruta, uri))
            val ubicacion = _estado.value.formulario.ubicacion
            if (procesada == null || ubicacion == null) {
                dispositivo.descartarFoto(ruta)
                _efectos.send(EvaluarEfecto.Mensaje("No se pudo procesar la foto"))
            } else {
                actualizarFormulario {
                    it.copy(
                        fotos = it.fotos + FotoFormulario(
                            id = id,
                            rutaArchivo = ruta,
                            lat = ubicacion.lat,
                            lon = ubicacion.lon,
                            instanteCapturaMs = Instant.now().toEpochMilli(),
                            sha256 = procesada.sha256,
                            tamanoBytes = procesada.tamanoBytes,
                            guardada = false,
                        ),
                    )
                }
            }
            _estado.update { it.copy(procesandoFoto = false) }
        }
    }

    fun borrarFoto(foto: FotoFormulario) {
        if (foto.guardada) return
        dispositivo.descartarFoto(foto.rutaArchivo)
        actualizarFormulario { it.copy(fotos = it.fotos - foto) }
    }

    fun guardar() {
        val estadoActual = _estado.value
        if (estadoActual.guardando || estadoActual.soloLectura) return
        val formulario = estadoActual.formulario
        val referencia = formulario.referenciaNueva.trim()
        val faltaReferencia = evaluacionId == null && formulario.activoNuevo && referencia.isEmpty()
        val referenciaLarga = evaluacionId == null && formulario.activoNuevo && referencia.length > Limites.REFERENCIA
        val tipoCodigo = formulario.tipoCodigo.orEmpty()
        val ubicacion = formulario.ubicacion

        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val config = configuracion.actual()
            val activoNuevo = if (evaluacionId == null && formulario.activoNuevo && !faltaReferencia && ubicacion != null) {
                Activo(UUID.randomUUID().toString(), tipoCodigo, ubicacion.lat, ubicacion.lon, referencia, null)
            } else {
                null
            }
            val evaluacion = construirEvaluacion(formulario, activoNuevo?.id ?: formulario.activoId.orEmpty(), config?.evaluador, config?.organizacion)
            val errores = buildList {
                addAll(ValidadorEvaluacion.validar(evaluacion))
                if (ubicacion == null) add(ErrorCampo(CampoEvaluacion.UBICACION, "Falta la ubicación"))
            }.filterNot { formulario.activoNuevo && it.campo == CampoEvaluacion.ACTIVO }

            if (errores.isNotEmpty() || faltaReferencia || referenciaLarga) {
                _estado.update {
                    it.copy(
                        guardando = false,
                        errores = errores.associate { error -> error.campo to error.mensaje },
                        errorReferencia = when {
                            faltaReferencia -> "La referencia es obligatoria"
                            referenciaLarga -> "Máximo ${Limites.REFERENCIA} caracteres"
                            else -> null
                        },
                    )
                }
                return@launch
            }

            val resultado = if (evaluacionId == null) {
                evaluaciones.guardarNueva(evaluacion, activoNuevo)
            } else {
                evaluaciones.guardarEdicion(evaluacion)
            }
            _estado.update { it.copy(guardando = false) }
            when (resultado) {
                is Resultado.Exito -> {
                    guardadaConExito = true
                    _efectos.send(EvaluarEfecto.Terminado)
                }
                is Resultado.Error -> _efectos.send(EvaluarEfecto.Mensaje(resultado.mensaje))
            }
        }
    }

    private fun construirEvaluacion(
        formulario: FormularioEvaluar,
        activoId: String,
        evaluador: String?,
        organizacion: String?,
    ): Evaluacion {
        val id = evaluacionId ?: idNueva
        val base = original
        return Evaluacion(
            id = id,
            activoId = base?.activoId ?: activoId,
            desastreId = formulario.desastreId,
            instanteCampo = base?.instanteCampo ?: Instant.ofEpochMilli(formulario.instanteCampoMs),
            evaluador = base?.evaluador ?: evaluador.orEmpty(),
            organizacion = base?.organizacion ?: organizacion.orEmpty(),
            lat = formulario.lat ?: 0.0,
            lon = formulario.lon ?: 0.0,
            precisionM = formulario.precisionM ?: 0.0,
            tipoActivoCodigo = formulario.tipoCodigo.orEmpty(),
            nivelDano = formulario.nivel,
            descripcion = formulario.descripcion.trim(),
            evidencias = formulario.fotos.map {
                Evidencia(
                    id = it.id,
                    evaluacionId = id,
                    rutaArchivo = it.rutaArchivo,
                    lat = it.lat,
                    lon = it.lon,
                    instanteCaptura = Instant.ofEpochMilli(it.instanteCapturaMs),
                    sha256 = it.sha256,
                    tamanoBytes = it.tamanoBytes,
                )
            },
        )
    }

    /** Al salir sin guardar, las fotos nuevas no pertenecen a ninguna evaluación. */
    override fun onCleared() {
        if (!guardadaConExito) {
            _estado.value.formulario.fotos.filterNot { it.guardada }
                .forEach { dispositivo.descartarFoto(it.rutaArchivo) }
        }
    }

    private companion object {
        const val CLAVE_FORMULARIO = "formulario"
        const val CLAVE_ID_NUEVA = "id_nueva"
    }
}
