package com.example.danos.presentacion.desastre

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danos.dominio.CampoDesastre
import com.example.danos.dominio.RADIOS_ZONA_M
import com.example.danos.dominio.SeveridadDesastre
import com.example.danos.presentacion.componentes.CampoTexto
import com.example.danos.presentacion.componentes.OpcionSelector
import com.example.danos.presentacion.componentes.Selector
import java.util.Locale

@Composable
fun RegistrarDesastreRuta(
    alTerminar: () -> Unit,
    viewModel: RegistrarDesastreViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.obtenerUbicacion()
    }
    val pedirUbicacion = {
        permisos.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) { pedirUbicacion() }
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            when (efecto) {
                RegistrarDesastreEfecto.Guardado -> alTerminar()
                is RegistrarDesastreEfecto.Error -> avisos.showSnackbar(efecto.mensaje)
            }
        }
    }
    RegistrarDesastrePantalla(
        estado = estado,
        avisos = avisos,
        alVolver = alTerminar,
        alUbicar = pedirUbicacion,
        alCambiarTipo = viewModel::alCambiarTipo,
        alCambiarNombre = viewModel::alCambiarNombre,
        alCambiarDescripcion = viewModel::alCambiarDescripcion,
        alCambiarSeveridad = viewModel::alCambiarSeveridad,
        alCambiarResponsable = viewModel::alCambiarResponsable,
        alCambiarHoras = viewModel::alCambiarHoras,
        alCambiarRadio = viewModel::alCambiarRadio,
        alCambiarMunicipios = viewModel::alCambiarMunicipios,
        alGuardar = viewModel::guardar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrarDesastrePantalla(
    estado: RegistrarDesastreEstado,
    avisos: SnackbarHostState,
    alVolver: () -> Unit,
    alUbicar: () -> Unit,
    alCambiarTipo: (String) -> Unit,
    alCambiarNombre: (String) -> Unit,
    alCambiarDescripcion: (String) -> Unit,
    alCambiarSeveridad: (SeveridadDesastre) -> Unit,
    alCambiarResponsable: (String) -> Unit,
    alCambiarHoras: (Int) -> Unit,
    alCambiarRadio: (Int?) -> Unit,
    alCambiarMunicipios: (String) -> Unit,
    alGuardar: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar desastre") },
                navigationIcon = {
                    IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (estado.tienePermiso == false) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Su usuario no tiene el rol organismo o autoridad. El registro quedará en el teléfono y la " +
                            "plataforma lo rechazará al sincronizar (RF-GD-02).",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("Caracterización del evento", style = MaterialTheme.typography.titleMedium)
            Selector(
                opciones = estado.tipos.map { OpcionSelector<String?>(it.codigo, it.nombre) },
                seleccionado = estado.tipo,
                textoVacio = "Tipo de evento",
                alSeleccionar = { codigo -> codigo?.let(alCambiarTipo) },
                error = estado.errores[CampoDesastre.TIPO],
            )
            CampoTexto(estado.nombre, alCambiarNombre, "Nombre del evento", error = estado.errores[CampoDesastre.NOMBRE])
            CampoTexto(
                estado.descripcion, alCambiarDescripcion, "Descripción (qué pasó, alcance observado)",
                error = estado.errores[CampoDesastre.DESCRIPCION], unaLinea = false,
            )
            Selector(
                opciones = SeveridadDesastre.entries.map { OpcionSelector<SeveridadDesastre?>(it, it.etiqueta) },
                seleccionado = estado.severidad,
                textoVacio = "Nivel del evento (RN-3)",
                alSeleccionar = { nivel -> nivel?.let(alCambiarSeveridad) },
                error = estado.errores[CampoDesastre.SEVERIDAD],
            )
            Selector(
                opciones = HORAS_INICIO.map { OpcionSelector(it, if (it == 0) "Inició ahora" else "Inició hace $it h") },
                seleccionado = estado.horasDesdeInicio,
                textoVacio = "Hora de inicio",
                alSeleccionar = alCambiarHoras,
                error = estado.errores[CampoDesastre.INICIO],
            )
            CampoTexto(
                estado.responsable, alCambiarResponsable, "Organismo responsable de la emergencia",
                error = estado.errores[CampoDesastre.RESPONSABLE],
            )

            Text("Ubicación y zona afectada", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = alUbicar, enabled = !estado.obteniendoUbicacion) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null)
                    Text(" Capturar ubicación")
                }
                if (estado.obteniendoUbicacion) CircularProgressIndicator(Modifier.padding(4.dp))
            }
            val textoUbicacion = if (estado.lat != null && estado.lon != null) {
                String.format(Locale.ROOT, "%.5f, %.5f (±%.0f m)", estado.lat, estado.lon, estado.precisionM ?: 0.0)
            } else {
                "Sin ubicación"
            }
            Text(textoUbicacion, style = MaterialTheme.typography.bodyMedium)
            (estado.errores[CampoDesastre.UBICACION] ?: estado.errorUbicacion)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Selector(
                opciones = listOf(OpcionSelector<Int?>(null, "Sin radio (solo municipios)")) +
                    RADIOS_ZONA_M.map { OpcionSelector<Int?>(it, if (it >= 1000) "Radio de ${it / 1000} km" else "Radio de $it m") },
                seleccionado = estado.radioZonaM,
                textoVacio = "Radio de la zona afectada",
                alSeleccionar = alCambiarRadio,
                error = estado.errores[CampoDesastre.ZONA],
            )
            CampoTexto(
                estado.municipios, alCambiarMunicipios, "Municipios DIVIPOLA (opcional, p. ej. 63001, 63130)",
                error = estado.errores[CampoDesastre.MUNICIPIOS],
            )
            Text(
                "Registra: ${estado.autor.ifBlank { "—" }} · ${estado.organizacion.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = alGuardar, enabled = !estado.guardando, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar y enviar cuando haya conexión")
            }
        }
    }
}
