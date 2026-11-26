package com.cramtool.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BulkOpsTest {
    private val notes get() = (RuntimeEnvironment.getApplication() as CramApp).notes

    @Test fun groupIntoNewFolderAndCycleSafeMove() = runBlocking {
        val a = notes.createFolder("A", null)
        val b = notes.createFolder("B", a)
        val n1 = notes.createNote("n1", "x", null)
        val n2 = notes.createNote("n2", "y", null)

        val g = notes.groupIntoNewFolder("新資料夾", null, listOf(n1, n2), listOf(a))
        assertEquals(2, notes.observeNotesIn(g).first().size)
        assertEquals(listOf("A"), notes.observeSubfolders(g).first().map { it.name })

        // A 不能移進自己的子資料夾 B：略過 1 個，筆記照常移動
        val skipped = notes.moveItems(listOf(n1), listOf(a), b)
        assertEquals(1, skipped)
        assertEquals(listOf("n1"), notes.observeNotesIn(b).first().map { it.title })

        // 批次刪除資料夾 A：未被選取的子資料夾 B（含 n1）移到上一層，不會消失
        notes.deleteItems(emptyList(), listOf(a))
        assertEquals(listOf("B"), notes.observeSubfolders(g).first().map { it.name })
        assertEquals(1, notes.observeNotesIn(b).first().size)
    }
}
