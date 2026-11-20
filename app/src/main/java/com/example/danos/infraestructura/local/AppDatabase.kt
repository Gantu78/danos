package com.example.danos.infraestructura.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migración explícita de versión 1 a 2 (H-71, DA-19).
 * Agrega las columnas aditivas del contrato nuevo sin borrar ni tocar la cola de evaluaciones pendientes (AD-44).
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tipo_activo` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `desastre` ADD COLUMN `estado` TEXT")
        db.execSQL("ALTER TABLE `desastre` ADD COLUMN `codigosMunicipio` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `activo` ADD COLUMN `codigoMunicipio` TEXT")
    }
}

/**
 * Migración de versión 2 a 3: tablas nuevas para el registro de desastres en campo (RF-GD-02) y su catálogo de tipos.
 * Es aditiva: no toca la cola de evaluaciones (AD-44).
 */
val MIGRACION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(SQL_REGISTRO_DESASTRE)
        db.execSQL(SQL_TIPO_DESASTRE)
    }
}

internal const val SQL_REGISTRO_DESASTRE = "CREATE TABLE IF NOT EXISTS `registro_desastre` (`id` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
    "`nombre` TEXT NOT NULL, `descripcion` TEXT NOT NULL, `instanteInicio` INTEGER NOT NULL, `lat` REAL NOT NULL, " +
    "`lon` REAL NOT NULL, `precisionM` REAL NOT NULL, `severidad` TEXT NOT NULL, `responsable` TEXT NOT NULL, " +
    "`radioZonaM` INTEGER NOT NULL, `codigosMunicipio` TEXT NOT NULL, `autor` TEXT NOT NULL, `organizacion` TEXT NOT NULL, " +
    "`instanteRegistro` INTEGER NOT NULL, `estadoEnvio` TEXT NOT NULL, `motivoRechazo` TEXT, `intentos` INTEGER NOT NULL, " +
    "PRIMARY KEY(`id`))"
internal const val SQL_TIPO_DESASTRE = "CREATE TABLE IF NOT EXISTS `tipo_desastre` (`codigo` TEXT NOT NULL, `nombre` TEXT NOT NULL, " +
    "PRIMARY KEY(`codigo`))"

@Database(
    entities = [
        TipoActivoEntity::class,
        CriterioNivelEntity::class,
        DesastreEntity::class,
        ActivoEntity::class,
        EvaluacionEntity::class,
        EvidenciaEntity::class,
        OperacionEntity::class,
        RegistroDesastreEntity::class,
        TipoDesastreEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogoDao(): CatalogoDao
    abstract fun activoDao(): ActivoDao
    abstract fun evaluacionDao(): EvaluacionDao
    abstract fun evidenciaDao(): EvidenciaDao
    abstract fun operacionDao(): OperacionDao
    abstract fun registroDesastreDao(): RegistroDesastreDao
}
