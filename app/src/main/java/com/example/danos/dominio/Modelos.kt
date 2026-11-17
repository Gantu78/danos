package com.example.danos.dominio

import java.time.Instant

/** Nivel de afectación en la escala 1 (sin daño) a 5 (destrucción total). */
enum class NivelDano(val numero: Int, val etiqueta: String) {
    SIN_DANO(1, "Sin daño"),
    LEVE(2, "Leve"),
    MODERADO(3, "Moderado"),
    GRAVE(4, "Grave"),
    DESTRUCCION_TOTAL(5, "Destrucción total"),
    ;

    val etiquetaConEscala: String get() = "$numero · $etiqueta"
}

enum class EstadoEvaluacion(val etiqueta: String) {
    PRELIMINAR("Preliminar"),
    REGISTRADA("Registrada"),
    EN_CONFLICTO("En conflicto"),
    VALIDADA("Validada"),
    RECHAZADA("Rechazada"),
}

enum class EstadoSync(val etiqueta: String) {
    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    INVALIDA("Inválida"),
}

enum class TipoOperacion { CREAR, ACTUALIZAR }

data class Evidencia(
    val id: String,
    val evaluacionId: String,
    val rutaArchivo: String,
    val lat: Double,
    val lon: Double,
    val instanteCaptura: Instant,
    val sha256: String,
    val tamanoBytes: Long,
    val subida: Boolean = false,
)

/** [nivelDano] admite null solo para que el formulario lo reporte como error de validación. */
data class Evaluacion(
    val id: String,
    val activoId: String,
    val desastreId: String?,
    val instanteCampo: Instant,
    val evaluador: String,
    val organizacion: String,
    val lat: Double,
    val lon: Double,
    val precisionM: Double,
    val tipoActivoCodigo: String,
    val nivelDano: NivelDano?,
    val descripcion: String,
    val evidencias: List<Evidencia> = emptyList(),
    val estadoEvaluacion: EstadoEvaluacion = EstadoEvaluacion.PRELIMINAR,
    val estadoSync: EstadoSync = EstadoSync.PENDIENTE,
    val motivoInvalida: String? = null,
)

data class Activo(
    val id: String,
    val tipoCodigo: String,
    val lat: Double,
    val lon: Double,
    val referencia: String,
    val versionDescargada: Int?,
    val codigoMunicipio: String? = null,
) {
    /** Muestra el municipio DIVIPOLA junto a la referencia cuando el servidor lo envía (DA-19). */
    val referenciaConMunicipio: String
        get() = if (codigoMunicipio.isNullOrBlank()) referencia else "$referencia ($codigoMunicipio)"
}

data class TipoActivo(
    val codigo: String,
    val nombre: String,
    val version: Int = 1,
)

data class CriterioNivel(val tipoCodigo: String, val nivel: NivelDano, val criterio: String)

data class Desastre(
    val id: String,
    val nombre: String,
    val tipo: String,
    val estado: String? = null,
    val codigosMunicipio: List<String> = emptyList(),
)

/** Una evaluación tal como se muestra en la lista, con los nombres ya resueltos. */
data class ResumenEvaluacion(
    val evaluacion: Evaluacion,
    val tipoActivoNombre: String,
    val referenciaActivo: String,
    val fotosPendientes: Int,
)

const val MENSAJE_INICIAR_SESION = "Inicie sesión para sincronizar"
const val EMISOR_OIDC_POR_DEFECTO = "http://localhost:8180/realms/plataforma"

/** Identidad extraída del token OIDC según el contrato del realm "plataforma" (DA-15). */
data class SesionIdentidad(
    val usuario: String,
    val organizacion: String,
    val roles: Set<String> = emptySet(),
    val ambito: String? = null,
    val territorio: String? = null,
)

sealed interface ResultadoToken {
    data class Exito(val accessToken: String, val sesion: SesionIdentidad) : ResultadoToken
    data object ModoEncabezados : ResultadoToken
    data class SesionExpirada(val mensaje: String = MENSAJE_INICIAR_SESION) : ResultadoToken
    data class ErrorRed(val mensaje: String) : ResultadoToken
}

data class Configuracion(
    val evaluador: String,
    val organizacion: String,
    val urlApi: String,
    val idDispositivo: String,
    val urlEmisor: String = EMISOR_OIDC_POR_DEFECTO,
    val modoEncabezados: Boolean = false,
)

data class Ubicacion(val lat: Double, val lon: Double, val precisionM: Double)

sealed interface ResultadoUbicacion {
    data class Exito(val ubicacion: Ubicacion) : ResultadoUbicacion
    data object SinPermiso : ResultadoUbicacion
    data object NoDisponible : ResultadoUbicacion
}

/** Estado inicial que le corresponde a una evaluación según tenga o no desastre. */
fun estadoInicial(desastreId: String?): EstadoEvaluacion =
    if (desastreId.isNullOrBlank()) EstadoEvaluacion.PRELIMINAR else EstadoEvaluacion.REGISTRADA
