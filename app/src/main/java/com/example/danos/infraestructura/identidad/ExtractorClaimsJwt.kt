package com.example.danos.infraestructura.identidad

import com.example.danos.dominio.SesionIdentidad
import com.example.danos.infraestructura.remoto.JsonDanos
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Extrae los claims del contrato de identidad del realm "plataforma" (DA-15):
 * - preferred_username -> usuario
 * - realm_access.roles -> roles
 * - organizacion, ambito, territorio -> claims de primer nivel
 */
object ExtractorClaimsJwt {

    fun extraerSesion(accessToken: String?, idToken: String? = null): SesionIdentidad? {
        val claimsAcceso = accessToken?.let(::decodificarPayload)
        val claimsId = idToken?.let(::decodificarPayload)
        if (claimsAcceso == null && claimsId == null) return null

        val usuario = leerTexto(claimsAcceso, "preferred_username")
            ?: leerTexto(claimsId, "preferred_username")
            ?: leerTexto(claimsAcceso, "sub")
            ?: leerTexto(claimsId, "sub")
            ?: return null

        val organizacion = leerTexto(claimsAcceso, "organizacion")
            ?: leerTexto(claimsId, "organizacion")
            ?: ""

        val ambito = leerTexto(claimsAcceso, "ambito")
            ?: leerTexto(claimsId, "ambito")

        val territorio = leerTexto(claimsAcceso, "territorio")
            ?: leerTexto(claimsId, "territorio")

        val roles = extraerRoles(claimsAcceso).ifEmpty { extraerRoles(claimsId) }

        return SesionIdentidad(
            usuario = usuario,
            organizacion = organizacion,
            roles = roles,
            ambito = ambito,
            territorio = territorio,
        )
    }

    internal fun decodificarPayload(jwt: String): JsonObject? = runCatching {
        val partes = jwt.split('.')
        if (partes.size < 2) return null
        val bytes = Base64.getUrlDecoder().decode(partes[1])
        val texto = String(bytes, StandardCharsets.UTF_8)
        JsonDanos.parseToJsonElement(texto).jsonObject
    }.getOrNull()

    private fun leerTexto(objeto: JsonObject?, clave: String): String? =
        objeto?.get(clave)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun extraerRoles(objeto: JsonObject?): Set<String> = runCatching {
        val realmAccess = objeto?.get("realm_access")?.jsonObject ?: return emptySet()
        val arreglo: JsonArray = realmAccess["roles"]?.jsonArray ?: return emptySet()
        arreglo.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.toSet()
    }.getOrDefault(emptySet())
}
