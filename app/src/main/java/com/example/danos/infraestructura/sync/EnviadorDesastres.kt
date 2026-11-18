package com.example.danos.infraestructura.sync

import com.example.danos.acceso.IdentidadRepositorio
import com.example.danos.dominio.EstadoEnvioDesastre
import com.example.danos.dominio.RegistroDesastre
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.TipoError
import com.example.danos.dominio.poligonoAlrededor
import com.example.danos.infraestructura.local.RegistroDesastreDao
import com.example.danos.infraestructura.local.TipoDesastreEntity
import com.example.danos.infraestructura.local.aDominio
import com.example.danos.infraestructura.remoto.DesastresApi
import com.example.danos.infraestructura.remoto.ErrorApiDto
import com.example.danos.infraestructura.remoto.GeometriaDto
import com.example.danos.infraestructura.remoto.JsonDanos
import com.example.danos.infraestructura.remoto.RegistroDesastreDto
import com.example.danos.infraestructura.remoto.ZonaRegistroDto
import com.example.danos.infraestructura.remoto.llamarApi
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Envía los registros de desastre pendientes y actualiza el catálogo de tipos (RF-GD-02, RF-GD-04).
 * Sigue las reglas de la cola de evaluaciones (AD-44): sin red, 5xx o 401 se reintenta después sin perder nada;
 * un 4xx de validación o de permiso marca el registro como rechazado con el motivo del servidor y no se insiste.
 */
class EnviadorDesastres @Inject constructor(
    private val dao: RegistroDesastreDao,
    private val api: DesastresApi,
    private val identidad: IdentidadRepositorio? = null,
) {

    /** Devuelve true si la cola quedó resuelta (enviados o rechazados) y false si conviene reintentar. */
    suspend fun enviarPendientes(): Boolean {
        for (entidad in dao.pendientes()) {
            dao.incrementarIntentos(entidad.id)
            when (enviar(entidad.aDominio())) {
                Desenlace.ENVIADO -> dao.actualizarEstado(entidad.id, EstadoEnvioDesastre.ENVIADO.name, null)
                is Desenlace.Rechazado -> Unit
                Desenlace.REINTENTAR -> return false
            }
        }
        return true
    }

    /** Descarga el catálogo vigente. Un fallo no es grave: el formulario usa los tipos de RN-1 mientras tanto. */
    suspend fun actualizarTipos(): Boolean =
        when (val respuesta = llamarApi { api.tiposDesastre() }) {
            is Resultado.Exito -> {
                val vigentes = respuesta.valor.filter { it.vigente }.map { TipoDesastreEntity(it.codigo, it.nombre) }
                if (vigentes.isNotEmpty()) dao.reemplazarTipos(vigentes)
                true
            }
            is Resultado.Error -> false
        }

    private suspend fun enviar(registro: RegistroDesastre): Desenlace {
        val respuesta = llamarApi { api.registrar(aDto(registro)) }
        val http = when (respuesta) {
            is Resultado.Error -> return if (respuesta.tipo == TipoError.NO_PERMITIDO) {
                identidad?.marcarReautenticacionRequerida()
                Desenlace.REINTENTAR
            } else {
                Desenlace.REINTENTAR
            }
            is Resultado.Exito -> respuesta.valor
        }
        return when {
            http.isSuccessful -> Desenlace.ENVIADO
            http.code() == 401 -> {
                identidad?.marcarReautenticacionRequerida()
                Desenlace.REINTENTAR
            }
            http.code() in 400..499 -> {
                val motivo = motivoDe(http.code(), http.errorBody()?.string())
                dao.actualizarEstado(registro.id, EstadoEnvioDesastre.RECHAZADO.name, motivo)
                Desenlace.Rechazado(motivo)
            }
            else -> Desenlace.REINTENTAR
        }
    }

    private fun motivoDe(codigo: Int, cuerpo: String?): String {
        if (codigo == 403) return "Su usuario no tiene el rol organismo o autoridad para registrar desastres"
        val error = cuerpo?.let { runCatching { JsonDanos.decodeFromString(ErrorApiDto.serializer(), it) }.getOrNull() }
        return error?.mensaje ?: error?.error ?: "El servidor rechazó el registro (HTTP $codigo)"
    }

    private sealed interface Desenlace {
        data object ENVIADO : Desenlace
        data object REINTENTAR : Desenlace
        data class Rechazado(val motivo: String) : Desenlace
    }

    companion object {
        fun aDto(r: RegistroDesastre) = RegistroDesastreDto(
            id = r.id,
            tipo = r.tipo,
            nombre = r.nombre.trim(),
            descripcion = r.descripcion.trim(),
            instanteInicio = r.instanteInicio.truncatedTo(ChronoUnit.SECONDS).toString(),
            lat = r.lat,
            lon = r.lon,
            severidad = r.severidad.name,
            responsable = r.responsable.trim(),
            zona = ZonaRegistroDto(
                geometria = if (r.radioZonaM > 0) {
                    GeometriaDto(coordinates = listOf(poligonoAlrededor(r.lat, r.lon, r.radioZonaM)))
                } else {
                    null
                },
                codigosMunicipio = r.codigosMunicipio,
            ),
        )
    }
}
