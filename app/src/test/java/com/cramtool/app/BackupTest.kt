package com.cramtool.app

import com.cramtool.app.data.BackupCodec
import com.cramtool.app.data.BackupException
import com.cramtool.app.data.ThemeMode
import com.cramtool.app.importer.QuestionParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val app get() = RuntimeEnvironment.getApplication() as CramApp

    /** 建立：1 題庫（8 題，其中 1 題答錯進錯題本）、資料夾 A/B、B 裡 1 篇有學習進度的筆記 */
    private suspend fun seed() {
        val sample = listOf("../samples/sample_bank.json", "samples/sample_bank.json").map(::File).first { it.exists() }
        val parsed = QuestionParser.parse(sample.readText())
        val bankId = app.repository.importQuestions("資料結構", "desc", parsed.questions, null)
        val q = app.repository.getQuestions(bankId).first()
        app.repository.recordAnswer(q.id, correct = false)
        val a = app.notes.createFolder("A", null)
        val b = app.notes.createFolder("B", a)
        val noteId = app.notes.createNote("筆記", "# 筆記\n## 1 章\n### 1.1 節\n內容", b)
        app.notes.saveLessonState(noteId, 1, setOf("1.1 節"))
        app.settings.setThemeMode(ThemeMode.DARK)
    }

    private suspend fun export() = BackupCodec.decode(BackupCodec.encode(app.backup.export("test")))

    @Test fun mergeKeepsExistingAndRemapsIds() = runBlocking {
        seed()
        val backup = export()
        assertEquals(1, backup.banks.size); assertEquals(8, backup.questions.size)
        assertEquals(2, backup.folders.size); assertEquals(1, backup.notes.size)

        app.backup.restore(backup, replace = false)

        val banks = app.repository.getBanks()
        assertEquals(2, banks.size)
        banks.forEach { assertEquals(8, app.repository.getQuestions(it.id).size) }
        // 作答紀錄跟著還原：兩份各有 1 題在錯題本
        assertEquals(2, app.repository.observeWrongItems().first().size)
        // 巢狀資料夾正確接回：每個 B 的父資料夾都是名為 A 的資料夾
        val folders = app.notes.observeAllFolders().first()
        assertEquals(4, folders.size)
        folders.filter { it.name == "B" }.forEach { b -> assertEquals("A", folders.first { it.id == b.parentId }.name) }
        // 筆記放在新的 B 裡，學習進度保留
        val restoredB = folders.filter { it.name == "B" }.map { it.id }
        val notesInB = restoredB.flatMap { app.notes.observeNotesIn(it).first() }
        assertEquals(2, notesInB.size)
        notesInB.forEach { assertEquals(setOf("1.1 節"), app.notes.getNote(it.id)!!.completedSet) }
    }

    @Test fun replaceWipesThenRestoresIncludingSettings() = runBlocking {
        seed()
        val backup = export()
        // 備份後又做了些變動
        app.repository.importQuestions("另一個", "", QuestionParser.parse("""[{"question":"q","answer":true}]""").questions, null)
        app.notes.createFolder("C", null)
        app.settings.setThemeMode(ThemeMode.LIGHT)

        app.backup.restore(backup, replace = true)

        assertEquals(listOf("資料結構"), app.repository.getBanks().map { it.name })
        assertEquals(setOf("A", "B"), app.notes.observeAllFolders().first().map { it.name }.toSet())
        assertEquals(1, app.repository.observeWrongItems().first().size)
        assertEquals(ThemeMode.DARK, app.settings.themeMode.value)
        val note = app.notes.searchNotes("筆記").first().single()
        assertEquals(setOf("1.1 節"), app.notes.getNote(note.id)!!.completedSet)
    }

    @Test fun rejectsInvalidFiles() {
        for (bad in listOf("hello", """{"questions":[]}""", """{"app":"Other","exportedAt":1}""", """{"app":"CramTool","format":99,"exportedAt":1}""")) {
            val e = runCatching { BackupCodec.decode(bad) }.exceptionOrNull()
            assertTrue("應拒絕：$bad", e is BackupException)
        }
    }
}
