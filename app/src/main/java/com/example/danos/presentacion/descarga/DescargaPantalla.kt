package com.example.danos.presentacion.descarga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danos.R
import com.example.danos.presentacion.componentes.OpcionSelector
import com.example.danos.presentacion.componentes.Selector
import com.example.danos.presentacion.componentes.textoLocal
import com.example.danos.presentacion.tema.DanosTheme

@Composable
fun DescargaRuta(
    alVolver: () -> Unit,
    viewModel: DescargaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            when (efecto) {
                is DescargaEfecto.Mensaje -> avisos.showSnackbar(efecto.texto)
            }
        }
    }
    DescargaPantalla(
        estado = estado,
        avisos = avisos,
        alElegirDesastre = viewModel::alElegirDesastre,
        alDescargar = viewModel::descargar,
        alVolver = alVolver,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescargaPantalla(
    estado: DescargaEstado,
    avisos: SnackbarHostState,
    alElegirDesastre: (String?) -> Unit,
    alDescargar: () -> Unit,
    alVolver: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.descarga_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.volver))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sinDesastre = stringResource(R.string.sin_desastre)
            Text(stringResource(R.string.elegir_desastre), style = MaterialTheme.typography.titleSmall)
            Selector(
                opciones = listOf(OpcionSelector<String?>(null, sinDesastre)) +
                    estado.desastres.map { OpcionSelector<String?>(it.id, "${it.nombre} (${it.tipo})") },
                seleccionado = estado.desastreId,
                textoVacio = sinDesastre,
                alSeleccionar = alElegirDesastre,
            )
            Button(
                onClick = alDescargar,
                enabled = !estado.descargando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.descargar))
            }
            if (estado.descargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(
                    R.string.ultima_descarga,
                    estado.ultimaDescarga?.textoLocal() ?: stringResource(R.string.nunca),
                ),
            )
            Text(stringResource(R.string.numero_activos, estado.numActivos))
            Text(stringResource(R.string.numero_tipos, estado.numTipos))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DescargaPantallaPreview() {
    DanosTheme {
        DescargaPantalla(DescargaEstado(numActivos = 12, numTipos = 3), SnackbarHostState(), {}, {}, {})
    }
}
