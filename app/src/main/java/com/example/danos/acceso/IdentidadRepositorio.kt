package com.example.danos.acceso

import android.content.Intent
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResultadoToken
import com.example.danos.dominio.SesionIdentidad
import kotlinx.coroutines.flow.Flow

/**
 * Puerto de identidad en la capa de acceso (H-70, DA-15, DT-13).
 * El dominio no conoce Android ni AppAuth; la implementación OIDC vive en infraestructura.
 */
interface IdentidadRepositorio {
    /** Sesión OIDC vigente extraída del token, o null si no se ha iniciado sesión. */
    val sesion: Flow<SesionIdentidad?>

    /** Indica si el refresh token venció o fue revocado y hace falta iniciar sesión para sincronizar. */
    val requiereInicioSesion: Flow<Boolean>

    suspend fun sesionActual(): SesionIdentidad?

    /** Descubre el emisor (/.well-known/openid-configuration) y construye el Intent de Authorization Code + PKCE (S256). */
    suspend fun prepararInicioSesion(urlEmisor: String): Resultado<Intent>

    /** Procesa la redirección OAuth2, intercambia el código por tokens y guarda el estado cifrado. */
    suspend fun procesarRespuestaAutorizacion(datosResultado: Intent?): Resultado<SesionIdentidad>

    /**
     * Obtiene un access token vigente usando AuthState.performActionWithFreshTokens.
     * Si el refresh token venció o fue revocado, marca [requiereInicioSesion] sin tocar la cola local.
     */
    suspend fun obtenerTokenFresco(): ResultadoToken

    /** Marca que hace falta volver a iniciar sesión (por ejemplo ante un 401 del gateway). */
    suspend fun marcarReautenticacionRequerida()

    /** Revoca/borra los tokens locales, pero conserva intacta la cola de evaluaciones pendientes (AD-44). */
    suspend fun cerrarSesion(): Resultado<Unit>
}
