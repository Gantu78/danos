package com.example.danos.presentacion.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danos.acceso.ConfiguracionRepositorio
import com.example.danos.acceso.SincronizacionRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decide la pantalla de arranque y dispara la sincronización al abrir la app. */
@HiltViewModel
class InicioViewModel @Inject constructor(
    configuracion: ConfiguracionRepositorio,
    sincronizacion: SincronizacionRepositorio,
) : ViewModel() {

    private val _configurada = MutableStateFlow<Boolean?>(null)
    val configurada: StateFlow<Boolean?> = _configurada.asStateFlow()

    init {
        viewModelScope.launch {
            _configurada.value = configuracion.actual() != null
            sincronizacion.sincronizarSiHayPendientes()
        }
    }
}
