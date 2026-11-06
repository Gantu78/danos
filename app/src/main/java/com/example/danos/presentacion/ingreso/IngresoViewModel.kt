package com.example.danos.presentacion.ingreso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danos.acceso.ConfiguracionRepositorio
import com.example.danos.dominio.Limites
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ValidadorIngreso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IngresoViewModel @Inject constructor(
    private val configuracion: ConfiguracionRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(IngresoEstado(urlApi = configuracion.urlPorDefecto))
    val estado: StateFlow<IngresoEstado> = _estado.asStateFlow()

    private val _efectos = Channel<IngresoEfecto>(Channel.BUFFERED)
    val efectos: Flow<IngresoEfecto> = _efectos.receiveAsFlow()

    init {
        viewModelScope.launch {
            configuracion.actual()?.let { guardada ->
                _estado.update {
                    it.copy(
                        evaluador = guardada.evaluador,
                        organizacion = guardada.organizacion,
                        urlApi = guardada.urlApi,
                    )
                }
            }
        }
    }

    fun alCambiarEvaluador(valor: String) = _estado.update { it.copy(evaluador = valor, errorEvaluador = null) }

    fun alCambiarOrganizacion(valor: String) = _estado.update { it.copy(organizacion = valor, errorOrganizacion = null) }

    fun alCambiarUrl(valor: String) = _estado.update { it.copy(urlApi = valor, errorUrl = null, pruebaConexion = null) }

    fun probarConexion() {
        val url = _estado.value.urlApi
        if (!ValidadorIngreso.urlValida(url)) {
            _estado.update { it.copy(errorUrl = ERROR_URL) }
            return
        }
        _estado.update { it.copy(probando = true, pruebaConexion = null) }
        viewModelScope.launch {
            val prueba = when (val resultado = configuracion.probarConexion(url)) {
                is Resultado.Exito -> PruebaConexion(exitosa = true, mensaje = "Conexión correcta con el servidor")
                is Resultado.Error -> PruebaConexion(exitosa = false, mensaje = resultado.mensaje)
            }
            // Si la URL cambió mientras se probaba, el resultado ya no aplica.
            _estado.update { if (it.urlApi == url) it.copy(probando = false, pruebaConexion = prueba) else it.copy(probando = false) }
        }
    }

    private fun errorNombre(valor: String, siVacio: String): String? = when {
        valor.isBlank() -> siVacio
        valor.trim().length > Limites.NOMBRE -> "Máximo ${Limites.NOMBRE} caracteres"
        else -> null
    }

    fun guardar() {
        val actual = _estado.value
        val conErrores = actual.copy(
            errorEvaluador = errorNombre(actual.evaluador, "El evaluador es obligatorio"),
            errorOrganizacion = errorNombre(actual.organizacion, "La organización es obligatoria"),
            errorUrl = ERROR_URL.takeUnless { ValidadorIngreso.urlValida(actual.urlApi) },
        )
        if (conErrores.errorEvaluador != null || conErrores.errorOrganizacion != null || conErrores.errorUrl != null) {
            _estado.value = conErrores
            return
        }
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            configuracion.guardar(actual.evaluador.trim(), actual.organizacion.trim(), actual.urlApi.trim())
            _estado.update { it.copy(guardando = false) }
            _efectos.send(IngresoEfecto.Guardado)
        }
    }

    private companion object {
        const val ERROR_URL = "Escribe una URL válida, por ejemplo http://192.168.1.10:5080"
    }
}
