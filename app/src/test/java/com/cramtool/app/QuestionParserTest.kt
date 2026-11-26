package com.cramtool.app

import com.cramtool.app.data.QuestionType
import com.cramtool.app.importer.ParseException
import com.cramtool.app.importer.QuestionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuestionParserTest {

    @Test fun parsesSampleFile() {
        val f = listOf("../samples/sample_bank.json", "samples/sample_bank.json").map(::File).first { it.exists() }
        val r = QuestionParser.parse(f.readText())
        assertEquals("資料結構 期中考（範例）", r.bankName)
        assertTrue(r.warnings.isEmpty())
        assertEquals(setOf(QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE, QuestionType.FILL, QuestionType.SHORT),
            r.questions.map { it.type }.toSet())
    }

    @Test fun stripsCodeFenceAndChatter() {
        val raw = """
            好的，以下是題目：
            ```json
            {"bank":"X","questions":[{"question":"1+1?","options":["1","2"],"answer":"B"}]}
            ```
            祝考試順利！
        """.trimIndent()
        val r = QuestionParser.parse(raw)
        assertEquals("X", r.bankName)
        assertEquals(listOf("1"), r.questions[0].answers)
        assertEquals(QuestionType.SINGLE, r.questions[0].type)
    }

    @Test fun rootArrayAndChineseKeys() {
        val r = QuestionParser.parse("""[{"題目":"台灣首都？","選項":["台北","台中"],"答案":"台北","章節":"地理"}]""")
        assertEquals("地理", r.questions[0].chapter)
        assertEquals(listOf("0"), r.questions[0].answers)
    }

    @Test fun optionPrefixesAndLetterStrings() {
        val r = QuestionParser.parse("""{"questions":[{"question":"q","options":["A. aa","B. bb","C. cc","D. dd"],"answer":"A, C"}]}""")
        val q = r.questions[0]
        assertEquals(listOf("aa", "bb", "cc", "dd"), q.options)
        assertEquals(QuestionType.MULTIPLE, q.type)
        assertEquals(listOf("0", "2"), q.answers)
    }

    @Test fun optionsAsObject() {
        val r = QuestionParser.parse("""{"questions":[{"question":"q","options":{"A":"x","B":"y","C":"z"},"answer":["B","C"],"type":"multiple"}]}""")
        assertEquals(listOf("x", "y", "z"), r.questions[0].options)
        assertEquals(listOf("1", "2"), r.questions[0].answers)
    }

    @Test fun numericAnswers() {
        // 有 0 → 0 起算
        val a = QuestionParser.parse("""[{"question":"q","options":["a","b","c"],"answer":0}]""")
        assertEquals(listOf("0"), a.questions[0].answers)
        // 出現選項數 → 1 起算
        val b = QuestionParser.parse("""[{"question":"q","options":["a","b","c"],"answer":3}]""")
        assertEquals(listOf("2"), b.questions[0].answers)
        // answerIndex 一律 0 起算
        val c = QuestionParser.parse("""[{"question":"q","options":["a","b","c"],"answerIndex":1}]""")
        assertEquals(listOf("1"), c.questions[0].answers)
    }

    @Test fun trueFalseVariants() {
        val r = QuestionParser.parse("""[
            {"question":"a","answer":true},
            {"question":"b","type":"是非","answer":"X"},
            {"question":"c","type":"tf","answer":"對"}
        ]""")
        assertEquals(listOf("0", "1", "0"), r.questions.map { it.answers.single() })
        assertTrue(r.questions.all { it.type == QuestionType.TRUE_FALSE })
    }

    @Test fun optionTextThatLooksLikeLettersIsSingle() {
        val r = QuestionParser.parse("""[{"question":"q","options":["Heap","Stack"],"answer":"Stack"}]""")
        assertEquals(QuestionType.SINGLE, r.questions[0].type)
        assertEquals(listOf("1"), r.questions[0].answers)
    }

    @Test fun nestedChapters() {
        val r = QuestionParser.parse("""{"bank":"B","chapters":[{"name":"Ch1","questions":[{"question":"q","answer":["x"],"type":"fill"}]}]}""")
        assertEquals("Ch1", r.questions[0].chapter)
    }

    @Test fun badItemsBecomeWarnings() {
        val r = QuestionParser.parse("""[{"question":"ok","answer":true},{"options":["a"]},{"question":"q","options":["a","b"],"answer":"Z"}]""")
        assertEquals(1, r.questions.size)
        assertEquals(2, r.warnings.size)
    }

    @Test(expected = ParseException::class) fun noJson() {
        QuestionParser.parse("抱歉我無法產生題目")
    }
}
