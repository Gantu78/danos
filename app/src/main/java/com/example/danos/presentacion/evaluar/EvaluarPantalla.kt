package com.example.danos.presentacion.evaluar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danos.R
import com.example.danos.dominio.Activo
import com.example.danos.dominio.CampoEvaluacion
import com.example.danos.dominio.NivelDano
import com.example.danos.presentacion.componentes.CampoTexto
import com.example.danos.presentacion.componentes.MiniaturaEvidencia
import com.example.danos.presentacion.componentes.OpcionSelector
import com.example.danos.presentacion.componentes.Selector
import com.example.danos.presentacion.componentes.SelectorNivel
import com.example.danos.presentacion.tema.DanosTheme
import java.util.Locale

@Composable
fun EvaluarRuta(
    alTerminar: () -> Unit,
    viewModel: EvaluarViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    val permisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado ->
        if (resultado.values.any { it }) viewModel.obtenerUbicacion() else viewModel.alNegarPermisoUbicacion()
    }
    val camara = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
        viewModel::alVolverDeCamara,
    )
    val pedirUbicacion = {
        viewModel.alSolicitarUbicacion()
        permisos.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    LaunchedEffect(estado.cargando) {
        if (!estado.cargando && !estado.formulario.ubicacionSolicitada) pedirUbicacion()
    }
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            when (efecto) {
                is EvaluarEfecto.AbrirCamara -> camara.launch(efecto.uri.toUri())
                is EvaluarEfecto.Mensaje -> avisos.showSnackbar(efecto.texto)
                EvaluarEfecto.Terminado -> alTerminar()
            }
        }
    }

    EvaluarPantalla(
        estado = estado,
        avisos = avisos,
        acciones = EvaluarAcciones(
            alVolver = alTerminar,
            alElegirModoNuevo = viewModel::alElegirModoActivoNuevo,
            alCambiarBusqueda = viewModel::alCambiarBusqueda,
            alElegirActivo = viewModel::alElegirActivo,
            alCambiarReferencia = viewModel::alCambiarReferencia,
            alReintentarUbicacion = pedirUbicacion,
            alElegirTipo = viewModel::alElegirTipo,
            alElegirNivel = viewModel::alElegirNivel,
            alElegirDesastre = viewModel::alElegirDesastre,
            alCambiarDescripcion = viewModel::alCambiarDescripcion,
            alTomarFoto = viewModel::tomarFoto,
            alBorrarFoto = viewModel::borrarFoto,
            alGuardar = viewModel::guardar,
        ),
    )
}

data class EvaluarAcciones(
    val alVolver: () -> Unit = {},
    val alElegirModoNuevo: (Boolean) -> Unit = {},
    val alCambiarBusqueda: (String) -> Unit = {},
    val alElegirActivo: (Activo) -> Unit = {},
    val alCambiarReferencia: (String) -> Unit = {},
    val alReintentarUbicacion: () -> Unit = {},
    val alElegirTipo: (String?) -> Unit = {},
    val alElegirNivel: (NivelDano) -> Unit = {},
    val alElegirDesastre: (String?) -> Unit = {},
    val alCambiarDescripcion: (String) -> Unit = {},
    val alTomarFoto: () -> Unit = {},
    val alBorrarFoto: (FotoFormulario) -> Unit = {},
    val alGuardar: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluarPantalla(
    estado: EvaluarEstado,
    avisos: SnackbarHostState,
    acciones: EvaluarAcciones,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (estado.esEdicion) R.string.editar_evaluacion else R.string.nueva_evaluacion))
                },
                navigationIcon = {
                    IconButton(onClick = acciones.alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.volver))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        if (estado.cargando) {
            LinearProgressIndicator(Modifier.padding(relleno).fillMaxWidth())
            return@Scaffold
        }
        val editable = !estado.soloLectura
        Column(
            modifier = Modifier
                .padding(relleno)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (estado.soloLectura) {
                Text(stringResource(R.string.solo_lectura), color = MaterialTheme.colorScheme.error)
            }
            if (estado.tipos.isEmpty()) {
                Text(stringResource(R.string.sin_catalogo), color = MaterialTheme.colorScheme.error)
            }
            SeccionActivo(estado, acciones)
            HorizontalDivider()
            SeccionUbicacion(estado, editable, acciones)
            HorizontalDivider()

            Text(stringResource(R.string.campo_tipo_activo), style = MaterialTheme.typography.titleSmall)
            Selector(
                opciones = estado.tipos.map { OpcionSelector<String?>(it.codigo, it.nombre) },
                seleccionado = estado.formulario.tipoCodigo,
                textoVacio = stringResource(R.string.elegir_tipo),
                alSeleccionar = acciones.alElegirTipo,
                error = estado.errores[CampoEvaluacion.TIPO_ACTIVO],
                habilitado = editable,
            )

            Text(stringResource(R.string.campo_nivel), style = MaterialTheme.typography.titleSmall)
            SelectorNivel(
                seleccionado = estado.formulario.nivel,
                criterio = estado.criterioElegido,
                alSeleccionar = acciones.alElegirNivel,
                error = estado.errores[CampoEvaluacion.NIVEL_DANO],
                habilitado = editable,
            )

            val sinDesastre = stringResource(R.string.sin_desastre_preliminar)
            Text(stringResource(R.string.campo_desastre), style = MaterialTheme.typography.titleSmall)
            Selector(
                opciones = listOf(OpcionSelector<String?>(null, sinDesastre)) +
                    estado.desastres.map { OpcionSelector<String?>(it.id, it.nombre) },
                seleccionado = estado.formulario.desastreId,
                textoVacio = sinDesastre,
                alSeleccionar = acciones.alElegirDesastre,
                habilitado = editable,
            )

            CampoTexto(
                valor = estado.formulario.descripcion,
                alCambiar = acciones.alCambiarDescripcion,
                etiqueta = stringResource(R.string.campo_descripcion),
                error = estado.errores[CampoEvaluacion.DESCRIPCION],
                unaLinea = false,
                habilitado = editable,
            )
            SeccionFotos(estado, editable, acciones)
            Button(
                onClick = acciones.alGuardar,
                enabled = editable && !estado.guardando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.guardar))
            }
        }
    }
}

@Composable
private fun SeccionActivo(estado: EvaluarEstado, acciones: EvaluarAcciones) {
    Text(stringResource(R.string.campo_activo), style = MaterialTheme.typography.titleSmall)
    val formulario = estado.formulario
    if (estado.esEdicion) {
        Text(estado.activoElegido?.referencia ?: formulario.activoId.orEmpty())
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !formulario.activoNuevo,
            onClick = { acciones.alElegirModoNuevo(false) },
            label = { Text(stringResource(R.string.activo_descargado)) },
        )
        FilterChip(
            selected = formulario.activoNuevo,
            onClick = { acciones.alElegirModoNuevo(true) },
            label = { Text(stringResource(R.string.activo_nuevo)) },
        )
    }
    if (formulario.activoNuevo) {
        CampoTexto(
            valor = formulario.referenciaNueva,
            alCambiar = acciones.alCambiarReferencia,
            etiqueta = stringResource(R.string.campo_referencia),
            error = estado.errorReferencia,
        )
        return
    }
    CampoTexto(
        valor = formulario.busqueda,
        alCambiar = acciones.alCambiarBusqueda,
        etiqueta = stringResource(R.string.buscar_activo),
        error = estado.errores[CampoEvaluacion.ACTIVO],
    )
    estado.activoElegido?.let {
        Text(stringResource(R.string.activo_elegido, it.referencia), style = MaterialTheme.typography.bodyMedium)
    }
    estado.activosFiltrados.forEach { activo ->
        Text(
            text = "${activo.referencia} · ${estado.tipos.firstOrNull { it.codigo == activo.tipoCodigo }?.nombre ?: activo.tipoCodigo}",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { acciones.alElegirActivo(activo) }
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun SeccionUbicacion(estado: EvaluarEstado, editable: Boolean, acciones: EvaluarAcciones) {
    Text(stringResource(R.string.campo_ubicacion), style = MaterialTheme.typography.titleSmall)
    val ubicacion = estado.formulario.ubicacion
    when {
        estado.obteniendoUbicacion -> LinearProgressIndicator(Modifier.fillMaxWidth())
        ubicacion != null -> Text(
            String.format(Locale.ROOT, "%.6f, %.6f · ±%.0f m", ubicacion.lat, ubicacion.lon, ubicacion.precisionM),
        )
        else -> Text(stringResource(R.string.sin_ubicacion))
    }
    if (estado.precisionBaja) {
        Text(stringResource(R.string.aviso_precision), color = MaterialTheme.colorScheme.tertiary)
    }
    (estado.errorUbicacion ?: estado.errores[CampoEvaluacion.UBICACION])?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(
        onClick = acciones.alReintentarUbicacion,
        enabled = editable && !estado.obteniendoUbicacion,
    ) {
        Icon(Icons.Filled.MyLocation, contentDescription = null)
        Text(stringResource(R.string.reintentar_ubicacion), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SeccionFotos(estado: EvaluarEstado, editable: Boolean, acciones: EvaluarAcciones) {
    Text(stringResource(R.string.campo_fotos), style = MaterialTheme.typography.titleSmall)
    if (estado.formulario.fotos.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(estado.formulario.fotos, key = { it.id }) { foto ->
                MiniaturaEvidencia(
                    rutaArchivo = foto.rutaArchivo,
                    alBorrar = if (foto.guardada || !editable) null else ({ acciones.alBorrarFoto(foto) }),
                )
            }
        }
    }
    if (estado.procesandoFoto) LinearProgressIndicator(Modifier.fillMaxWidth())
    OutlinedButton(onClick = acciones.alTomarFoto, enabled = editable && !estado.procesandoFoto) {
        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
        Text(stringResource(R.string.tomar_foto), Modifier.padding(start = 8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun EvaluarPantallaPreview() {
    DanosTheme {
        EvaluarPantalla(EvaluarEstado(cargando = false), SnackbarHostState(), EvaluarAcciones())
    }
}
