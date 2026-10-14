package com.capytutor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.capytutor.container
import com.capytutor.ui.home.HomeScreen
import com.capytutor.ui.home.HomeViewModel
import com.capytutor.ui.parent.ChangePinScreen
import com.capytutor.ui.parent.HistoryScreen
import com.capytutor.ui.parent.HistoryViewModel
import com.capytutor.ui.parent.LessonReviewScreen
import com.capytutor.ui.parent.LessonReviewViewModel
import com.capytutor.ui.parent.ParentHomeScreen
import com.capytutor.ui.parent.ParentHomeViewModel
import com.capytutor.ui.parent.ParentSettingsScreen
import com.capytutor.ui.parent.PinGateScreen
import com.capytutor.ui.parent.UsageScreen
import com.capytutor.ui.session.SessionScreen
import com.capytutor.ui.session.SessionViewModel
import com.capytutor.ui.setup.SetupScreen

object Routes {
    const val SETUP = "configurare"
    const val HOME = "acasa"
    const val LESSON = "lectie/{id}"
    const val PIN = "parinte/pin"
    const val PARENT = "parinte"
    const val REVIEW = "parinte/lectie/{id}"
    const val HISTORY = "parinte/istoric/{id}"
    const val SETTINGS = "parinte/setari"
    const val NEW_PIN = "parinte/pin-nou"
    const val USAGE = "parinte/folosire"

    fun lesson(id: Long) = "lectie/$id"
    fun review(id: Long) = "parinte/lectie/$id"
    fun history(id: Long) = "parinte/istoric/$id"
}

private val idArg = listOf(navArgument("id") { type = NavType.LongType })

/**
 * [shared]: a lesson file shared to the app (Android share menu). It opens parent mode behind
 * the PIN and is imported there.
 */
@Composable
fun CapyNavHost(shared: String?, onSharedConsumed: () -> Unit) {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    if (!settings.loaded) return
    val nav = rememberNavController()

    LaunchedEffect(shared, settings.setupDone) {
        if (shared != null && settings.setupDone && nav.currentDestination?.route?.startsWith(Routes.PARENT) != true) {
            nav.navigate(Routes.PIN)
        }
    }

    NavHost(navController = nav, startDestination = if (settings.setupDone) Routes.HOME else Routes.SETUP) {
        composable(Routes.SETUP) {
            SetupScreen(c, onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } } })
        }
        composable(Routes.HOME) {
            HomeScreen(
                vm = viewModel { HomeViewModel(c) },
                onOpenLesson = { nav.navigate(Routes.lesson(it)) },
                onParentMode = { nav.navigate(Routes.PIN) },
            )
        }
        composable(Routes.LESSON, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            SessionScreen(vm = viewModel { SessionViewModel(c, id) }, onClose = { nav.back() })
        }
        composable(Routes.PIN) {
            PinGateScreen(c, onBack = { nav.back() }, onOpen = { nav.navigate(Routes.PARENT) { popUpTo(Routes.PIN) { inclusive = true } } })
        }
        composable(Routes.PARENT) {
            ParentHomeScreen(
                vm = viewModel { ParentHomeViewModel(c) },
                shared = shared,
                onSharedConsumed = onSharedConsumed,
                onBack = { nav.back() },
                onLesson = { nav.navigate(Routes.review(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onUsage = { nav.navigate(Routes.USAGE) },
            )
        }
        composable(Routes.REVIEW, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            LessonReviewScreen(vm = viewModel { LessonReviewViewModel(c, id) }, onBack = { nav.back() }, onHistory = { nav.navigate(Routes.history(id)) })
        }
        composable(Routes.HISTORY, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            HistoryScreen(vm = viewModel { HistoryViewModel(c, id) }, onBack = { nav.back() })
        }
        composable(Routes.SETTINGS) { ParentSettingsScreen(c, onBack = { nav.back() }, onChangePin = { nav.navigate(Routes.NEW_PIN) }) }
        composable(Routes.NEW_PIN) { ChangePinScreen(c, onBack = { nav.back() }) }
        composable(Routes.USAGE) { UsageScreen(c, onBack = { nav.back() }) }
    }
}

/** Back, but never past Acasă (a double tap on back must not leave a blank screen). */
private fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack() else navigate(Routes.HOME) { popUpTo(0) }
}
