package com.example.danos.presentacion.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.danos.R
import com.example.danos.presentacion.tema.DanosTheme
import java.io.File

@Composable
fun MiniaturaEvidencia(
    rutaArchivo: String,
    modifier: Modifier = Modifier,
    alBorrar: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = File(rutaArchivo),
            contentDescription = stringResource(R.string.foto_evidencia),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp),
        )
        if (alBorrar != null) {
            IconButton(onClick = alBorrar, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.borrar_foto),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .size(20.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MiniaturaEvidenciaPreview() {
    DanosTheme { MiniaturaEvidencia(rutaArchivo = "/no/existe.jpg", alBorrar = {}) }
}
