package io.ingwe.dash.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ingwe.dash.ui.dashboard.DashboardScreen
import io.ingwe.dash.ui.dtc.DtcScreen
import io.ingwe.dash.ui.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Dtc       : Screen("dtc")
    object Scanner   : Screen("scanner")
    object Terminal  : Screen("terminal")
    object Connect   : Screen("connect")
}

@Composable
fun IngweNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Terminal.route) {
            TerminalScreen(navController = navController)
        }
        composable(Screen.Dtc.route) {
            DtcScreen(navController = navController)
        }
        composable(Screen.Scanner.route) { /* M6 */ }
        composable(Screen.Connect.route) { /* M3 */ }
    }
}
