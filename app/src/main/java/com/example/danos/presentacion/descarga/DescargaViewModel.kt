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
            catalogo.observarMarcadorDesde(),
        ) { desastres, tipos, activos, ultima, desde ->
            _estado.update { actual ->
                val desastreValido = actual.desastreId?.takeIf { id -> desastres.any { it.id == id } }
                val municipios = desastres.firstOrNull { it.id == desastreValido }?.codigosMunicipio.orEmpty()
                val zonaValida = actual.zona?.takeIf { it in municipios }
                actual.copy(
                    desastres = desastres,
                    desastreId = desastreValido,
                    zona = zonaValida,
                    numTipos = tipos.size,
                    numActivos = activos.size,
                    ultimaDescarga = ultima,
                    marcadorDesde = desde,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun alElegirDesastre(id: String?) = _estado.update { actual ->
        val municipios = actual.desastres.firstOrNull { it.id == id }?.codigosMunicipio.orEmpty()
        actual.copy(
            desastreId = id,
            zona = actual.zona?.takeIf { it in municipios },
        )
    }

    fun alElegirZona(codigoMunicipio: String?) = _estado.update { it.copy(zona = codigoMunicipio) }

    fun descargar() = ejecutarDescarga(forzarCompleta = false)

    fun descargarCompleta() = ejecutarDescarga(forzarCompleta = true)

    private fun ejecutarDescarga(forzarCompleta: Boolean) {
        val actual = _estado.value
        _estado.update { it.copy(descargando = true) }
        viewModelScope.launch {
            val resultado = catalogo.descargar(
                desastreId = actual.desastreId,
                zona = actual.zona,
                forzarCompleta = forzarCompleta,
            )
            val texto = when (resultado) {
                is Resultado.Exito -> if (forzarCompleta) "Descarga completa realizada" else "Descarga actualizada"
                is Resultado.Error -> "${resultado.mensaje}. Se conserva lo ya descargado."
            }
            _estado.update { it.copy(descargando = false) }
            _efectos.send(DescargaEfecto.Mensaje(texto))
        }
    }
}
