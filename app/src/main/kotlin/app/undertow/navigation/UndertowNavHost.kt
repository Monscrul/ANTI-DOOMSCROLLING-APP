package app.undertow.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.undertow.feature.home.HomeRoute

/**
 * Route names in one place, so later screens are added here instead of restructuring
 * the activity. Only destinations that exist are registered: an unreachable
 * placeholder route would be UI that claims to be built when it is not.
 */
object UndertowRoutes {
    const val HOME = "home"
}

@Composable
fun UndertowNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = UndertowRoutes.HOME,
    ) {
        composable(UndertowRoutes.HOME) {
            HomeRoute()
        }
    }
}
