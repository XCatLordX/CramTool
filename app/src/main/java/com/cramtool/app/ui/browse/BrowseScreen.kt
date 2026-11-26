package com.cramtool.app.ui.browse

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.ui.cramApp
import com.cramtool.app.ui.quiz.AnswerReveal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(bankId: Long, onBack: () -> Unit) {
    val repo = cramApp().repository
    val bank by repo.observeBank(bankId).collectAsState(initial = null)
    val questions by repo.observeQuestions(bankId).collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val shown = if (query.isBlank()) questions else questions.filter {
        it.stem.contains(query, true) || it.chapter.contains(query, true) || it.options.any { o -> o.contains(query, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bank?.name ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("搜尋題目／章節") }, singleLine = true,
                )
            }
            items(shown, key = { it.id }) { q ->
                QuestionCard(q, number = questions.indexOf(q) + 1) {
                    scope.launch { repo.setStarred(q.id, !q.starred) }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(q: QuestionEntity, number: Int, onStar: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#$number · ${q.chapter} · ${q.type.label}" +
                        if (q.attempts > 0) " · 答對 ${q.correctCount}/${q.attempts}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onStar) {
                    if (q.starred) Icon(Icons.Default.Star, "取消收藏", tint = Color(0xFFFFB300))
                    else Icon(Icons.Default.StarBorder, "收藏")
                }
            }
            Text(q.stem, style = MaterialTheme.typography.bodyLarge, maxLines = if (open) Int.MAX_VALUE else 3)
            if (open) AnswerReveal(q, Modifier.padding(top = 8.dp))
        }
    }
}
