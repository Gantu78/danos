package com.example.danos.presentacion.desastre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.danos.acceso.DesastreRepositorio
import com.example.danos.acceso.SincronizacionRepositorio
import com.example.danos.dominio.EstadoEnvioDesastre
import com.example.danos.dominio.RegistroDesastre
import com.example.danos.presentacion.componentes.ChipEstado
import com.example.danos.presentacion.componentes.TonoChip
import com.example.danos.presentacion.componentes.textoLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DesastresViewModel @Inject constructor(
    desastres: DesastreRepositorio,
    private val sincronizacion: SincronizacionRepositorio,
) : ViewModel() {
    val registros: StateFlow<List<RegistroDesastre>> =
        desastres.observarRegistros().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sincronizar() = sincronizacion.sincronizar()
}

fun EstadoEnvioDesastre.tono(): TonoChip = when (this) {
    EstadoEnvioDesastre.PENDIENTE -> TonoChip.AVISO
    EstadoEnvioDesastre.ENVIADO -> TonoChip.EXITO
    EstadoEnvioDesastre.RECHAZADO -> TonoChip.ERROR
}

@Composable
fun DesastresRuta(
    alVolver: () -> Unit,
    alRegistrar: () -> Unit,
    viewModel: DesastresViewModel = hiltViewModel(),
) {
    val registros by viewModel.registros.collectAsStateWithLifecycle()
    DesastresPantalla(registros, alVolver, alRegistrar, viewModel::sincronizar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesastresPantalla(
    registros: List<RegistroDesastre>,
    alVolver: () -> Unit,
    alRegistrar: () -> Unit,
    alSincronizar: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Desastres registrados en campo") },
                navigationIcon = {
                    IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = alSincronizar) { Icon(Icons.Filled.Sync, "Sincronizar") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alRegistrar,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Registrar desastre") },
            )
        },
    ) { relleno ->
        if (registros.isEmpty()) {
            Box(Modifier.padding(relleno).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no hay desastres registrados desde este teléfono")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(relleno),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(registros, key = { it.id }) { registro -> TarjetaDesastre(registro) }
            }
        }
    }
}

@Composable
private fun TarjetaDesastre(registro: RegistroDesastre) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(registro.nombre, style = MaterialTheme.typography.titleMedium)
            Text("${registro.tipo} · ${registro.severidad.etiqueta}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Inicio: ${registro.instanteInicio.textoLocal()} · Responsable: ${registro.responsable}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipEstado(registro.estadoEnvio.etiqueta, registro.estadoEnvio.tono())
            }
            registro.motivoRechazo?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
