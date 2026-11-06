package com.example.danos.presentacion.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.danos.presentacion.descarga.DescargaRuta
import com.example.danos.presentacion.evaluar.EvaluarRuta
import com.example.danos.presentacion.ingreso.IngresoRuta
import com.example.danos.presentacion.lista.ListaRuta

@Composable
fun AppNavegacion(
    navController: NavHostController = rememberNavController(),
    inicioViewModel: InicioViewModel = hiltViewModel(),
) {
    val configurada by inicioViewModel.configurada.collectAsStateWithLifecycle()
    val conDatos = configurada ?: return

    NavHost(
        navController = navController,
        startDestination = if (conDatos) RutaLista else RutaIngreso,
    ) {
        composable<RutaIngreso> {
            IngresoRuta(
                alGuardar = {
                    navController.navigate(RutaLista) {
                        popUpTo(RutaIngreso) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<RutaLista> {
            ListaRuta(
                alNuevaEvaluacion = { navController.navigate(RutaEvaluar()) },
                alEditar = { id -> navController.navigate(RutaEvaluar(evaluacionId = id)) },
                alAbrirDescarga = { navController.navigate(RutaDescarga) },
                alAbrirIngreso = { navController.navigate(RutaIngreso) },
            )
        }
        composable<RutaDescarga> {
            DescargaRuta(alVolver = { navController.popBackStack() })
        }
        composable<RutaEvaluar> {
            EvaluarRuta(alTerminar = { navController.popBackStack() })
        }
    }
}
