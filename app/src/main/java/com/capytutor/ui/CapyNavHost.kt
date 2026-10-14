package com.capytutor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capytutor.ui.home.HomeScreen

/** Screen routes. M3 adds the session screens and parent mode. */
object Routes {
    const val HOME = "acasa"
}

@Composable
fun CapyNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onParentMode = { /* Parent mode (PIN) arrives in M3. */ })
        }
    }
}
