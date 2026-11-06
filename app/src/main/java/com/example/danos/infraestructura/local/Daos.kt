package com.example.danos.infraestructura.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogoDao {
    @Query("SELECT * FROM tipo_activo ORDER BY nombre")
    fun observarTipos(): Flow<List<TipoActivoEntity>>

    @Query("SELECT * FROM criterio_nivel")
    fun observarCriterios(): Flow<List<CriterioNivelEntity>>

    @Query("SELECT * FROM desastre ORDER BY nombre")
    fun observarDesastres(): Flow<List<DesastreEntity>>

    @Query("DELETE FROM criterio_nivel")
    suspend fun borrarCriterios()

    @Query("DELETE FROM tipo_activo")
    suspend fun borrarTipos()

    @Query("DELETE FROM desastre")
    suspend fun borrarDesastres()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTipos(tipos: List<TipoActivoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCriterios(criterios: List<CriterioNivelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDesastres(desastres: List<DesastreEntity>)
}

@Dao
interface ActivoDao {
    @Query("SELECT * FROM activo ORDER BY referencia")
    fun observarTodos(): Flow<List<ActivoEntity>>

    @Query("SELECT * FROM activo WHERE id = :id")
    suspend fun obtener(id: String): ActivoEntity?

    @Upsert
    suspend fun guardar(activo: ActivoEntity)

    @Upsert
    suspend fun guardarTodos(activos: List<ActivoEntity>)

    /** Ids que la descarga no puede sobrescribir: creados en campo o con operaciones pendientes. */
    @Query(
        """SELECT id FROM activo
           WHERE versionDescargada IS NULL
              OR id IN (SELECT activoId FROM operacion)""",
    )
    suspend fun idsProtegidos(): List<String>

    /**
     * Borra los activos descargados que ya no hacen falta. Conserva los creados en campo, los que
     * tienen operaciones pendientes y los que usa alguna evaluación local, porque mostrarla y
     * editarla necesita su referencia y su versión.
     */
    @Query(
        """DELETE FROM activo
           WHERE versionDescargada IS NOT NULL
             AND id NOT IN (SELECT activoId FROM operacion)
             AND id NOT IN (SELECT activoId FROM evaluacion)""",
    )
    suspend fun borrarDescargadosReemplazables()

    @Query("UPDATE activo SET versionDescargada = :version WHERE id = :id")
    suspend fun actualizarVersion(id: String, version: Int)
}

@Dao
interface EvaluacionDao {
    @Transaction
    @Query("SELECT * FROM evaluacion ORDER BY instanteCampo DESC")
    fun observarLista(): Flow<List<FilaEvaluacion>>

    @Transaction
    @Query("SELECT * FROM evaluacion WHERE id = :id")
    suspend fun obtenerFila(id: String): FilaEvaluacion?

    @Query("SELECT * FROM evaluacion WHERE id = :id")
    suspend fun obtener(id: String): EvaluacionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(evaluacion: EvaluacionEntity)

    @Upsert
    suspend fun guardar(evaluacion: EvaluacionEntity)

    @Query(
        """UPDATE evaluacion SET estadoSync = :estadoSync, estadoEvaluacion = :estadoEvaluacion,
           motivoInvalida = :motivo, conocidaPorServidor = :conocida WHERE id = :id""",
    )
    suspend fun actualizarEstados(
        id: String,
        estadoSync: EstadoSync,
        estadoEvaluacion: EstadoEvaluacion,
        motivo: String?,
        conocida: Boolean,
    )
}

@Dao
interface EvidenciaDao {
    @Upsert
    suspend fun guardarTodas(evidencias: List<EvidenciaEntity>)

    @Query("SELECT * FROM evidencia WHERE evaluacionId = :evaluacionId")
    suspend fun deEvaluacion(evaluacionId: String): List<EvidenciaEntity>

    @Query(
        """SELECT e.* FROM evidencia e
           INNER JOIN evaluacion v ON v.id = e.evaluacionId
           WHERE e.subida = 0 AND v.estadoSync = 'CONFIRMADA'
           ORDER BY e.instanteCaptura""",
    )
    suspend fun pendientesDeSubir(): List<EvidenciaEntity>

    @Query("UPDATE evidencia SET subida = 1 WHERE id = :id")
    suspend fun marcarSubida(id: String)
}

@Dao
interface OperacionDao {
    @Query("SELECT COUNT(*) FROM operacion")
    fun observarConteo(): Flow<Int>

    @Query("SELECT ultimoError FROM operacion WHERE ultimoError IS NOT NULL ORDER BY creada, rowid LIMIT 1")
    fun observarUltimoError(): Flow<String?>

    @Query("SELECT COUNT(*) FROM operacion")
    suspend fun contar(): Int

    @Query("SELECT * FROM operacion ORDER BY creada, rowid LIMIT :limite")
    suspend fun primeras(limite: Int): List<OperacionEntity>

    @Query("SELECT * FROM operacion WHERE evaluacionId = :evaluacionId ORDER BY creada, rowid")
    suspend fun deEvaluacion(evaluacionId: String): List<OperacionEntity>

    @Query("SELECT * FROM operacion WHERE idOperacion = :id")
    suspend fun obtener(id: String): OperacionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(operacion: OperacionEntity)

    /** La condición sobre intentos es la última barrera del invariante de inmutabilidad. */
    @Query("UPDATE operacion SET payloadJson = :payload WHERE idOperacion = :id AND intentos = 0")
    suspend fun reemplazarPayloadSiNoIntentada(id: String, payload: String): Int

    @Query("UPDATE operacion SET intentos = intentos + 1 WHERE idOperacion IN (:ids)")
    suspend fun incrementarIntentos(ids: List<String>)

    @Query("UPDATE operacion SET ultimoError = :error WHERE idOperacion IN (:ids)")
    suspend fun guardarError(ids: List<String>, error: String)

    @Query("DELETE FROM operacion WHERE idOperacion = :id")
    suspend fun borrar(id: String)
}
