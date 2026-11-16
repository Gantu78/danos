package com.example.danos.infraestructura.identidad

import com.example.danos.ActivoDaoFalso
import com.example.danos.EvaluacionDaoFalso
import com.example.danos.EvidenciaDaoFalso
import com.example.danos.IdentidadFalsa
import com.example.danos.OperacionDaoFalso
import com.example.danos.PreferenciasFalsas
import com.example.danos.ProgramadorFalso
import com.example.danos.TransaccionDirecta
import com.example.danos.acceso.EvaluacionRepositorioImpl
import com.example.danos.activoDePrueba
import com.example.danos.dominio.SesionIdentidad
import com.example.danos.evaluacionDePrueba
import com.example.danos.infraestructura.remoto.EvaluacionDto
import com.example.danos.infraestructura.remoto.JsonDanos
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class IdentidadOidcTest {

    private fun crearJwtPrueba(payloadJson: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray(StandardCharsets.UTF_8))
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))
        return "$header.$payload.firma"
    }

    @Test
    fun `extrae preferred_username, organizacion, roles, ambito y territorio del token OIDC`() {
        val jwt = crearJwtPrueba(
            """
            {
              "sub": "u-123",
              "preferred_username": "evaluador.armenia",
              "organizacion": "alcaldia-armenia",
              "ambito": "LOCAL",
              "territorio": "63001",
              "aud": "gateway",
              "realm_access": {
                "roles": ["evaluador", "offline_access"]
              }
            }
            """.trimIndent(),
        )

        val sesion = ExtractorClaimsJwt.extraerSesion(jwt)!!

        assertEquals("evaluador.armenia", sesion.usuario)
        assertEquals("alcaldia-armenia", sesion.organizacion)
        assertEquals("LOCAL", sesion.ambito)
        assertEquals("63001", sesion.territorio)
        assertEquals(setOf("evaluador", "offline_access"), sesion.roles)
    }

    @Test
    fun `EvaluacionDto llena evaluador y organizacion desde la identidad del token y cerrar sesion no borra la cola`() = runTest {
        val operaciones = OperacionDaoFalso()
        val evaluaciones = EvaluacionDaoFalso()
        val evidencias = EvidenciaDaoFalso(evaluaciones)
        val activos = ActivoDaoFalso()
        val preferencias = PreferenciasFalsas()
        val identidad = IdentidadFalsa(
            sesionInicial = SesionIdentidad(
                usuario = "evaluador.armenia",
                organizacion = "alcaldia-armenia",
                roles = setOf("evaluador"),
            ),
        )
        val repositorio = EvaluacionRepositorioImpl(
            TransaccionDirecta(),
            evaluaciones,
            evidencias,
            operaciones,
            activos,
            preferencias,
            ProgramadorFalso(),
            identidad,
        )

        repositorio.guardarNueva(
            evaluacionDePrueba().copy(evaluador = "otro", organizacion = "otra"),
            activoDePrueba(version = null),
        )

        val dto = JsonDanos.decodeFromString<EvaluacionDto>(operaciones.filas.single().payloadJson)
        assertEquals("evaluador.armenia", dto.evaluador)
        assertEquals("alcaldia-armenia", dto.organizacion)

        // Cerrar sesión borra los tokens pero NO borra la cola de evaluaciones pendientes (AD-44).
        identidad.cerrarSesion()
        assertNull(identidad.sesionActual())
        assertEquals(1, operaciones.filas.size)
        assertEquals(1, evaluaciones.filas.size)
    }
}
