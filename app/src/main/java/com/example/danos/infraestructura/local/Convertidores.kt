package com.example.danos.infraestructura.local

import androidx.room.TypeConverter
import com.example.danos.dominio.EstadoEvaluacion
import com.example.danos.dominio.EstadoSync
import com.example.danos.dominio.NivelDano
import com.example.danos.dominio.TipoOperacion
import java.time.Instant

class Convertidores {
    @TypeConverter fun instanteALong(valor: Instant): Long = valor.toEpochMilli()
    @TypeConverter fun longAInstante(valor: Long): Instant = Instant.ofEpochMilli(valor)

    @TypeConverter fun nivelATexto(valor: NivelDano): String = valor.name
    @TypeConverter fun textoANivel(valor: String): NivelDano = NivelDano.valueOf(valor)

    @TypeConverter fun estadoEvaluacionATexto(valor: EstadoEvaluacion): String = valor.name
    @TypeConverter fun textoAEstadoEvaluacion(valor: String): EstadoEvaluacion = EstadoEvaluacion.valueOf(valor)

    @TypeConverter fun estadoSyncATexto(valor: EstadoSync): String = valor.name
    @TypeConverter fun textoAEstadoSync(valor: String): EstadoSync = EstadoSync.valueOf(valor)

    @TypeConverter fun tipoOperacionATexto(valor: TipoOperacion): String = valor.name
    @TypeConverter fun textoATipoOperacion(valor: String): TipoOperacion = TipoOperacion.valueOf(valor)
}
