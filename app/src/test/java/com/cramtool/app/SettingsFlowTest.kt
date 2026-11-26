package com.cramtool.app

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cramtool.app.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class SettingsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(t: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        runCatching {
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                java.io.File("build/screenshots").mkdirs()
                java.io.File("build/screenshots/settings_$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun settingsFlow() {
        val settings = (rule.activity.application as CramApp).settings
        rule.onNodeWithContentDescription("設定").performClick()
        waitText("顯示模式")
        // 版本資訊在列表最下面
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("CramTool 0.4.0", substring = true))
        waitText("CramTool 0.4.0")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("顯示模式"))

        shot("01_light")
        rule.onNodeWithText("深色").performClick()
        rule.waitForIdle()
        shot("02_dark")
        assertEquals(ThemeMode.DARK, settings.themeMode.value)

        // 關閉音效 → 音效風格選項隱藏
        waitText("內建音效風格")
        rule.onNodeWithText("答題音效").performClick()
        rule.waitForIdle()
        assertFalse(settings.soundEnabled.value)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("內建音效風格").fetchSemanticsNodes().isEmpty() }

        rule.onNodeWithContentDescription("返回").performClick()
        waitText("我的題庫")
        shot("03_home_dark")
    }
}
