package com.cramtool.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cramtool.app.ui.CramNavHost
import com.cramtool.app.ui.theme.CramTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            CramTheme {
                CramNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val text: String? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.streamUri()?.let { readUri(it) }
            Intent.ACTION_VIEW -> intent.data?.let { readUri(it) }
            else -> null
        }
        if (!text.isNullOrBlank()) SessionStore.pendingImport.value = text
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    private fun readUri(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
}
