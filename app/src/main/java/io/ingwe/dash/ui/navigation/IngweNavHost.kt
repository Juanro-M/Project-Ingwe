package io.ingwe.dash.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    // Shared across screens so the dashboard banner reflects codes cleared on the fault screen.
    val faultStore = remember { FaultStore() }

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController, faultStore = faultStore)
        }
        composable(Screen.Terminal.route) {
            TerminalScreen(navController = navController)
        }
        composable(Screen.Dtc.route) {
            DtcScreen(navController = navController, faultStore = faultStore)
        }
    }
}
