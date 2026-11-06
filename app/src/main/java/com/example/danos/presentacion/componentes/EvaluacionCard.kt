package com.example.danos.presentacion.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.danos.R
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.ResumenEvaluacion
import com.example.danos.presentacion.tema.DanosTheme
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvaluacionCard(
    resumen: ResumenEvaluacion,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val evaluacion = resumen.evaluacion
    Card(onClick = alTocar, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${resumen.tipoActivoNombre} · ${evaluacion.nivelDano?.etiquetaConEscala.orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(resumen.referenciaActivo, style = MaterialTheme.typography.bodyMedium)
            Text(evaluacion.instanteCampo.textoLocal(), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipEstado(evaluacion.estadoEvaluacion.etiqueta, evaluacion.estadoEvaluacion.tono())
                ChipEstado(evaluacion.estadoSync.etiqueta, evaluacion.estadoSync.tono())
            }
            if (resumen.fotosPendientes > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.fotos_pendientes,
                        resumen.fotosPendientes,
                        resumen.fotosPendientes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (evaluacion.estadoSync == EstadoSync.INVALIDA && evaluacion.motivoInvalida != null) {
                Text(
                    text = evaluacion.motivoInvalida,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EvaluacionCardPreview() {
    DanosTheme {
        EvaluacionCard(
            resumen = ResumenEvaluacion(
                evaluacion = Evaluacion(
                    id = "1",
                    activoId = "a",
                    desastreId = null,
                    instanteCampo = Instant.EPOCH,
                    evaluador = "Ana",
                    organizacion = "Defensa Civil",
                    lat = 4.6,
                    lon = -74.1,
                    precisionM = 8.0,
                    tipoActivoCodigo = "VIV",
                    nivelDano = NivelDano.GRAVE,
                    descripcion = "Colapso parcial",
                    estadoEvaluacion = EstadoEvaluacion.PRELIMINAR,
                    estadoSync = EstadoSync.INVALIDA,
                    motivoInvalida = "Falta información",
                ),
                tipoActivoNombre = "Vivienda",
                referenciaActivo = "CL 10 # 5-20",
                fotosPendientes = 2,
            ),
            alTocar = {},
        )
    }
}
