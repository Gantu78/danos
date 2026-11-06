package com.example.danos.presentacion.componentes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.danos.R
import com.example.danos.presentacion.tema.DanosTheme

@Composable
fun IndicadorConexion(conectado: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (conectado) Icons.Filled.Cloud else Icons.Filled.CloudOff,
        contentDescription = stringResource(if (conectado) R.string.con_conexion else R.string.sin_conexion),
        tint = if (conectado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun IndicadorConexionPreview() {
    DanosTheme { IndicadorConexion(conectado = false) }
}
