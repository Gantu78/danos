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

@Database(
    entities = [
        TipoActivoEntity::class,
        CriterioNivelEntity::class,
        DesastreEntity::class,
        ActivoEntity::class,
        EvaluacionEntity::class,
        EvidenciaEntity::class,
        OperacionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogoDao(): CatalogoDao
    abstract fun activoDao(): ActivoDao
    abstract fun evaluacionDao(): EvaluacionDao
    abstract fun evidenciaDao(): EvidenciaDao
    abstract fun operacionDao(): OperacionDao
}
