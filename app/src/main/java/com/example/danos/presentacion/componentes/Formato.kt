package com.example.danos.presentacion.componentes

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_INSTANTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun Instant.textoLocal(): String = FORMATO_INSTANTE.format(atZone(ZoneId.systemDefault()))
