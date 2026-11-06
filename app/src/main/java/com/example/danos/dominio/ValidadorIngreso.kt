package com.example.danos.dominio

import java.net.URI
import java.net.URISyntaxException

object ValidadorIngreso {

    fun urlValida(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return false
        }
        val esquema = uri.scheme?.lowercase()
        return (esquema == "http" || esquema == "https") && !uri.host.isNullOrBlank()
    }
}
