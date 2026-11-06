package com.example.danos.infraestructura.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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
