package com.example.danos.dominio

import java.net.URI
import java.net.URISyntaxException

object ValidadorIngreso {

    fun urlValida(url: String, exigirHttps: Boolean = false): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return false
        }
        val esquema = uri.scheme?.lowercase()
        val esquemaValido = if (exigirHttps) {
            esquema == "https"
        } else {
            esquema == "http" || esquema == "https"
        }
        return esquemaValido && !uri.host.isNullOrBlank()
    }
}
