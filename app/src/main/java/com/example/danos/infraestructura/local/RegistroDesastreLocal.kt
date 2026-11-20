package com.example.danos.infraestructura.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.danos.dominio.EstadoEnvioDesastre
import com.example.danos.dominio.RegistroDesastre
import com.example.danos.dominio.SeveridadDesastre
import com.example.danos.dominio.TipoDesastre
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Registro de desastre hecho en campo y su estado en la cola local (AD-44). Tabla nueva de la versión 3. */
@Entity(tableName = "registro_desastre")
data class RegistroDesastreEntity(
    @PrimaryKey val id: String,
    val tipo: String,
    val nombre: String,
    val descripcion: String,
    val instanteInicio: Instant,
    val lat: Double,
    val lon: Double,
    val precisionM: Double,
    val severidad: String,
    val responsable: String,
    val radioZonaM: Int,
    val codigosMunicipio: String,
    val autor: String,
    val organizacion: String,
    val instanteRegistro: Instant,
    val estadoEnvio: String,
    val motivoRechazo: String?,
    val intentos: Int,
)

/** Catálogo de tipos de desastre descargado del servidor (RF-GD-04). */
@Entity(tableName = "tipo_desastre")
data class TipoDesastreEntity(
    @PrimaryKey val codigo: String,
    val nombre: String,
)

@Dao
interface RegistroDesastreDao {
    @Query("SELECT * FROM registro_desastre ORDER BY instanteRegistro DESC")
    fun observarTodos(): Flow<List<RegistroDesastreEntity>>

    @Query("SELECT COUNT(*) FROM registro_desastre WHERE estadoEnvio = 'PENDIENTE'")
    fun observarPendientes(): Flow<Int>

    @Query("SELECT * FROM registro_desastre WHERE estadoEnvio = 'PENDIENTE' ORDER BY instanteRegistro")
    suspend fun pendientes(): List<RegistroDesastreEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(registro: RegistroDesastreEntity)

    @Query("UPDATE registro_desastre SET intentos = intentos + 1 WHERE id = :id")
    suspend fun incrementarIntentos(id: String)

    @Query("UPDATE registro_desastre SET estadoEnvio = :estado, motivoRechazo = :motivo WHERE id = :id")
    suspend fun actualizarEstado(id: String, estado: String, motivo: String?)

    @Query("SELECT * FROM tipo_desastre ORDER BY nombre")
    fun observarTipos(): Flow<List<TipoDesastreEntity>>

    @Query("DELETE FROM tipo_desastre")
    suspend fun borrarTipos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTipos(tipos: List<TipoDesastreEntity>)

    @Transaction
    suspend fun reemplazarTipos(tipos: List<TipoDesastreEntity>) {
        borrarTipos()
        insertarTipos(tipos)
    }
}

fun RegistroDesastre.aEntidad(intentos: Int = 0) = RegistroDesastreEntity(
    id = id,
    tipo = tipo,
    nombre = nombre,
    descripcion = descripcion,
    instanteInicio = instanteInicio,
    lat = lat,
    lon = lon,
    precisionM = precisionM,
    severidad = severidad.name,
    responsable = responsable,
    radioZonaM = radioZonaM,
    codigosMunicipio = codigosMunicipio.joinToString(","),
    autor = autor,
    organizacion = organizacion,
    instanteRegistro = instanteRegistro,
    estadoEnvio = estadoEnvio.name,
    motivoRechazo = motivoRechazo,
    intentos = intentos,
)

fun RegistroDesastreEntity.aDominio() = RegistroDesastre(
    id = id,
    tipo = tipo,
    nombre = nombre,
    descripcion = descripcion,
    instanteInicio = instanteInicio,
    lat = lat,
    lon = lon,
    precisionM = precisionM,
    severidad = SeveridadDesastre.entries.firstOrNull { it.name == severidad } ?: SeveridadDesastre.NIVEL_1,
    responsable = responsable,
    radioZonaM = radioZonaM,
    codigosMunicipio = codigosMunicipio.split(',').filter { it.isNotBlank() },
    autor = autor,
    organizacion = organizacion,
    instanteRegistro = instanteRegistro,
    estadoEnvio = EstadoEnvioDesastre.entries.firstOrNull { it.name == estadoEnvio } ?: EstadoEnvioDesastre.PENDIENTE,
    motivoRechazo = motivoRechazo,
)

fun TipoDesastreEntity.aDominio() = TipoDesastre(codigo, nombre)
