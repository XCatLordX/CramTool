package com.cramtool.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cramtool.app.QuizRequest
import com.cramtool.app.SessionStore
import com.cramtool.app.ui.bank.BankScreen
import com.cramtool.app.ui.browse.BrowseScreen
import com.cramtool.app.ui.exam.ExamScreen
import com.cramtool.app.ui.home.HomeScreen
import com.cramtool.app.ui.importer.ImportScreen
import com.cramtool.app.ui.notes.NoteEditScreen
import com.cramtool.app.ui.notes.NoteReaderScreen
import com.cramtool.app.ui.notes.NotesScreen
import com.cramtool.app.ui.quiz.QuizScreen
import com.cramtool.app.ui.settings.SettingsScreen
import com.cramtool.app.ui.stats.StatsScreen
import com.cramtool.app.ui.wrongbook.WrongBookScreen

private const val NONE = -1L

@Composable
fun CramNavHost() {
    val nav = rememberNavController()

    // 其他 App 分享進來的文字：題庫 JSON → 匯入題庫；其他 → 新增筆記
    val pending by SessionStore.pendingImport.collectAsState()
    LaunchedEffect(pending) {
        val text = pending ?: return@LaunchedEffect
        if (SessionStore.looksLikeQuestionBank(text)) {
            if (nav.currentDestination?.route != "import") nav.navigate("import")
        } else {
            SessionStore.pendingImport.value = null
            SessionStore.pendingNote.value = text
            nav.navigate("note/edit?id=$NONE&folder=$NONE")
        }
    }

    fun startQuiz(req: QuizRequest) {
        SessionStore.quiz = req
        nav.navigate("quiz")
    }

    val tabs: @Composable (MainTab) -> Unit = { current -> MainTabs(current) { nav.switchTab(it) } }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenBank = { nav.navigate("bank/$it") },
                onImport = { nav.navigate("import") },
                onWrongBook = { nav.navigate("wrongbook") },
                onSettings = { nav.navigate("settings") },
                bottomBar = { tabs(MainTab.QUIZ) },
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
                onExam = { req -> SessionStore.exam = req; nav.navigate("exam") },
            )
        }
        composable("browse/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            BrowseScreen(bankId = it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("stats") { StatsScreen(onPractice = ::startQuiz, bottomBar = { tabs(MainTab.STATS) }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("wrongbook") {
            WrongBookScreen(onBack = { nav.popBackStack() }, onStart = ::startQuiz)
        }
        composable("exam") {
            ExamScreen(
                onExit = { nav.popBackStack() },
                onPractice = { req ->
                    SessionStore.quiz = req
                    nav.navigate("quiz") { popUpTo("exam") { inclusive = true } }
                },
            )
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

        // ---------- 筆記 ----------
        val openNote = { id: Long -> nav.navigate("note/$id") }
        val newNote = { folder: Long? -> nav.navigate("note/edit?id=$NONE&folder=${folder ?: NONE}") }
        composable("notes") {
            NotesScreen(
                folderId = null,
                onBack = null,
                onOpenFolder = { nav.navigate("folder/$it") },
                onOpenNote = openNote,
                onNewNote = newNote,
                bottomBar = { tabs(MainTab.NOTES) },
            )
        }
        composable("folder/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            NotesScreen(
                folderId = it.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onOpenFolder = { f -> nav.navigate("folder/$f") },
                onOpenNote = openNote,
                onNewNote = newNote,
            )
        }
        composable("note/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            NoteReaderScreen(
                noteId = it.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate("note/edit?id=$id&folder=$NONE") },
            )
        }
        composable(
            "note/edit?id={id}&folder={folder}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = NONE },
                navArgument("folder") { type = NavType.LongType; defaultValue = NONE },
            ),
        ) {
            val id = it.arguments!!.getLong("id").takeIf { v -> v != NONE }
            val folder = it.arguments!!.getLong("folder").takeIf { v -> v != NONE }
            NoteEditScreen(
                noteId = id,
                initialFolderId = folder,
                onDone = { saved ->
                    if (id == null && saved != null) {
                        // 新筆記存好後直接開啟閱讀
                        nav.navigate("note/$saved") { popUpTo("note/edit?id={id}&folder={folder}") { inclusive = true } }
                    } else nav.popBackStack()
                },
            )
        }
    }
}

private fun NavHostController.switchTab(tab: MainTab) {
    if (currentDestination?.route == tab.route) return
    navigate(tab.route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
