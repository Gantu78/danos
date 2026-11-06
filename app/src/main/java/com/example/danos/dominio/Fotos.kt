package com.example.danos.dominio

/** Archivo reservado para la cámara, aún sin procesar. */
data class FotoEnCurso(val id: String, val rutaArchivo: String, val uri: String)

data class FotoProcesada(val sha256: String, val tamanoBytes: Long)
