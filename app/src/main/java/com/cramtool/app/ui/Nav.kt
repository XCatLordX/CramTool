package com.cramtool.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cramtool.app.QuizRequest
import com.cramtool.app.SessionStore
import com.cramtool.app.ui.bank.BankScreen
import com.cramtool.app.ui.browse.BrowseScreen
import com.cramtool.app.ui.home.HomeScreen
import com.cramtool.app.ui.importer.ImportScreen
import com.cramtool.app.ui.quiz.QuizScreen
import com.cramtool.app.ui.wrongbook.WrongBookScreen

@Composable
fun CramNavHost() {
    val nav = rememberNavController()
    val pending by SessionStore.pendingImport.collectAsState()
    LaunchedEffect(pending) {
        if (pending != null && nav.currentDestination?.route != "import") nav.navigate("import")
    }

    fun startQuiz(req: QuizRequest) {
        SessionStore.quiz = req
        nav.navigate("quiz")
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenBank = { nav.navigate("bank/$it") },
                onImport = { nav.navigate("import") },
                onWrongBook = { nav.navigate("wrongbook") },
            )
        }
        composable("import") {
            ImportScreen(
                onBack = { nav.popBackStack() },
                onImported = { bankId ->
                    nav.navigate("bank/$bankId") { popUpTo("home") }
                },
            )
        }
        composable("bank/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            val id = it.arguments!!.getLong("id")
            BankScreen(
                bankId = id,
                onBack = { nav.popBackStack() },
                onStart = ::startQuiz,
                onBrowse = { nav.navigate("browse/$id") },
            )
        }
        composable("browse/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            BrowseScreen(bankId = it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("wrongbook") {
            WrongBookScreen(onBack = { nav.popBackStack() }, onStart = ::startQuiz)
        }
        composable("quiz") {
            QuizScreen(
                onExit = { nav.popBackStack() },
                onRestart = { req ->
                    SessionStore.quiz = req
                    nav.navigate("quiz") { popUpTo("quiz") { inclusive = true } }
                },
            )
        }
    }
}
