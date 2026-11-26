package com.cramtool.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class MainTab(val route: String, val label: String) { QUIZ("home", "刷題"), NOTES("notes", "筆記") }

@Composable
fun MainTabs(current: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == MainTab.QUIZ, onClick = { onSelect(MainTab.QUIZ) },
            icon = { Icon(Icons.Default.Quiz, null) }, label = { Text(MainTab.QUIZ.label) },
        )
        NavigationBarItem(
            selected = current == MainTab.NOTES, onClick = { onSelect(MainTab.NOTES) },
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }, label = { Text(MainTab.NOTES.label) },
        )
    }
}
