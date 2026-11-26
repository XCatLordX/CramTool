package com.cramtool.app.ui.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 圖片快取（以位元組計，最多約可用記憶體的 1/8） */
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** 依目標寬度縮小解碼，避免大圖吃光記憶體 */
    suspend fun load(path: String, maxWidth: Int = 1600): Bitmap? = cache.get("$path@$maxWidth") ?: withContext(Dispatchers.IO) {
        val f = File(path.removePrefix("file://"))
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.also { cache.put("$path@$maxWidth", it) }
    }
}

/** 筆記中的圖片：寬度填滿、維持比例；點一下全螢幕檢視並可縮放 */
@Composable
fun ImageBlock(block: com.cramtool.app.notes.MdBlock.Image, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, block.src) { value = ImageCache.load(block.src) }
    var zoom by remember { mutableStateOf(false) }
    val b = bmp
    if (b == null) {
        val missing = !File(block.src.removePrefix("file://")).exists()
        if (missing) Text(
            "（圖片已不在這支手機上，請重新匯入原始檔）",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 6.dp),
        )
        return
    }
    Image(
        b.asImageBitmap(), contentDescription = block.alt.ifBlank { "圖片" },
        contentScale = ContentScale.Fit,
        modifier = modifier.padding(vertical = 8.dp).fillMaxWidth()
            .aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1))
            .clickable { zoom = true },
    )
    if (zoom) ZoomDialog(b) { zoom = false }
}

@Composable
private fun ZoomDialog(bmp: Bitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, pan, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bmp.asImageBitmap(), contentDescription = "放大檢視",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().transformable(state)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, "關閉", tint = Color.White)
            }
        }
    }
}
