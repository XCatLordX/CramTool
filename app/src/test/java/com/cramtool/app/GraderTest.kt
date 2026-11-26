package com.cramtool.app

import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.data.QuestionType
import com.cramtool.app.importer.Grader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {
    private fun q(type: QuestionType, answers: List<String>) = QuestionEntity(
        bankId = 1, chapter = "c", type = type, stem = "s", options = listOf("a", "b", "c"),
        answers = answers, explanation = "", orderInBank = 0,
    )

    @Test fun choice() {
        val m = q(QuestionType.MULTIPLE, listOf("0", "2"))
        assertTrue(Grader.gradeChoice(m, setOf(0, 2)))
        assertFalse(Grader.gradeChoice(m, setOf(0)))
        assertFalse(Grader.gradeChoice(m, emptySet()))
    }

    @Test fun fillIsLenientOnCaseWidthAndSpaces() {
        val f = q(QuestionType.FILL, listOf("O(n log n)"))
        assertTrue(Grader.gradeFill(f, "o(nlogn)"))
        assertTrue(Grader.gradeFill(f, "Ｏ（ｎ ｌｏｇ ｎ）"))
        assertFalse(Grader.gradeFill(f, "O(n)"))
    }
}
