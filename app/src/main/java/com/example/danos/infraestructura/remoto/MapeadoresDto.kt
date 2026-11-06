package com.example.danos.infraestructura.remoto

import com.example.danos.dominio.Activo
import com.example.danos.dominio.CriterioNivel
import com.example.danos.dominio.Desastre
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.TipoActivo
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.format.DateTimeFormatter

fun instanteDesdeIso(texto: String): Instant =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(texto, Instant::from)

fun TipoActivoDto.aDominio() = TipoActivo(codigo, nombre)

/** Descarta los criterios con un nivel que esta versión de la app no conoce. */
fun TipoActivoDto.criteriosADominio(): List<CriterioNivel> = criterios.mapNotNull { dto ->
    NivelDano.entries.firstOrNull { it.name == dto.nivel }?.let { CriterioNivel(codigo, it, dto.criterio) }
}

fun DesastreDto.aDominio() = Desastre(id, nombre, tipo)

fun ActivoDto.aDominio() = Activo(id, tipoCodigo, lat, lon, referencia, version)

fun Activo.aActivoNuevoDto() = ActivoNuevoDto(id, tipoCodigo, lat, lon, referencia)

fun Evidencia.aDto() = EvidenciaDto(id, lat, lon, instanteCaptura.toString(), sha256, tamanoBytes)

fun Evaluacion.aDto(activoNuevo: Activo?): EvaluacionDto = EvaluacionDto(
    id = id,
    activoId = activoId,
    activoNuevo = activoNuevo?.aActivoNuevoDto(),
    desastreId = desastreId,
    instanteCampo = instanteCampo.toString(),
    evaluador = evaluador,
    organizacion = organizacion,
    lat = lat,
    lon = lon,
    precisionM = precisionM,
    tipoActivoCodigo = tipoActivoCodigo,
    nivelDano = requireNotNull(nivelDano) { "Una evaluación validada siempre tiene nivel" }.name,
    descripcion = descripcion,
    evidencias = evidencias.map { it.aDto() },
)

/** Instantánea que se encola: activoNuevo solo viaja si el activo aún no tiene versión. */
fun Evaluacion.aPayloadJson(activo: Activo?): String =
    JsonDanos.encodeToString(aDto(activo?.takeIf { it.versionDescargada == null }))
