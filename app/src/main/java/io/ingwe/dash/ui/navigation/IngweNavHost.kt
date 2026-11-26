package io.ingwe.dash.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ingwe.dash.data.service.ServiceStore
import io.ingwe.dash.data.signals.SignalCatalog
import java.io.File
import io.ingwe.dash.data.transport.BleTransport
import io.ingwe.dash.data.vehicle.VehicleRepository
import io.ingwe.dash.ui.components.IgnitionIntro
import io.ingwe.dash.ui.dashboard.DashboardScreen
import io.ingwe.dash.ui.dtc.DtcScreen
import io.ingwe.dash.ui.dtc.FaultStore
import io.ingwe.dash.ui.terminal.TerminalScreen

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Dtc       : Screen("dtc")
    data object Terminal  : Screen("terminal")
}

@Composable
fun IngweNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current.applicationContext
    // Shared across screens so the dashboard banner reflects codes cleared on the fault screen.
    val faultStore = remember { FaultStore() }
    val repo = remember {
        VehicleRepository(
            loadSignals = { SignalCatalog.load(context) },
            adapterTransport = { BleTransport(context) },
        )
    }

    // Service log: one small JSON file in app-private storage (see ServiceStore).
    val serviceStore = remember { ServiceStore(File(context.filesDir, "service_log.json")) }

    // Data runs only while the app is on screen: backgrounded = no polling, no CPU.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repo, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { repo.run() }
    }

    IgnitionIntro {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(navController = navController, faultStore = faultStore, repo = repo, serviceStore = serviceStore)
            }
            composable(Screen.Terminal.route) {
                TerminalScreen(navController = navController)
            }
            composable(Screen.Dtc.route) {
                DtcScreen(navController = navController, faultStore = faultStore)
            }
        }
    }
}
