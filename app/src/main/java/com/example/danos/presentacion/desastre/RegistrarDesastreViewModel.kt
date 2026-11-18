package com.example.danos.presentacion.desastre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danos.BuildConfig
import com.example.danos.acceso.ConfiguracionRepositorio
import com.example.danos.acceso.DesastreRepositorio
import com.example.danos.acceso.DispositivoRepositorio
import com.example.danos.acceso.IdentidadRepositorio
import com.example.danos.dominio.CampoDesastre
import com.example.danos.dominio.RADIOS_ZONA_M
import com.example.danos.dominio.RegistroDesastre
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResultadoUbicacion
import com.example.danos.dominio.SeveridadDesastre
import com.example.danos.dominio.TipoDesastre
import com.example.danos.dominio.ValidadorRegistroDesastre
import com.example.danos.dominio.puedeRegistrarDesastres
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Horas atrás que ofrece el formulario para la hora de inicio del evento (atributo 3 del Cuadro 7 del SRS). */
val HORAS_INICIO: List<Int> = listOf(0, 1, 3, 6, 12, 24, 48)

data class RegistrarDesastreEstado(
    val tipos: List<TipoDesastre> = emptyList(),
    val tipo: String? = null,
    val nombre: String = "",
    val descripcion: String = "",
    val severidad: SeveridadDesastre? = null,
    val responsable: String = "",
    val horasDesdeInicio: Int = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val precisionM: Double? = null,
    val radioZonaM: Int? = RADIOS_ZONA_M[1],
    val municipios: String = "",
    val obteniendoUbicacion: Boolean = false,
    val errorUbicacion: String? = null,
    val errores: Map<CampoDesastre, String> = emptyMap(),
    val guardando: Boolean = false,
    val autor: String = "",
    val organizacion: String = "",
    /** Null mientras se lee la sesión. */
    val tienePermiso: Boolean? = null,
)

sealed interface RegistrarDesastreEfecto {
    data object Guardado : RegistrarDesastreEfecto
    data class Error(val mensaje: String) : RegistrarDesastreEfecto
}

@HiltViewModel
class RegistrarDesastreViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val desastres: DesastreRepositorio,
    private val configuracion: ConfiguracionRepositorio,
    private val identidad: IdentidadRepositorio,
    private val dispositivo: DispositivoRepositorio,
) : ViewModel() {

    /** El id se fija al abrir el formulario y sobrevive a la rotación: es la llave de idempotencia del envío. */
    private val id: String = estadoGuardado[CLAVE_ID] ?: UUID.randomUUID().toString().also { estadoGuardado[CLAVE_ID] = it }

    private val _estado = MutableStateFlow(RegistrarDesastreEstado())
    val estado: StateFlow<RegistrarDesastreEstado> = _estado.asStateFlow()

    private val _efectos = Channel<RegistrarDesastreEfecto>(Channel.BUFFERED)
    val efectos: Flow<RegistrarDesastreEfecto> = _efectos.receiveAsFlow()

    init {
        desastres.observarTipos().onEach { tipos -> _estado.update { it.copy(tipos = tipos) } }.launchIn(viewModelScope)
        viewModelScope.launch { cargarIdentidad() }
        viewModelScope.launch { desastres.actualizarTipos() }
    }

    private suspend fun cargarIdentidad() {
        val config = configuracion.actual()
        val modoEncabezados = BuildConfig.DEBUG && config?.modoEncabezados == true
        val sesion = if (modoEncabezados) null else identidad.sesionActual()
        val autor = sesion?.usuario ?: config?.evaluador.orEmpty()
        val organizacion = sesion?.organizacion ?: config?.organizacion.orEmpty()
        // En modo encabezados (solo debug) el servidor decide el permiso; con OIDC se mira el rol del token.
        val permiso = modoEncabezados || (sesion != null && puedeRegistrarDesastres(sesion.roles))
        _estado.update {
            it.copy(
                autor = autor,
                organizacion = organizacion,
                responsable = it.responsable.ifBlank { organizacion },
                tienePermiso = permiso,
            )
        }
    }

    fun alCambiarTipo(valor: String) = cambiar(CampoDesastre.TIPO) { it.copy(tipo = valor) }
    fun alCambiarNombre(valor: String) = cambiar(CampoDesastre.NOMBRE) { it.copy(nombre = valor) }
    fun alCambiarDescripcion(valor: String) = cambiar(CampoDesastre.DESCRIPCION) { it.copy(descripcion = valor) }
    fun alCambiarSeveridad(valor: SeveridadDesastre) = cambiar(CampoDesastre.SEVERIDAD) { it.copy(severidad = valor) }
    fun alCambiarResponsable(valor: String) = cambiar(CampoDesastre.RESPONSABLE) { it.copy(responsable = valor) }
    fun alCambiarHoras(valor: Int) = cambiar(CampoDesastre.INICIO) { it.copy(horasDesdeInicio = valor) }
    fun alCambiarRadio(valor: Int?) = cambiar(CampoDesastre.ZONA) { it.copy(radioZonaM = valor) }
    fun alCambiarMunicipios(valor: String) = cambiar(CampoDesastre.MUNICIPIOS) { it.copy(municipios = valor) }

    private fun cambiar(campo: CampoDesastre, cambio: (RegistrarDesastreEstado) -> RegistrarDesastreEstado) {
        _estado.update { cambio(it).copy(errores = it.errores - campo) }
    }

    fun obtenerUbicacion() {
        _estado.update { it.copy(obteniendoUbicacion = true, errorUbicacion = null) }
        viewModelScope.launch {
            when (val resultado = dispositivo.obtenerUbicacion()) {
                is ResultadoUbicacion.Exito -> _estado.update {
                    it.copy(
                        lat = resultado.ubicacion.lat,
                        lon = resultado.ubicacion.lon,
                        precisionM = resultado.ubicacion.precisionM,
                        obteniendoUbicacion = false,
                        errores = it.errores - CampoDesastre.UBICACION,
                    )
                }
                ResultadoUbicacion.SinPermiso -> _estado.update {
                    it.copy(obteniendoUbicacion = false, errorUbicacion = "Conceda el permiso de ubicación")
                }
                ResultadoUbicacion.NoDisponible -> _estado.update {
                    it.copy(obteniendoUbicacion = false, errorUbicacion = "Ubicación no disponible; intente al aire libre")
                }
            }
        }
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando) return
        val ahora = Instant.now()
        val inicio = ahora.minus(Duration.ofHours(actual.horasDesdeInicio.toLong()))
        val municipios = ValidadorRegistroDesastre.separarMunicipios(actual.municipios)
        val errores = ValidadorRegistroDesastre.validar(
            tipo = actual.tipo,
            nombre = actual.nombre,
            descripcion = actual.descripcion,
            severidad = actual.severidad,
            responsable = actual.responsable,
            lat = actual.lat,
            lon = actual.lon,
            radioZonaM = actual.radioZonaM,
            codigosMunicipio = municipios,
            instanteInicio = inicio,
            ahora = ahora,
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores) }
            return
        }
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val registro = RegistroDesastre(
                id = id,
                tipo = actual.tipo!!,
                nombre = actual.nombre.trim(),
                descripcion = actual.descripcion.trim(),
                instanteInicio = inicio,
                lat = actual.lat!!,
                lon = actual.lon!!,
                precisionM = actual.precisionM ?: 0.0,
                severidad = actual.severidad!!,
                responsable = actual.responsable.trim(),
                radioZonaM = actual.radioZonaM ?: 0,
                codigosMunicipio = municipios,
                autor = actual.autor,
                organizacion = actual.organizacion,
                instanteRegistro = ahora,
            )
            when (val resultado = desastres.registrar(registro)) {
                is Resultado.Exito -> _efectos.send(RegistrarDesastreEfecto.Guardado)
                is Resultado.Error -> {
                    _estado.update { it.copy(guardando = false) }
                    _efectos.send(RegistrarDesastreEfecto.Error(resultado.mensaje))
                }
            }
        }
    }

    private companion object {
        const val CLAVE_ID = "id_registro_desastre"
    }
}
