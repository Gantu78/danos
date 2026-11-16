package com.example.danos.acceso

import com.example.danos.ActivoDaoFalso
import com.example.danos.ApiFalsa
import com.example.danos.CatalogoDaoFalso
import com.example.danos.EvaluacionDaoFalso
import com.example.danos.EvidenciaDaoFalso
import com.example.danos.IdentidadFalsa
import com.example.danos.OperacionDaoFalso
import com.example.danos.PreferenciasFalsas
import com.example.danos.ProgramadorFalso
import com.example.danos.TransaccionDirecta
import com.example.danos.activoDePrueba
import com.example.danos.dominio.Resultado
import com.example.danos.evaluacionDePrueba
import com.example.danos.infraestructura.local.aEntidad
import com.example.danos.infraestructura.remoto.ActivoDto
import com.example.danos.infraestructura.remoto.CriterioDto
import com.example.danos.infraestructura.remoto.DesastreDto
import com.example.danos.infraestructura.remoto.DescargaDto
import com.example.danos.infraestructura.remoto.TipoActivoDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoRepositorioTest {
    private val operaciones = OperacionDaoFalso()
    private val evaluaciones = EvaluacionDaoFalso()
    private val evidencias = EvidenciaDaoFalso(evaluaciones)
    private val catalogoDao = CatalogoDaoFalso(evaluaciones, operaciones)
    private val activos = ActivoDaoFalso(operaciones, evaluaciones)
    private val preferencias = PreferenciasFalsas()
    private val identidad = IdentidadFalsa()
    private val api = ApiFalsa()

    private val catalogo = CatalogoRepositorioImpl(
        api = api,
        transaccion = TransaccionDirecta(),
        catalogoDao = catalogoDao,
        activoDao = activos,
        preferencias = preferencias,
        identidad = identidad,
    )

    private val repositorioEvaluaciones = EvaluacionRepositorioImpl(
        TransaccionDirecta(),
        evaluaciones,
        evidencias,
        operaciones,
        activos,
        preferencias,
        ProgramadorFalso(),
        identidad,
    )

    @Test
    fun `la primera descarga envia desde nulo y guarda instanteServidor para la siguiente`() = runTest {
        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T10:00:00Z",
                incremental = false,
            )
        }

        assertTrue(catalogo.descargar(desastreId = "d-1", zona = "63001") is Resultado.Exito)
        assertEquals(null, api.descargas.single().desde)
        assertEquals("63001", api.descargas.single().zona)
        assertEquals("2026-11-16T10:00:00Z", preferencias.obtenerMarcadorDesde())

        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T12:00:00Z",
                incremental = true,
                desde = "2026-11-16T10:00:00Z",
            )
        }
        assertTrue(catalogo.descargar(desastreId = "d-1", zona = "63001") is Resultado.Exito)
        assertEquals("2026-11-16T10:00:00Z", api.descargas[1].desde)
        assertEquals("2026-11-16T12:00:00Z", preferencias.obtenerMarcadorDesde())
    }

    @Test
    fun `forzar descarga completa borra el marcador desde y pide descarga sin desde`() = runTest {
        preferencias.guardarMarcadorDesde("2026-11-16T08:00:00Z")
        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T14:00:00Z",
                incremental = false,
            )
        }

        assertTrue(catalogo.descargar(desastreId = "d-1", forzarCompleta = true) is Resultado.Exito)

        assertNull(api.descargas.single().desde)
        assertEquals("2026-11-16T14:00:00Z", preferencias.obtenerMarcadorDesde())
    }

    @Test
    fun `la descarga incremental fusiona activos sin borrar los que no vinieron y respeta la version`() = runTest {
        activos.guardar(activoDePrueba(id = "a-conservado", version = 2, codigoMunicipio = "63001").aEntidad())
        activos.guardar(activoDePrueba(id = "a-actualizar", version = 3, codigoMunicipio = "63001").aEntidad())
        activos.guardar(activoDePrueba(id = "a-mas-nuevo", version = 5, codigoMunicipio = "63001").aEntidad())

        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T15:00:00Z",
                incremental = true,
                desde = "2026-11-16T10:00:00Z",
                activos = listOf(
                    ActivoDto("a-actualizar", "VIV", 4.5, -75.6, "Ref actualizada", version = 4, codigoMunicipio = "63130"),
                    ActivoDto("a-mas-nuevo", "VIV", 4.5, -75.6, "Ref vieja del servidor", version = 3, codigoMunicipio = "63001"),
                    ActivoDto("a-nuevo", "ESC", 4.53, -75.68, "Escuela nueva", version = 1, codigoMunicipio = "63001"),
                ),
            )
        }

        assertTrue(catalogo.descargar("d-1") is Resultado.Exito)

        // No borra el que no vino en la incremental
        assertNotNull(activos.obtener("a-conservado"))
        // Actualiza cuando la versión entrante es mayor
        val actualizado = activos.obtener("a-actualizar")!!
        assertEquals(4, actualizado.versionDescargada)
        assertEquals("Ref actualizada", actualizado.referencia)
        assertEquals("63130", actualizado.codigoMunicipio)
        // Respeta versión: no pisa la versión 5 local con la versión 3 entrante
        val masNuevo = activos.obtener("a-mas-nuevo")!!
        assertEquals(5, masNuevo.versionDescargada)
        assertEquals("CL 10 # 5-20", masNuevo.referencia)
        // Inserta el nuevo
        val insertado = activos.obtener("a-nuevo")!!
        assertEquals(1, insertado.versionDescargada)
        assertEquals("63001", insertado.codigoMunicipio)
    }

    @Test
    fun `la descarga incremental no toca activos creados en campo ni con operaciones pendientes`() = runTest {
        repositorioEvaluaciones.guardarNueva(
            evaluacionDePrueba(id = "e-campo", activoId = "a-campo"),
            activoDePrueba(id = "a-campo", version = null),
        )
        activos.guardar(activoDePrueba(id = "a-pendiente", version = 2).aEntidad())
        repositorioEvaluaciones.guardarNueva(
            evaluacionDePrueba(id = "e-pendiente", activoId = "a-pendiente"),
            null,
        )

        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T16:00:00Z",
                incremental = true,
                activos = listOf(
                    ActivoDto("a-campo", "VIV", 0.0, 0.0, "Intento pisar campo", version = 10),
                    ActivoDto("a-pendiente", "VIV", 0.0, 0.0, "Intento pisar pendiente", version = 10),
                ),
            )
        }

        assertTrue(catalogo.descargar(null) is Resultado.Exito)

        assertNull(activos.obtener("a-campo")!!.versionDescargada)
        assertEquals("CL 10 # 5-20", activos.obtener("a-campo")!!.referencia)
        assertEquals(2, activos.obtener("a-pendiente")!!.versionDescargada)
    }

    @Test
    fun `la descarga completa retira un desastre que deja de llegar solo si no tiene evaluaciones pendientes`() = runTest {
        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T10:00:00Z",
                incremental = false,
                desastres = listOf(
                    DesastreDto("d-con-pendiente", "Sismo Quindío", "SISMO", "EN_EVALUACION", listOf("63001", "63130")),
                    DesastreDto("d-sin-pendiente", "Inundación", "INUNDACION", "REGISTRADO", listOf("63001")),
                ),
            )
        }
        assertTrue(catalogo.descargar(null) is Resultado.Exito)
        assertEquals(2, catalogo.observarDesastres().first().size)

        // Creamos una evaluación pendiente asociada a "d-con-pendiente"
        repositorioEvaluaciones.guardarNueva(
            evaluacionDePrueba(id = "eval-pendiente", desastreId = "d-con-pendiente"),
            activoDePrueba(version = null),
        )

        // En la siguiente descarga completa ya no llegan ninguno de los dos, y llega "d-nuevo"
        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T11:00:00Z",
                incremental = false,
                desastres = listOf(
                    DesastreDto("d-nuevo", "Deslizamiento", "DESLIZAMIENTO", "REGISTRADO", listOf("63001")),
                ),
            )
        }
        assertTrue(catalogo.descargar(null, forzarCompleta = true) is Resultado.Exito)

        val idsLocales = catalogo.observarDesastres().first().map { it.id }.toSet()
        // "d-sin-pendiente" se retira; "d-con-pendiente" se conserva por tener evaluación pendiente; "d-nuevo" entra
        assertEquals(setOf("d-con-pendiente", "d-nuevo"), idsLocales)
    }

    @Test
    fun `fusiona tipos de activo por version en descarga incremental`() = runTest {
        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T10:00:00Z",
                incremental = false,
                tiposActivo = listOf(
                    TipoActivoDto("VIV", "Vivienda", listOf(CriterioDto("LEVE", "Fisura")), version = 2),
                    TipoActivoDto("ESC", "Escuela", emptyList(), version = 1),
                ),
            )
        }
        catalogo.descargar(null)

        api.alDescargar = {
            DescargaDto(
                instanteServidor = "2026-11-16T11:00:00Z",
                incremental = true,
                tiposActivo = listOf(
                    TipoActivoDto("VIV", "Vivienda vieja", emptyList(), version = 1),
                    TipoActivoDto("ESC", "Escuela actualizada", listOf(CriterioDto("GRAVE", "Colapso")), version = 3),
                ),
            )
        }
        catalogo.descargar(null)

        val tipos = catalogo.observarTipos().first().associateBy { it.codigo }
        assertEquals(2, tipos.getValue("VIV").version)
        assertEquals("Vivienda", tipos.getValue("VIV").nombre)
        assertEquals(3, tipos.getValue("ESC").version)
        assertEquals("Escuela actualizada", tipos.getValue("ESC").nombre)
    }
}
