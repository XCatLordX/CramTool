package com.cramtool.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cramtool.app.CramApp

@Composable
fun cramApp(): CramApp = LocalContext.current.applicationContext as CramApp

/** 以 Repository 建立 ViewModel 的小工具，省去 DI 框架 */
@Composable
inline fun <reified VM : ViewModel> cramViewModel(
    key: String? = null,
    crossinline create: (CramApp) -> VM,
): VM {
    val app = cramApp()
    return viewModel(key = key, factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(app) as T
    })
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * 底部按鈕列用：避開手機的導覽列（home 鍵／手勢條），鍵盤彈出時則改為避開鍵盤。
 * 用 union 取兩者較大值，避免鍵盤開啟時重複加上導覽列高度。
 */
@Composable
fun Modifier.bottomBarInsets(): Modifier = this.windowInsetsPadding(
    androidx.compose.foundation.layout.WindowInsets.navigationBars
        .union(androidx.compose.foundation.layout.WindowInsets.ime)
)

/** 浮動按鈕等貼邊元件：橫放時避開左右兩側的導覽列與鏡頭挖孔 */
@Composable
fun Modifier.horizontalSafe(): Modifier = this.windowInsetsPadding(
    androidx.compose.foundation.layout.WindowInsets.safeDrawing
        .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal)
)

/** 在任何執行緒都能安全顯示 Toast（資料庫交易結束後可能不在主執行緒） */
suspend fun mainToast(ctx: android.content.Context, msg: String, long: Boolean = false) =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        android.widget.Toast.makeText(ctx, msg, if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
    }
