package com.example.danos.presentacion.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.danos.presentacion.tema.DanosTheme

data class OpcionSelector<T>(val valor: T, val texto: String)

@Composable
fun <T> Selector(
    opciones: List<OpcionSelector<T>>,
    seleccionado: T,
    textoVacio: String,
    alSeleccionar: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    habilitado: Boolean = true,
) {
    var abierto by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            OutlinedButton(
                onClick = { abierto = true },
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(opciones.firstOrNull { it.valor == seleccionado }?.texto ?: textoVacio)
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(opcion.texto) },
                        onClick = {
                            abierto = false
                            alSeleccionar(opcion.valor)
                        },
                    )
                }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectorPreview() {
    DanosTheme {
        Selector(
            opciones = listOf(OpcionSelector("a", "Vivienda")),
            seleccionado = "a",
            textoVacio = "Elegir",
            alSeleccionar = {},
        )
    }
}
