package com.example.danos.presentacion.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danos.R
import com.example.danos.presentacion.componentes.CampoTexto
import com.example.danos.presentacion.componentes.ChipEstado
import com.example.danos.presentacion.componentes.TonoChip
import com.example.danos.presentacion.tema.DanosTheme

@Composable
fun IngresoRuta(
    alGuardar: () -> Unit,
    viewModel: IngresoViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            when (efecto) {
                IngresoEfecto.Guardado -> alGuardar()
            }
        }
    }
    IngresoPantalla(
        estado = estado,
        alCambiarEvaluador = viewModel::alCambiarEvaluador,
        alCambiarOrganizacion = viewModel::alCambiarOrganizacion,
        alCambiarUrl = viewModel::alCambiarUrl,
        alGuardar = viewModel::guardar,
        alProbarConexion = viewModel::probarConexion,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngresoPantalla(
    estado: IngresoEstado,
    alCambiarEvaluador: (String) -> Unit,
    alCambiarOrganizacion: (String) -> Unit,
    alCambiarUrl: (String) -> Unit,
    alGuardar: () -> Unit,
    alProbarConexion: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ingreso_titulo)) }) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CampoTexto(
                valor = estado.evaluador,
                alCambiar = alCambiarEvaluador,
                etiqueta = stringResource(R.string.campo_evaluador),
                error = estado.errorEvaluador,
            )
            CampoTexto(
                valor = estado.organizacion,
                alCambiar = alCambiarOrganizacion,
                etiqueta = stringResource(R.string.campo_organizacion),
                error = estado.errorOrganizacion,
            )
            CampoTexto(
                valor = estado.urlApi,
                alCambiar = alCambiarUrl,
                etiqueta = stringResource(R.string.campo_url_api),
                error = estado.errorUrl,
                teclado = KeyboardType.Uri,
            )
            Text(
                text = stringResource(R.string.ayuda_url_api),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = alProbarConexion,
                enabled = !estado.probando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.NetworkCheck, contentDescription = null)
                Text(stringResource(R.string.probar_conexion), Modifier.padding(start = 8.dp))
            }
            if (estado.probando) LinearProgressIndicator(Modifier.fillMaxWidth())
            estado.pruebaConexion?.let { prueba ->
                ChipEstado(
                    texto = prueba.mensaje,
                    tono = if (prueba.exitosa) TonoChip.EXITO else TonoChip.ERROR,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = alGuardar,
                enabled = !estado.guardando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.guardar))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IngresoPantallaPreview() {
    DanosTheme {
        IngresoPantalla(
            estado = IngresoEstado(
                urlApi = "http://192.168.1.10:5080",
                pruebaConexion = PruebaConexion(exitosa = true, mensaje = "Conexión correcta con el servidor"),
            ),
            alCambiarEvaluador = {},
            alCambiarOrganizacion = {},
            alCambiarUrl = {},
            alGuardar = {},
        )
    }
}
