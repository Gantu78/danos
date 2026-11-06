package com.example.danos.presentacion.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EsquemaOscuro = darkColorScheme(
    primary = Pizarra80,
    secondary = Gris80,
    tertiary = Ocre80,
)

private val EsquemaClaro = lightColorScheme(
    primary = Pizarra40,
    secondary = Gris40,
    tertiary = Ocre40,
)

@Composable
fun DanosTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        typography = Typography,
        content = content,
    )
}
