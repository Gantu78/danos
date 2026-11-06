package com.example.danos.infraestructura.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.TipoOperacion
import java.time.Instant

@Entity(tableName = "tipo_activo")
data class TipoActivoEntity(@PrimaryKey val codigo: String, val nombre: String)

@Entity(tableName = "criterio_nivel", primaryKeys = ["tipoCodigo", "nivel"])
data class CriterioNivelEntity(val tipoCodigo: String, val nivel: NivelDano, val criterio: String)

@Entity(tableName = "desastre")
data class DesastreEntity(@PrimaryKey val id: String, val nombre: String, val tipo: String)

@Entity(tableName = "activo")
data class ActivoEntity(
    @PrimaryKey val id: String,
    val tipoCodigo: String,
    val lat: Double,
    val lon: Double,
    val referencia: String,
    val versionDescargada: Int?,
)

/**
 * Sin claves foráneas hacia activo ni catálogo: la descarga los reemplaza y nunca debe arrastrar
 * una evaluación. [conocidaPorServidor] es solo local: indica si el servidor ya registró esta
 * evaluación, para decidir el tipo de la operación al corregir una INVALIDA.
 */
@Entity(tableName = "evaluacion", indices = [Index("activoId")])
data class EvaluacionEntity(
    @PrimaryKey val id: String,
    val activoId: String,
    val desastreId: String?,
    val instanteCampo: Instant,
    val evaluador: String,
    val organizacion: String,
    val lat: Double,
    val lon: Double,
    val precisionM: Double,
    val tipoActivoCodigo: String,
    val nivelDano: NivelDano,
    val descripcion: String,
    val estadoEvaluacion: EstadoEvaluacion,
    val estadoSync: EstadoSync,
    val motivoInvalida: String?,
    val conocidaPorServidor: Boolean = false,
)

/** La clave foránea no tiene CASCADE a propósito: nada puede borrar una evidencia en cadena. */
@Entity(
    tableName = "evidencia",
    foreignKeys = [
        ForeignKey(
            entity = EvaluacionEntity::class,
            parentColumns = ["id"],
            childColumns = ["evaluacionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("evaluacionId")],
)
data class EvidenciaEntity(
    @PrimaryKey val id: String,
    val evaluacionId: String,
    val rutaArchivo: String,
    val lat: Double,
    val lon: Double,
    val instanteCaptura: Instant,
    val sha256: String,
    val tamanoBytes: Long,
    val subida: Boolean,
)

@Entity(tableName = "operacion", indices = [Index("evaluacionId"), Index("activoId"), Index("creada")])
data class OperacionEntity(
    @PrimaryKey val idOperacion: String,
    val tipo: TipoOperacion,
    val evaluacionId: String,
    val activoId: String,
    val versionBaseActivo: Int?,
    val payloadJson: String,
    val creada: Instant,
    val intentos: Int = 0,
    val ultimoError: String? = null,
)

data class FilaEvaluacion(
    @Embedded val evaluacion: EvaluacionEntity,
    @Relation(parentColumn = "id", entityColumn = "evaluacionId")
    val evidencias: List<EvidenciaEntity>,
    @Relation(parentColumn = "activoId", entityColumn = "id")
    val activo: ActivoEntity?,
    @Relation(parentColumn = "tipoActivoCodigo", entityColumn = "codigo")
    val tipoActivo: TipoActivoEntity?,
)
