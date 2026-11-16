package com.example.danos.infraestructura.identidad

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.danos.BuildConfig
import com.example.danos.acceso.IdentidadRepositorio
import com.example.danos.dominio.MENSAJE_INICIAR_SESION
import com.example.danos.dominio.Resultado
import com.example.danos.dominio.ResultadoToken
import com.example.danos.dominio.SesionIdentidad
import com.example.danos.dominio.TipoError
import com.example.danos.infraestructura.preferencias.PreferenciasLocales
import com.example.danos.infraestructura.sync.ProgramadorSincronizacion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.ResponseTypeValues
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class IdentidadRepositorioImpl @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val preferencias: PreferenciasLocales,
    private val cifrador: CifradorDatos,
    private val programador: ProgramadorSincronizacion,
) : IdentidadRepositorio {

    private val mutexRenovacion = Mutex()
    private val constructorConexion = ConexionOidcBuilder(BuildConfig.DEBUG)

    override val sesion: Flow<SesionIdentidad?> = preferencias.authStateCifrado.map { cifrado ->
        leerAuthState(cifrado)?.let { estado ->
            ExtractorClaimsJwt.extraerSesion(estado.accessToken, estado.idToken)
        }
    }

    override val requiereInicioSesion: Flow<Boolean> = preferencias.requiereReautenticacion

    override suspend fun sesionActual(): SesionIdentidad? = sesion.first()

    override suspend fun prepararInicioSesion(urlEmisor: String): Resultado<Intent> {
        val base = urlEmisor.trim().trimEnd('/')
        if (base.isEmpty()) {
            return Resultado.Error(TipoError.CLIENTE, "La URL del emisor OIDC es obligatoria")
        }
        val uriDescubrimiento = Uri.parse("$base$RUTA_DESCUBRIMIENTO")
        return try {
            val configServicio = descubrirEmisor(uriDescubrimiento)
            val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
            val codeChallenge = CodeVerifierUtil.deriveCodeVerifierChallenge(codeVerifier)
            val solicitud = AuthorizationRequest.Builder(
                configServicio,
                CLIENTE_ID,
                ResponseTypeValues.CODE,
                Uri.parse(URI_REDIRECCION),
            )
                .setScope(SCOPES_OIDC)
                .setCodeVerifier(
                    codeVerifier,
                    codeChallenge,
                    AuthorizationRequest.CODE_CHALLENGE_METHOD_S256,
                )
                .build()

            val servicio = crearServicioAppAuth()
            try {
                Resultado.Exito(servicio.getAuthorizationRequestIntent(solicitud))
            } finally {
                servicio.dispose()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthorizationException) {
            Resultado.Error(
                TipoError.SIN_RED,
                "No se pudo descubrir el emisor OIDC en $base (${e.errorDescription ?: e.error ?: "sin respuesta"}).",
            )
        } catch (_: Exception) {
            Resultado.Error(TipoError.CLIENTE, "No se pudo iniciar el flujo OIDC con el emisor $base")
        }
    }

    override suspend fun procesarRespuestaAutorizacion(datosResultado: Intent?): Resultado<SesionIdentidad> {
        if (datosResultado == null) {
            return Resultado.Error(TipoError.CLIENTE, "Inicio de sesión cancelado")
        }
        val respuesta = AuthorizationResponse.fromIntent(datosResultado)
        val excepcion = AuthorizationException.fromIntent(datosResultado)
        if (respuesta == null) {
            val detalle = excepcion?.errorDescription ?: excepcion?.error ?: "Autorización cancelada"
            return Resultado.Error(TipoError.CLIENTE, "No se completó el inicio de sesión: $detalle")
        }

        val estadoAuth = AuthState(respuesta, excepcion)
        val servicio = crearServicioAppAuth()
        return try {
            val (respuestaToken, errorToken) = intercambiarCodigo(servicio, respuesta)
            estadoAuth.update(respuestaToken, errorToken)
            if (respuestaToken == null || errorToken != null) {
                val motivo = errorToken?.errorDescription ?: errorToken?.error ?: "Error al obtener tokens"
                return Resultado.Error(TipoError.CLIENTE, "El emisor rechazó el canje de código: $motivo")
            }

            val sesionNueva = ExtractorClaimsJwt.extraerSesion(estadoAuth.accessToken, estadoAuth.idToken)
                ?: return Resultado.Error(TipoError.SERVIDOR, "El token recibido no contiene preferred_username")

            guardarAuthState(estadoAuth, sesionNueva)
            preferencias.guardarRequiereReautenticacion(false)
            programador.programar()
            Resultado.Exito(sesionNueva)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Resultado.Error(TipoError.SIN_RED, "No se pudo completar el canje de tokens OIDC")
        } finally {
            servicio.dispose()
        }
    }

    override suspend fun obtenerTokenFresco(): ResultadoToken = mutexRenovacion.withLock {
        val config = preferencias.actual()
        if (BuildConfig.DEBUG && config?.modoEncabezados == true) {
            return@withLock ResultadoToken.ModoEncabezados
        }

        val estadoAuth = leerAuthState(preferencias.obtenerAuthStateCifrado())
        if (estadoAuth == null || !estadoAuth.isAuthorized) {
            preferencias.guardarRequiereReautenticacion(true)
            return@withLock ResultadoToken.SesionExpirada(MENSAJE_INICIAR_SESION)
        }

        val servicio = crearServicioAppAuth()
        try {
            val (accessToken, idToken, ex) = renovarSiHaceFalta(servicio, estadoAuth)
            when {
                ex != null -> {
                    if (esRefreshTokenVencidoORevocado(ex, estadoAuth)) {
                        guardarAuthState(estadoAuth, sesion = null)
                        preferencias.guardarRequiereReautenticacion(true)
                        ResultadoToken.SesionExpirada(MENSAJE_INICIAR_SESION)
                    } else {
                        ResultadoToken.ErrorRed("Sin conexión con el servidor de identidad")
                    }
                }
                !accessToken.isNullOrBlank() -> {
                    val sesionVigente = ExtractorClaimsJwt.extraerSesion(accessToken, idToken ?: estadoAuth.idToken)
                        ?: SesionIdentidad(
                            usuario = config?.evaluador.orEmpty(),
                            organizacion = config?.organizacion.orEmpty(),
                        )
                    guardarAuthState(estadoAuth, sesionVigente)
                    preferencias.guardarRequiereReautenticacion(false)
                    ResultadoToken.Exito(accessToken, sesionVigente)
                }
                else -> {
                    preferencias.guardarRequiereReautenticacion(true)
                    ResultadoToken.SesionExpirada(MENSAJE_INICIAR_SESION)
                }
            }
        } finally {
            servicio.dispose()
        }
    }

    override suspend fun marcarReautenticacionRequerida() {
        preferencias.guardarRequiereReautenticacion(true)
    }

    override suspend fun cerrarSesion(): Resultado<Unit> {
        preferencias.guardarAuthStateCifrado(null)
        preferencias.guardarRequiereReautenticacion(false)
        return Resultado.Exito(Unit)
    }

    private fun crearServicioAppAuth(): AuthorizationService {
        val config = AppAuthConfiguration.Builder()
            .setConnectionBuilder(constructorConexion)
            .build()
        return AuthorizationService(contexto, config)
    }

    private suspend fun descubrirEmisor(uriDescubrimiento: Uri): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromUrl(
                uriDescubrimiento,
                { config, ex ->
                    if (!cont.isActive) return@fetchFromUrl
                    if (config != null) {
                        cont.resume(config)
                    } else {
                        cont.cancel(ex ?: IllegalStateException("Sin configuración OIDC"))
                    }
                },
                constructorConexion,
            )
        }

    private suspend fun intercambiarCodigo(
        servicio: AuthorizationService,
        respuesta: AuthorizationResponse,
    ): Pair<net.openid.appauth.TokenResponse?, AuthorizationException?> =
        suspendCancellableCoroutine { cont ->
            servicio.performTokenRequest(respuesta.createTokenExchangeRequest()) { tokenResp, ex ->
                if (cont.isActive) {
                    cont.resume(tokenResp to ex)
                }
            }
        }

    private suspend fun renovarSiHaceFalta(
        servicio: AuthorizationService,
        estadoAuth: AuthState,
    ): Triple<String?, String?, AuthorizationException?> =
        suspendCancellableCoroutine { cont ->
            estadoAuth.performActionWithFreshTokens(servicio) { accessToken, idToken, ex ->
                if (cont.isActive) {
                    cont.resume(Triple(accessToken, idToken, ex))
                }
            }
        }

    private fun esRefreshTokenVencidoORevocado(ex: AuthorizationException, estadoAuth: AuthState): Boolean =
        ex.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR ||
            ex == AuthorizationException.TokenRequestErrors.INVALID_GRANT ||
            !estadoAuth.isAuthorized

    private fun leerAuthState(cifrado: String?): AuthState? {
        if (cifrado.isNullOrBlank()) return null
        val bytes = cifrador.descifrar(cifrado) ?: return null
        return runCatching {
            AuthState.jsonDeserialize(String(bytes, StandardCharsets.UTF_8))
        }.getOrNull()
    }

    private suspend fun guardarAuthState(estadoAuth: AuthState, sesion: SesionIdentidad?) {
        val json = estadoAuth.jsonSerializeString()
        val cifrado = cifrador.cifrar(json.toByteArray(StandardCharsets.UTF_8))
        preferencias.guardarAuthStateCifrado(
            cifrado = cifrado,
            evaluador = sesion?.usuario?.takeIf { it.isNotBlank() },
            organizacion = sesion?.organizacion,
        )
    }

    companion object {
        const val CLIENTE_ID = "movil"
        const val SCOPES_OIDC = "openid profile offline_access"
        const val URI_REDIRECCION = "co.plataforma.danos:/oauth2redirect"
        const val RUTA_DESCUBRIMIENTO = "/.well-known/openid-configuration"
    }
}
