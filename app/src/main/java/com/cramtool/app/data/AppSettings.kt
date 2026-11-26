package com.cramtool.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("跟隨系統"), LIGHT("淺色"), DARK("深色") }

enum class SoundStyle(val label: String, val desc: String) {
    BRIGHT("清脆", "明亮俐落的提示音"),
    SOFT("柔和", "木琴般溫和的音色"),
    RETRO("復古 8-bit", "電玩風格的方波音效"),
}

/** App 層級設定（SharedPreferences），以 StateFlow 讓畫面即時更新 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun <T> pref(initial: T, save: (T) -> Unit): Pair<MutableStateFlow<T>, (T) -> Unit> {
        val flow = MutableStateFlow(initial)
        return flow to { v: T -> save(v); flow.value = v }
    }

    private val theme = pref(enumOr(prefs.getString(K_THEME, null), ThemeMode.SYSTEM)) { prefs.edit().putString(K_THEME, it.name).apply() }
    val themeMode: StateFlow<ThemeMode> = theme.first
    fun setThemeMode(v: ThemeMode) = theme.second(v)

    private val dynamic = pref(prefs.getBoolean(K_DYNAMIC, true)) { prefs.edit().putBoolean(K_DYNAMIC, it).apply() }
    val dynamicColor: StateFlow<Boolean> = dynamic.first
    fun setDynamicColor(v: Boolean) = dynamic.second(v)

    private val sound = pref(prefs.getBoolean(K_SOUND, true)) { prefs.edit().putBoolean(K_SOUND, it).apply() }
    val soundEnabled: StateFlow<Boolean> = sound.first
    fun setSoundEnabled(v: Boolean) = sound.second(v)

    private val style = pref(enumOr(prefs.getString(K_STYLE, null), SoundStyle.BRIGHT)) { prefs.edit().putString(K_STYLE, it.name).apply() }
    val soundStyle: StateFlow<SoundStyle> = style.first
    fun setSoundStyle(v: SoundStyle) = style.second(v)

    private val volume = pref(prefs.getFloat(K_VOLUME, 0.8f)) { prefs.edit().putFloat(K_VOLUME, it).apply() }
    val soundVolume: StateFlow<Float> = volume.first
    fun setSoundVolume(v: Float) = volume.second(v.coerceIn(0f, 1f))

    private val haptics = pref(prefs.getBoolean(K_HAPTICS, true)) { prefs.edit().putBoolean(K_HAPTICS, it).apply() }
    val hapticsEnabled: StateFlow<Boolean> = haptics.first
    fun setHapticsEnabled(v: Boolean) = haptics.second(v)

    /** 自訂音效檔（App 內部儲存的路徑）；null = 使用內建風格 */
    private val customCorrectP = pref(prefs.getString(K_CUSTOM_OK, null)) { prefs.edit().putString(K_CUSTOM_OK, it).apply() }
    val customCorrect: StateFlow<String?> = customCorrectP.first
    fun setCustomCorrect(path: String?) = customCorrectP.second(path)

    private val customWrongP = pref(prefs.getString(K_CUSTOM_BAD, null)) { prefs.edit().putString(K_CUSTOM_BAD, it).apply() }
    val customWrong: StateFlow<String?> = customWrongP.first
    fun setCustomWrong(path: String?) = customWrongP.second(path)

    fun resetAll() {
        setThemeMode(ThemeMode.SYSTEM); setDynamicColor(true)
        setSoundEnabled(true); setSoundStyle(SoundStyle.BRIGHT); setSoundVolume(0.8f); setHapticsEnabled(true)
        setCustomCorrect(null); setCustomWrong(null)
    }

    companion object {
        private const val K_THEME = "theme_mode"
        private const val K_DYNAMIC = "dynamic_color"
        private const val K_SOUND = "sound_enabled"
        private const val K_STYLE = "sound_style"
        private const val K_VOLUME = "sound_volume"
        private const val K_HAPTICS = "haptics_enabled"
        private const val K_CUSTOM_OK = "sound_custom_correct"
        private const val K_CUSTOM_BAD = "sound_custom_wrong"

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
    }
}
