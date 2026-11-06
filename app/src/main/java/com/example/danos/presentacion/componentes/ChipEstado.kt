package com.example.danos.presentacion.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.presentacion.tema.AvisoClaro
import com.example.danos.presentacion.tema.AvisoOscuro
import com.example.danos.presentacion.tema.DanosTheme
import com.example.danos.presentacion.tema.ExitoClaro
import com.example.danos.presentacion.tema.ExitoOscuro

enum class TonoChip { NEUTRO, EXITO, AVISO, ERROR }

fun EstadoEvaluacion.tono(): TonoChip = when (this) {
    EstadoEvaluacion.PRELIMINAR, EstadoEvaluacion.REGISTRADA -> TonoChip.NEUTRO
    EstadoEvaluacion.VALIDADA -> TonoChip.EXITO
    EstadoEvaluacion.EN_CONFLICTO -> TonoChip.AVISO
    EstadoEvaluacion.RECHAZADA -> TonoChip.ERROR
}

fun EstadoSync.tono(): TonoChip = when (this) {
    EstadoSync.PENDIENTE -> TonoChip.AVISO
    EstadoSync.CONFIRMADA -> TonoChip.EXITO
    EstadoSync.INVALIDA -> TonoChip.ERROR
}

@Composable
fun ChipEstado(texto: String, tono: TonoChip, modifier: Modifier = Modifier) {
    val oscuro = isSystemInDarkTheme()
    val color: Color = when (tono) {
        TonoChip.NEUTRO -> MaterialTheme.colorScheme.onSurfaceVariant
        TonoChip.EXITO -> if (oscuro) ExitoOscuro else ExitoClaro
        TonoChip.AVISO -> if (oscuro) AvisoOscuro else AvisoClaro
        TonoChip.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChipEstadoPreview() {
    DanosTheme { ChipEstado("En conflicto", TonoChip.AVISO) }
}
