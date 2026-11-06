package com.example.danos.presentacion.lista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danos.R
import com.example.danos.presentacion.componentes.EvaluacionCard
import com.example.danos.presentacion.componentes.IndicadorConexion
import com.example.danos.presentacion.tema.DanosTheme

@Composable
fun ListaRuta(
    alNuevaEvaluacion: () -> Unit,
    alEditar: (String) -> Unit,
    alAbrirDescarga: () -> Unit,
    alAbrirIngreso: () -> Unit,
    viewModel: ListaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    val textoSolicitada = stringResource(R.string.sincronizacion_solicitada)
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            when (efecto) {
                ListaEfecto.SincronizacionSolicitada -> avisos.showSnackbar(textoSolicitada)
            }
        }
    }
    ListaPantalla(
        estado = estado,
        avisos = avisos,
        alSincronizar = viewModel::sincronizar,
        alNuevaEvaluacion = alNuevaEvaluacion,
        alEditar = alEditar,
        alAbrirDescarga = alAbrirDescarga,
        alAbrirIngreso = alAbrirIngreso,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaPantalla(
    estado: ListaEstado,
    avisos: SnackbarHostState,
    alSincronizar: () -> Unit,
    alNuevaEvaluacion: () -> Unit,
    alEditar: (String) -> Unit,
    alAbrirDescarga: () -> Unit,
    alAbrirIngreso: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lista_titulo)) },
                actions = {
                    IndicadorConexion(estado.conectado, Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = alSincronizar) {
                        BadgedBox(
                            badge = {
                                if (estado.operacionesPendientes > 0) {
                                    Badge { Text(estado.operacionesPendientes.toString()) }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Sync, stringResource(R.string.sincronizar))
                        }
                    }
                    IconButton(onClick = alAbrirDescarga) {
                        Icon(Icons.Filled.CloudDownload, stringResource(R.string.descarga_titulo))
                    }
                    IconButton(onClick = alAbrirIngreso) {
                        Icon(Icons.Filled.Person, stringResource(R.string.ingreso_titulo))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alNuevaEvaluacion,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.nueva_evaluacion)) },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            estado.errorSincronizacion?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (estado.evaluaciones.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lista_vacia))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(estado.evaluaciones, key = { it.evaluacion.id }) { resumen ->
                        EvaluacionCard(resumen = resumen, alTocar = { alEditar(resumen.evaluacion.id) })
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListaPantallaPreview() {
    DanosTheme {
        ListaPantalla(ListaEstado(operacionesPendientes = 3), SnackbarHostState(), {}, {}, {}, {}, {})
    }
}
