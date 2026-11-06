package com.example.danos.acceso

import com.example.danos.dominio.FotoEnCurso
import com.example.danos.dominio.FotoProcesada
import com.example.danos.dominio.ResultadoUbicacion
import com.example.danos.infraestructura.dispositivo.Conectividad
import com.example.danos.infraestructura.dispositivo.ProcesadorFotos
import com.example.danos.infraestructura.dispositivo.ServicioUbicacion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Única puerta de los ViewModel a los sensores y archivos del teléfono. */
interface DispositivoRepositorio {
    val conectado: Flow<Boolean>
    suspend fun obtenerUbicacion(): ResultadoUbicacion
    fun prepararFoto(): FotoEnCurso
    suspend fun procesarFoto(foto: FotoEnCurso): FotoProcesada?
    fun descartarFoto(ruta: String)
}

class DispositivoRepositorioImpl @Inject constructor(
    conectividad: Conectividad,
    private val ubicacion: ServicioUbicacion,
    private val fotos: ProcesadorFotos,
) : DispositivoRepositorio {
    override val conectado: Flow<Boolean> = conectividad.conectado
    override suspend fun obtenerUbicacion(): ResultadoUbicacion = ubicacion.obtener()
    override fun prepararFoto(): FotoEnCurso = fotos.preparar()
    override suspend fun procesarFoto(foto: FotoEnCurso): FotoProcesada? = fotos.procesar(foto)
    override fun descartarFoto(ruta: String) = fotos.descartar(ruta)
}
