package com.example.danos.presentacion.evaluar

import com.example.danos.dominio.Activo
import com.example.danos.dominio.CampoEvaluacion
import com.example.danos.dominio.CriterioNivel
import com.example.danos.dominio.Desastre
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.TipoActivo
import com.example.danos.dominio.Ubicacion
import kotlinx.serialization.Serializable

@Serializable
data class FotoFormulario(
    val id: String,
    val rutaArchivo: String,
    val lat: Double,
    val lon: Double,
    val instanteCapturaMs: Long,
    val sha256: String,
    val tamanoBytes: Long,
    /** Ya está en la base local: no se puede borrar desde el formulario. */
    val guardada: Boolean,
)

/**
 * Lo que escribe el evaluador. Se guarda en SavedStateHandle porque abrir la cámara puede hacer
 * que el sistema mate el proceso, y al volver el formulario debe seguir igual.
 */
@Serializable
data class FormularioEvaluar(
    val instanteCampoMs: Long = 0,
    val activoNuevo: Boolean = false,
    val activoId: String? = null,
    val referenciaNueva: String = "",
    val busqueda: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val precisionM: Double? = null,
    val tipoCodigo: String? = null,
    val nivel: NivelDano? = null,
    val desastreId: String? = null,
    val descripcion: String = "",
    val fotos: List<FotoFormulario> = emptyList(),
    val fotoEnCursoId: String? = null,
    val fotoEnCursoRuta: String? = null,
    val fotoEnCursoUri: String? = null,
    val ubicacionSolicitada: Boolean = false,
) {
    val ubicacion: Ubicacion?
        get() = if (lat != null && lon != null && precisionM != null) Ubicacion(lat, lon, precisionM) else null
}

data class EvaluarEstado(
    val cargando: Boolean = true,
    val esEdicion: Boolean = false,
    val soloLectura: Boolean = false,
    val formulario: FormularioEvaluar = FormularioEvaluar(),
    val tipos: List<TipoActivo> = emptyList(),
    val criterios: List<CriterioNivel> = emptyList(),
    val desastres: List<Desastre> = emptyList(),
    val activos: List<Activo> = emptyList(),
    val obteniendoUbicacion: Boolean = false,
    val errorUbicacion: String? = null,
    val procesandoFoto: Boolean = false,
    val errores: Map<CampoEvaluacion, String> = emptyMap(),
    val errorReferencia: String? = null,
    val guardando: Boolean = false,
) {
    val activoElegido: Activo? get() = activos.firstOrNull { it.id == formulario.activoId }

    val criterioElegido: String?
        get() = criterios
            .firstOrNull { it.tipoCodigo == formulario.tipoCodigo && it.nivel == formulario.nivel }
            ?.criterio

    val activosFiltrados: List<Activo>
        get() {
            val texto = formulario.busqueda.trim()
            if (texto.isEmpty()) return activos.take(MAXIMO_RESULTADOS)
            val nombresTipo = tipos.associate { it.codigo to it.nombre }
            return activos.filter { activo ->
                activo.referencia.contains(texto, ignoreCase = true) ||
                    nombresTipo[activo.tipoCodigo].orEmpty().contains(texto, ignoreCase = true) ||
                    activo.tipoCodigo.contains(texto, ignoreCase = true)
            }.take(MAXIMO_RESULTADOS)
        }

    val precisionBaja: Boolean
        get() = (formulario.precisionM ?: 0.0) > PRECISION_MAXIMA_M

    private companion object {
        const val MAXIMO_RESULTADOS = 20
        const val PRECISION_MAXIMA_M = 50.0
    }
}

sealed interface EvaluarEfecto {
    data class AbrirCamara(val uri: String) : EvaluarEfecto
    data class Mensaje(val texto: String) : EvaluarEfecto
    data object Terminado : EvaluarEfecto
}
