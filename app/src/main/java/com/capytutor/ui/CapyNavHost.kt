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
import com.capytutor.ui.components.ChildTab
import com.capytutor.ui.corner.CornerScreen
import com.capytutor.ui.corner.CornerViewModel
import com.capytutor.ui.home.HomeScreen
import com.capytutor.ui.home.HomeViewModel
import com.capytutor.ui.parent.ChangePinScreen
import com.capytutor.ui.parent.HistoryScreen
import com.capytutor.ui.parent.HistoryViewModel
import com.capytutor.ui.parent.LessonFilter
import com.capytutor.ui.parent.LessonProgressScreen
import com.capytutor.ui.parent.LessonsScreen
import com.capytutor.ui.parent.LessonsViewModel
import com.capytutor.ui.parent.LessonProgressViewModel
import com.capytutor.ui.parent.LessonReviewScreen
import com.capytutor.ui.parent.ProgressScreen
import com.capytutor.ui.parent.ProgressViewModel
import com.capytutor.ui.parent.LessonReviewViewModel
import com.capytutor.ui.parent.ParentHomeScreen
import com.capytutor.ui.parent.ParentHomeViewModel
import com.capytutor.ui.parent.ParentSettingsScreen
import com.capytutor.ui.parent.PhoneCheckScreen
import com.capytutor.ui.parent.PinGateScreen
import com.capytutor.ui.parent.UsageScreen
import com.capytutor.ui.quiz.QuizScreen
import com.capytutor.ui.quiz.QuizViewModel
import com.capytutor.ui.reading.ReadingScreen
import com.capytutor.ui.reading.ReadingViewModel
import com.capytutor.ui.recap.RecapScreen
import com.capytutor.ui.recap.RecapViewModel
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
    const val PHONE = "parinte/verificare"
    const val PROGRESS = "parinte/progres"
    const val LESSONS = "parinte/lectii/{filtru}"
    const val LESSON_PROGRESS = "parinte/progres/{id}"
    const val RECAP = "recapitulare"
    const val CORNER = "coltul-lui-capy"
    const val QUIZ = "quiz/{id}"
    /** E14: reading before retelling (ends in the session) or again later (just back). */
    const val READ = "citeste/{id}"
    const val REREAD = "reciteste/{id}"

    fun lesson(id: Long) = "lectie/$id"
    fun review(id: Long) = "parinte/lectie/$id"
    fun history(id: Long) = "parinte/istoric/$id"
    fun lessonProgress(id: Long) = "parinte/progres/$id"
    fun lessons(filter: LessonFilter) = "parinte/lectii/${filter.wire}"
    fun quiz(id: Long) = "quiz/$id"
    fun read(id: Long) = "citeste/$id"
    fun reread(id: Long) = "reciteste/$id"
}

private const val READ_DONE = "citit"

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
                onTab = nav::tab,
            )
        }
        composable(Routes.RECAP) {
            RecapScreen(vm = viewModel { RecapViewModel(c) }, onQuiz = { nav.navigate(Routes.quiz(it)) }, onTab = nav::tab)
        }
        composable(Routes.CORNER) {
            CornerScreen(vm = viewModel { CornerViewModel(c) }, onTab = nav::tab)
        }
        composable(Routes.QUIZ, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            QuizScreen(vm = viewModel { QuizViewModel(c, id) }, onClose = { nav.back() }, onRead = { nav.navigate(Routes.reread(id)) })
        }
        composable(Routes.LESSON, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            val vm = viewModel { SessionViewModel(c, id) }
            // "Am citit, hai să povestesc" comes back here and starts the retelling.
            val read by e.savedStateHandle.getStateFlow(READ_DONE, false).collectAsStateWithLifecycle()
            LaunchedEffect(read) {
                if (read) {
                    e.savedStateHandle[READ_DONE] = false
                    vm.startAfterReading()
                }
            }
            SessionScreen(vm = vm, onClose = { nav.back() }, onRead = { nav.navigate(Routes.read(id)) })
        }
        composable(Routes.READ, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            ReadingScreen(
                c,
                vm = viewModel { ReadingViewModel(c, id) },
                onBack = { nav.back() },
                onDone = {
                    nav.previousBackStackEntry?.savedStateHandle?.set(READ_DONE, true)
                    nav.back()
                },
            )
        }
        composable(Routes.REREAD, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            ReadingScreen(c, vm = viewModel { ReadingViewModel(c, id) }, onBack = { nav.back() }, onDone = null)
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
                onPhoneCheck = { nav.navigate(Routes.PHONE) },
                onProgress = { nav.navigate(Routes.PROGRESS) },
                onLessons = { nav.navigate(Routes.lessons(it)) },
            )
        }
        composable(Routes.LESSONS) { e ->
            LessonsScreen(
                vm = viewModel { LessonsViewModel(c) },
                start = LessonFilter.fromWire(e.arguments?.getString("filtru")),
                onBack = { nav.back() },
                onLesson = { nav.navigate(Routes.review(it)) },
            )
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(
                vm = viewModel { ProgressViewModel(c) },
                childName = settings.childName,
                onBack = { nav.back() },
                onLesson = { nav.navigate(Routes.lessonProgress(it)) },
            )
        }
        composable(Routes.LESSON_PROGRESS, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            LessonProgressScreen(vm = viewModel { LessonProgressViewModel(c, id) }, onBack = { nav.back() }, onSessions = { nav.navigate(Routes.history(id)) })
        }
        composable(Routes.REVIEW, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            LessonReviewScreen(
                vm = viewModel { LessonReviewViewModel(c, id) },
                onBack = { nav.back() },
                onHistory = { nav.navigate(Routes.history(id)) },
                onProgress = { nav.navigate(Routes.lessonProgress(id)) },
            )
        }
        composable(Routes.HISTORY, idArg) { e ->
            val id = e.arguments!!.getLong("id")
            HistoryScreen(vm = viewModel { HistoryViewModel(c, id) }, onBack = { nav.back() })
        }
        composable(Routes.SETTINGS) { ParentSettingsScreen(c, onBack = { nav.back() }, onChangePin = { nav.navigate(Routes.NEW_PIN) }) }
        composable(Routes.NEW_PIN) { ChangePinScreen(c, onBack = { nav.back() }) }
        composable(Routes.USAGE) { UsageScreen(c, onBack = { nav.back() }) }
        composable(Routes.PHONE) { PhoneCheckScreen(c, onBack = { nav.back() }) }
    }
}

/** The child's three tabs: Acasă stays at the bottom of the stack, the others replace each other on top. */
private fun NavHostController.tab(tab: ChildTab) {
    when (tab) {
        ChildTab.HOME -> if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME) { popUpTo(0) }
        ChildTab.REVIEW -> navigate(Routes.RECAP) { popUpTo(Routes.HOME); launchSingleTop = true }
        ChildTab.CORNER -> navigate(Routes.CORNER) { popUpTo(Routes.HOME); launchSingleTop = true }
    }
}

/** Back, but never past Acasă (a double tap on back must not leave a blank screen). */
private fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack() else navigate(Routes.HOME) { popUpTo(0) }
}
