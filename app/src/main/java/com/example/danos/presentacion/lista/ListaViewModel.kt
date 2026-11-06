package com.example.danos.presentacion.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danos.acceso.DispositivoRepositorio
import com.example.danos.acceso.EvaluacionRepositorio
import com.example.danos.acceso.SincronizacionRepositorio
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
class ListaViewModel @Inject constructor(
    evaluaciones: EvaluacionRepositorio,
    dispositivo: DispositivoRepositorio,
    private val sincronizacion: SincronizacionRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(ListaEstado())
    val estado: StateFlow<ListaEstado> = _estado.asStateFlow()

    private val _efectos = Channel<ListaEfecto>(Channel.BUFFERED)
    val efectos: Flow<ListaEfecto> = _efectos.receiveAsFlow()

    init {
        combine(
            evaluaciones.observarResumenes(),
            dispositivo.conectado,
            sincronizacion.observarOperacionesPendientes(),
            sincronizacion.observarUltimoError(),
        ) { lista, conectado, pendientes, error ->
            _estado.update {
                it.copy(
                    evaluaciones = lista,
                    conectado = conectado,
                    operacionesPendientes = pendientes,
                    errorSincronizacion = error,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun sincronizar() {
        sincronizacion.sincronizar()
        viewModelScope.launch { _efectos.send(ListaEfecto.SincronizacionSolicitada) }
    }
}
