package com.example.danos.presentacion.descarga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danos.acceso.CatalogoRepositorio
import com.example.danos.dominio.Resultado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DescargaViewModel @Inject constructor(
    private val catalogo: CatalogoRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(DescargaEstado())
    val estado: StateFlow<DescargaEstado> = _estado.asStateFlow()

    private val _efectos = Channel<DescargaEfecto>(Channel.BUFFERED)
    val efectos: Flow<DescargaEfecto> = _efectos.receiveAsFlow()

    init {
        combine(
            catalogo.observarDesastres(),
            catalogo.observarTipos(),
            catalogo.observarActivos(),
            catalogo.observarUltimaDescarga(),
        ) { desastres, tipos, activos, ultima ->
            _estado.update {
                it.copy(
                    desastres = desastres,
                    numTipos = tipos.size,
                    numActivos = activos.size,
                    ultimaDescarga = ultima,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun alElegirDesastre(id: String?) = _estado.update { it.copy(desastreId = id) }

    fun descargar() {
        _estado.update { it.copy(descargando = true) }
        viewModelScope.launch {
            val texto = when (val resultado = catalogo.descargar(_estado.value.desastreId)) {
                is Resultado.Exito -> "Descarga completa"
                is Resultado.Error -> "${resultado.mensaje}. Se conserva lo ya descargado."
            }
            _estado.update { it.copy(descargando = false) }
            _efectos.send(DescargaEfecto.Mensaje(texto))
        }
    }
}
