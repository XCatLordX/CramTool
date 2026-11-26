package com.cramtool.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cramtool.app.data.AppSettings
import com.cramtool.app.data.SoundStyle
import java.io.File

/**
 * 答題音效與震動。內建音效以 [Synth] 合成後存成 WAV 快取；自訂音效為使用者選的檔案（複製到 App 內部）。
 * 使用 SoundPool 以取得低延遲播放。
 */
class SoundEffects(private val context: Context, private val settings: AppSettings) {
    internal val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = mutableMapOf<String, Int>()      // 檔案路徑 -> soundId
    private val ready = mutableSetOf<Int>()
    private val pending = mutableMapOf<Int, Float>()   // 載入中、載完要播的 soundId -> 音量
    private val failed = mutableSetOf<Int>()
    var onLoadFailed: ((path: String) -> Unit)? = null

    init {
        pool.setOnLoadCompleteListener { p, id, status ->
            synchronized(this) {
                if (status == 0) {
                    ready += id
                    pending.remove(id)?.let { vol -> p.play(id, vol, vol, 1, 0, 1f) }
                } else {
                    failed += id
                    pending.remove(id)
                    ids.entries.firstOrNull { it.value == id }?.key?.let { path -> onLoadFailed?.invoke(path) }
                }
            }
        }
    }

    /** 答題時呼叫：依設定播放音效與震動 */
    fun play(cue: Cue) {
        if (settings.hapticsEnabled.value) vibrate(cue)
        if (!settings.soundEnabled.value) return
        playFile(pathFor(cue, settings.soundStyle.value), settings.soundVolume.value)
    }

    /** 設定頁試聽：不受開關影響 */
    fun preview(cue: Cue, style: SoundStyle = settings.soundStyle.value, useCustom: Boolean = true) =
        playFile(if (useCustom) pathFor(cue, style) else builtIn(style, cue).path, settings.soundVolume.value.coerceAtLeast(0.3f))

    fun previewFile(path: String) = playFile(path, settings.soundVolume.value.coerceAtLeast(0.3f))

    private fun pathFor(cue: Cue, style: SoundStyle): String {
        val custom = when (cue) {
            Cue.CORRECT -> settings.customCorrect.value
            Cue.WRONG -> settings.customWrong.value
            Cue.FINISH -> null
        }
        return custom?.takeIf { File(it).exists() } ?: builtIn(style, cue).path
    }

    private fun builtIn(style: SoundStyle, cue: Cue): File {
        val f = File(context.cacheDir, "sfx/v1_${style.name.lowercase()}_${cue.name.lowercase()}.wav")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.writeBytes(Synth.cueWav(style, cue))
        }
        return f
    }

    @Synchronized
    private fun playFile(path: String, volume: Float) {
        val id = ids.getOrPut(path) { pool.load(path, 1) }
        when {
            id in ready -> pool.play(id, volume, volume, 1, 0, 1f)
            id in failed -> Unit
            else -> pending[id] = volume
        }
    }

    /** 自訂檔案更換後，舊的快取要丟掉 */
    @Synchronized
    fun forget(path: String) {
        ids.remove(path)?.let { pool.unload(it); ready -= it; failed -= it }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** 此裝置是否有震動馬達 */
    val canVibrate: Boolean get() = vibrator()?.hasVibrator() == true

    /** 設定頁「測試震動」：不受開關影響 */
    fun testVibration(cue: Cue = Cue.WRONG) = vibrate(cue)

    /**
     * 使用明確強度的波形（不用系統預設的 EFFECT_CLICK：部分手機震感極弱或不支援），
     * 並標記為「觸控回饋」用途：與鍵盤震動同類，靜音／勿擾模式下通常仍會震動。
     */
    private fun vibrate(cue: Cue) {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        val amp = v.hasAmplitudeControl()
        val effect = when (cue) {
            // 答對：一下短而清楚
            Cue.CORRECT -> if (amp) VibrationEffect.createOneShot(40, 180) else VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            // 答錯：兩下
            Cue.WRONG -> if (amp) VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60), intArrayOf(0, 255, 0, 255), -1)
                else VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60), -1)
            // 完成：三下由弱到強
            Cue.FINISH -> if (amp) VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 80), intArrayOf(0, 120, 0, 180, 0, 255), -1)
                else VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 80), -1)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            }
        }
    }

    /** 把使用者選的音訊複製到 App 內部，回傳新路徑 */
    fun importCustom(cue: Cue, uri: android.net.Uri): Result<String> = runCatching {
        val type = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            "ogg" in type -> "ogg"; "wav" in type -> "wav"; "mpeg" in type || "mp3" in type -> "mp3"
            "aac" in type || "mp4" in type || "m4a" in type -> "m4a"; else -> "audio"
        }
        val dir = File(context.filesDir, "sounds").apply { mkdirs() }
        // 檔名每次不同，避免 SoundPool 快取到舊檔
        val out = File(dir, "${cue.name.lowercase()}_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { o ->
                val copied = input.copyTo(o)
                require(copied in 1..MAX_CUSTOM_BYTES) { "檔案太大（上限 1 MB，建議 3 秒內的短音效）" }
            }
        }
        // 刪掉同類型的舊檔
        dir.listFiles()?.filter { it.name.startsWith(cue.name.lowercase() + "_") && it != out }?.forEach { forget(it.path); it.delete() }
        out.path
    }

    fun removeCustom(path: String?) {
        path ?: return
        forget(path); File(path).delete()
    }

    companion object { const val MAX_CUSTOM_BYTES = 1L * 1024 * 1024 }
}
