package com.example.danos.presentacion.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.danos.presentacion.tema.DanosTheme

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    unaLinea: Boolean = true,
    habilitado: Boolean = true,
    teclado: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = error?.let { mensaje -> { Text(mensaje) } },
        singleLine = unaLinea,
        minLines = if (unaLinea) 1 else 3,
        enabled = habilitado,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun CampoTextoPreview() {
    DanosTheme {
        CampoTexto(valor = "", alCambiar = {}, etiqueta = "Evaluador", error = "Campo obligatorio")
    }
}
