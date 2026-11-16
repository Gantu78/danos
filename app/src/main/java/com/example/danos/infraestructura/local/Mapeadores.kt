package com.example.danos.infraestructura.local

import com.example.danos.dominio.Activo
import com.example.danos.dominio.CriterioNivel
import com.example.danos.dominio.Desastre
import com.example.danos.dominio.Evaluacion
import com.example.danos.dominio.Evidencia
import com.example.danos.dominio.ResumenEvaluacion
import com.example.danos.dominio.TipoActivo

fun TipoActivoEntity.aDominio() = TipoActivo(codigo, nombre, version)
fun TipoActivo.aEntidad() = TipoActivoEntity(codigo, nombre, version)
fun CriterioNivelEntity.aDominio() = CriterioNivel(tipoCodigo, nivel, criterio)
fun CriterioNivel.aEntidad() = CriterioNivelEntity(tipoCodigo, nivel, criterio)

fun DesastreEntity.aDominio() = Desastre(
    id = id,
    nombre = nombre,
    tipo = tipo,
    estado = estado,
    codigosMunicipio = codigosMunicipio.split(',').map { it.trim() }.filter { it.isNotEmpty() },
)

fun Desastre.aEntidad() = DesastreEntity(
    id = id,
    nombre = nombre,
    tipo = tipo,
    estado = estado,
    codigosMunicipio = codigosMunicipio.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
)

fun ActivoEntity.aDominio() = Activo(id, tipoCodigo, lat, lon, referencia, versionDescargada, codigoMunicipio)
fun Activo.aEntidad() = ActivoEntity(id, tipoCodigo, lat, lon, referencia, versionDescargada, codigoMunicipio)

fun EvidenciaEntity.aDominio() =
    Evidencia(id, evaluacionId, rutaArchivo, lat, lon, instanteCaptura, sha256, tamanoBytes, subida)

fun Evidencia.aEntidad() =
    EvidenciaEntity(id, evaluacionId, rutaArchivo, lat, lon, instanteCaptura, sha256, tamanoBytes, subida)

fun EvaluacionEntity.aDominio(evidencias: List<EvidenciaEntity>) = Evaluacion(
    id = id,
    activoId = activoId,
    desastreId = desastreId,
    instanteCampo = instanteCampo,
    evaluador = evaluador,
    organizacion = organizacion,
    lat = lat,
    lon = lon,
    precisionM = precisionM,
    tipoActivoCodigo = tipoActivoCodigo,
    nivelDano = nivelDano,
    descripcion = descripcion,
    evidencias = evidencias.map { it.aDominio() }.sortedBy { it.instanteCaptura },
    estadoEvaluacion = estadoEvaluacion,
    estadoSync = estadoSync,
    motivoInvalida = motivoInvalida,
)

fun FilaEvaluacion.aResumen() = ResumenEvaluacion(
    evaluacion = evaluacion.aDominio(evidencias),
    tipoActivoNombre = tipoActivo?.nombre ?: evaluacion.tipoActivoCodigo,
    referenciaActivo = activo?.aDominio()?.referenciaConMunicipio ?: evaluacion.activoId,
    fotosPendientes = evidencias.count { !it.subida },
)
