package com.example.danos.presentacion.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.danos.dominio.NivelDano
import com.example.danos.presentacion.tema.DanosTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorNivel(
    seleccionado: NivelDano?,
    criterio: String?,
    alSeleccionar: (NivelDano) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    habilitado: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NivelDano.entries.forEach { nivel ->
                FilterChip(
                    selected = nivel == seleccionado,
                    onClick = { alSeleccionar(nivel) },
                    label = { Text(nivel.etiquetaConEscala) },
                    enabled = habilitado,
                )
            }
        }
        criterio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectorNivelPreview() {
    DanosTheme {
        SelectorNivel(
            seleccionado = NivelDano.MODERADO,
            criterio = "Grietas visibles en muros portantes",
            alSeleccionar = {},
        )
    }
}
