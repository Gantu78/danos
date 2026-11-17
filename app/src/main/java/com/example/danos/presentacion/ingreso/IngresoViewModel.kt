package com.example.danos.presentacion.ingreso

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danos.BuildConfig
import com.example.danos.acceso.ConfiguracionRepositorio
import com.example.danos.acceso.IdentidadRepositorio
import com.example.danos.dominio.Limites
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ValidadorIngreso
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
import javax.inject.Inject

@HiltViewModel
class IngresoViewModel @Inject constructor(
    private val configuracion: ConfiguracionRepositorio,
    private val identidad: IdentidadRepositorio,
) : ViewModel() {

    private val exigirHttps: Boolean = !BuildConfig.DEBUG

    private val _estado = MutableStateFlow(
        IngresoEstado(
            urlApi = configuracion.urlPorDefecto,
            urlEmisor = configuracion.emisorPorDefecto,
            permiteModoEncabezados = configuracion.permiteModoEncabezados,
        ),
    )
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
                        urlEmisor = guardada.urlEmisor,
                        modoEncabezados = BuildConfig.DEBUG && guardada.modoEncabezados,
                    )
                }
            }
        }
        combine(
            identidad.sesion,
            identidad.requiereInicioSesion,
        ) { sesion, requiereLogin ->
            _estado.update {
                it.copy(
                    sesionActiva = sesion,
                    requiereInicioSesion = requiereLogin,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun alCambiarEvaluador(valor: String) = _estado.update { it.copy(evaluador = valor, errorEvaluador = null) }

    fun alCambiarOrganizacion(valor: String) = _estado.update { it.copy(organizacion = valor, errorOrganizacion = null) }

    fun alCambiarUrl(valor: String) = _estado.update { it.copy(urlApi = valor, errorUrl = null, pruebaConexion = null) }

    fun alCambiarEmisor(valor: String) = _estado.update { it.copy(urlEmisor = valor, errorEmisor = null, errorOidc = null) }

    fun alCambiarModoEncabezados(activo: Boolean) {
        if (!BuildConfig.DEBUG) return
        _estado.update {
            it.copy(
                modoEncabezados = activo,
                errorEvaluador = null,
                errorOrganizacion = null,
                errorEmisor = null,
                errorOidc = null,
            )
        }
    }

    fun probarConexion() {
        val url = _estado.value.urlApi
        if (!ValidadorIngreso.urlValida(url, exigirHttps)) {
            _estado.update { it.copy(errorUrl = mensajeErrorUrl()) }
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

    /** Inicia el flujo Authorization Code + PKCE (S256) contra el cliente público `movil` (H-70, DA-15). */
    fun iniciarSesionOidc() {
        val actual = _estado.value
        val errorUrl = mensajeErrorUrl().takeUnless { ValidadorIngreso.urlValida(actual.urlApi, exigirHttps) }
        val errorEmisor = mensajeErrorEmisor().takeUnless { ValidadorIngreso.urlValida(actual.urlEmisor, exigirHttps) }
        if (errorUrl != null || errorEmisor != null) {
            _estado.update { it.copy(errorUrl = errorUrl, errorEmisor = errorEmisor, errorOidc = null) }
            return
        }

        _estado.update { it.copy(guardando = true, errorOidc = null) }
        viewModelScope.launch {
            val sesionPrevia = identidad.sesionActual()
            configuracion.guardarConfiguracion(
                evaluador = sesionPrevia?.usuario ?: actual.evaluador.trim(),
                organizacion = sesionPrevia?.organizacion ?: actual.organizacion.trim(),
                urlApi = actual.urlApi.trim(),
                urlEmisor = actual.urlEmisor.trim(),
                modoEncabezados = false,
            )
            when (val preparado = identidad.prepararInicioSesion(actual.urlEmisor.trim())) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(guardando = false) }
                    _efectos.send(IngresoEfecto.LanzarOidc(preparado.valor))
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(guardando = false, errorOidc = preparado.mensaje) }
                }
            }
        }
    }

    fun alRecibirRespuestaOidc(datos: Intent?) {
        _estado.update { it.copy(guardando = true, errorOidc = null) }
        viewModelScope.launch {
            when (val resultado = identidad.procesarRespuestaAutorizacion(datos)) {
                is Resultado.Exito -> {
                    val sesion = resultado.valor
                    val actual = _estado.value
                    configuracion.guardarConfiguracion(
                        evaluador = sesion.usuario,
                        organizacion = sesion.organizacion,
                        urlApi = actual.urlApi.trim(),
                        urlEmisor = actual.urlEmisor.trim(),
                        modoEncabezados = false,
                    )
                    _estado.update {
                        it.copy(
                            guardando = false,
                            evaluador = sesion.usuario,
                            organizacion = sesion.organizacion,
                            sesionActiva = sesion,
                            requiereInicioSesion = false,
                        )
                    }
                    _efectos.send(IngresoEfecto.Guardado)
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(guardando = false, errorOidc = resultado.mensaje) }
                }
            }
        }
    }

    /** Guarda cambios de URL/emisor cuando ya hay una sesión OIDC activa, o guarda en modo encabezados (debug). */
    fun guardar() {
        val actual = _estado.value
        if (BuildConfig.DEBUG && actual.modoEncabezados) {
            val conErrores = actual.copy(
                errorEvaluador = errorNombre(actual.evaluador, "El evaluador es obligatorio"),
                errorOrganizacion = errorNombre(actual.organizacion, "La organización es obligatoria"),
                errorUrl = mensajeErrorUrl().takeUnless { ValidadorIngreso.urlValida(actual.urlApi, exigirHttps) },
            )
            if (conErrores.errorEvaluador != null || conErrores.errorOrganizacion != null || conErrores.errorUrl != null) {
                _estado.value = conErrores
                return
            }
            _estado.update { it.copy(guardando = true) }
            viewModelScope.launch {
                configuracion.guardarConfiguracion(
                    evaluador = actual.evaluador.trim(),
                    organizacion = actual.organizacion.trim(),
                    urlApi = actual.urlApi.trim(),
                    urlEmisor = actual.urlEmisor.trim(),
                    modoEncabezados = true,
                )
                _estado.update { it.copy(guardando = false) }
                _efectos.send(IngresoEfecto.Guardado)
            }
            return
        }

        val sesion = actual.sesionActiva
        if (sesion == null) {
            iniciarSesionOidc()
            return
        }
        val errorUrl = mensajeErrorUrl().takeUnless { ValidadorIngreso.urlValida(actual.urlApi, exigirHttps) }
        val errorEmisor = mensajeErrorEmisor().takeUnless { ValidadorIngreso.urlValida(actual.urlEmisor, exigirHttps) }
        if (errorUrl != null || errorEmisor != null) {
            _estado.update { it.copy(errorUrl = errorUrl, errorEmisor = errorEmisor) }
            return
        }
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            configuracion.guardarConfiguracion(
                evaluador = sesion.usuario,
                organizacion = sesion.organizacion,
                urlApi = actual.urlApi.trim(),
                urlEmisor = actual.urlEmisor.trim(),
                modoEncabezados = false,
            )
            _estado.update { it.copy(guardando = false) }
            _efectos.send(IngresoEfecto.Guardado)
        }
    }

    /** Borra los tokens locales sin tocar las evaluaciones ni la cola de sincronización (AD-44). */
    fun cerrarSesion() {
        viewModelScope.launch {
            identidad.cerrarSesion()
            _estado.update { it.copy(sesionActiva = null, errorOidc = null) }
        }
    }

    private fun mensajeErrorUrl(): String =
        if (exigirHttps) {
            "Escribe una URL HTTPS válida, por ejemplo https://gateway.plataforma.co"
        } else {
            "Escribe una URL válida, por ejemplo http://10.0.2.2:8080"
        }

    private fun mensajeErrorEmisor(): String =
        if (exigirHttps) {
            "Escribe una URL HTTPS válida para el emisor OIDC"
        } else {
            "Escribe una URL válida para el emisor, por ejemplo http://localhost:8180/realms/plataforma"
        }
}
