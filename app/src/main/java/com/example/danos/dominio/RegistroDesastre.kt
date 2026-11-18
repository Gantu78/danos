package com.example.danos.dominio

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Nivel de clasificación del evento (SRS RN-3): cuatro niveles, el 4 es el más grave. */
enum class SeveridadDesastre(val etiqueta: String) {
    NIVEL_1("Nivel 1 · Local"),
    NIVEL_2("Nivel 2 · Municipal"),
    NIVEL_3("Nivel 3 · Departamental"),
    NIVEL_4("Nivel 4 · Nacional"),
}

/** Estado del registro en la cola local (AD-44): el registro nunca se pierde por falta de red. */
enum class EstadoEnvioDesastre(val etiqueta: String) {
    PENDIENTE("Pendiente de envío"),
    ENVIADO("Registrado en la plataforma"),
    RECHAZADO("Rechazado"),
}

data class TipoDesastre(val codigo: String, val nombre: String)

/** Tipos de RN-1. Se usan mientras la app no haya descargado el catálogo vigente del servidor (RF-GD-04). */
val TIPOS_DESASTRE_RN1: List<TipoDesastre> = listOf(
    TipoDesastre("DESLIZAMIENTO", "Deslizamiento"),
    TipoDesastre("ERUPCION_VOLCANICA", "Erupción volcánica"),
    TipoDesastre("HURACAN", "Huracán"),
    TipoDesastre("INCENDIO", "Incendio"),
    TipoDesastre("INUNDACION", "Inundación"),
    TipoDesastre("TERREMOTO", "Terremoto"),
    TipoDesastre("OTRO", "Otro"),
)

/** Roles del realm que pueden registrar un evento (SRS RF-GD-02: organismo de respuesta o autoridad). */
val ROLES_REGISTRO_DESASTRE: Set<String> = setOf("organismo", "autoridad")

fun puedeRegistrarDesastres(roles: Set<String>): Boolean = roles.any { it in ROLES_REGISTRO_DESASTRE }

/**
 * Registro y caracterización de un desastre hecho en campo (RF-GD-02, RF-GD-03, RF-GD-05, RF-GD-08).
 * La zona es un polígono de [radioZonaM] alrededor del punto, con fuente EVALUACION_CAMPO, más los municipios
 * DIVIPOLA que el usuario conozca. El [id] lo genera el teléfono para que el reenvío no duplique el evento.
 */
data class RegistroDesastre(
    val id: String,
    val tipo: String,
    val nombre: String,
    val descripcion: String,
    val instanteInicio: Instant,
    val lat: Double,
    val lon: Double,
    val precisionM: Double,
    val severidad: SeveridadDesastre,
    val responsable: String,
    val radioZonaM: Int,
    val codigosMunicipio: List<String>,
    val autor: String,
    val organizacion: String,
    val instanteRegistro: Instant,
    val estadoEnvio: EstadoEnvioDesastre = EstadoEnvioDesastre.PENDIENTE,
    val motivoRechazo: String? = null,
)

enum class CampoDesastre { TIPO, NOMBRE, DESCRIPCION, SEVERIDAD, RESPONSABLE, UBICACION, ZONA, MUNICIPIOS, INICIO }

/** Radios de zona que ofrece el formulario, en metros. */
val RADIOS_ZONA_M: List<Int> = listOf(100, 500, 1_000, 5_000)

object ValidadorRegistroDesastre {
    private val DIVIPOLA = Regex("^\\d{5}$")

    /** Mismas reglas del dominio del servidor (Desastre.Registrar y VersionZona.Validar), para fallar antes de encolar. */
    fun validar(
        tipo: String?,
        nombre: String,
        descripcion: String,
        severidad: SeveridadDesastre?,
        responsable: String,
        lat: Double?,
        lon: Double?,
        radioZonaM: Int?,
        codigosMunicipio: List<String>,
        instanteInicio: Instant,
        ahora: Instant,
    ): Map<CampoDesastre, String> = buildMap {
        if (tipo.isNullOrBlank()) put(CampoDesastre.TIPO, "Elija el tipo de evento")
        if (nombre.isBlank()) put(CampoDesastre.NOMBRE, "El nombre es obligatorio")
        if (descripcion.isBlank()) put(CampoDesastre.DESCRIPCION, "La descripción es obligatoria")
        if (severidad == null) put(CampoDesastre.SEVERIDAD, "Elija el nivel del evento")
        if (responsable.isBlank()) put(CampoDesastre.RESPONSABLE, "El responsable de la emergencia es obligatorio")
        if (lat == null || lon == null) put(CampoDesastre.UBICACION, "Capture la ubicación del evento")
        if (radioZonaM == null && codigosMunicipio.isEmpty()) {
            put(CampoDesastre.ZONA, "La zona necesita un radio o al menos un municipio")
        }
        codigosMunicipio.firstOrNull { !DIVIPOLA.matches(it) }?.let {
            put(CampoDesastre.MUNICIPIOS, "'$it' no es un código DIVIPOLA de 5 dígitos")
        }
        if (instanteInicio.isAfter(ahora.plus(Duration.ofMinutes(5)))) {
            put(CampoDesastre.INICIO, "La hora de inicio no puede ser futura")
        }
    }

    fun separarMunicipios(texto: String): List<String> =
        texto.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/**
 * Anillo cerrado de un polígono regular de [lados] vértices y radio [radioM] alrededor del punto, en orden
 * [lon, lat] como exige GeoJSON (WGS 84). La aproximación esférica basta para radios de pocos kilómetros.
 */
fun poligonoAlrededor(lat: Double, lon: Double, radioM: Int, lados: Int = 16): List<List<Double>> {
    require(lados >= 3) { "Un polígono necesita al menos 3 lados" }
    val radioTierraM = 6_371_000.0
    val dLat = radioM / radioTierraM * (180 / PI)
    val dLon = dLat / cos(lat * PI / 180)
    val anillo = (0 until lados).map { i ->
        val angulo = 2 * PI * i / lados
        listOf(lon + dLon * cos(angulo), lat + dLat * sin(angulo))
    }
    return anillo + listOf(anillo.first())
}
