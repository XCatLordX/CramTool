package com.cramtool.app.importer

import com.cramtool.app.data.QuestionEntity
import java.text.Normalizer

object Grader {
    fun gradeChoice(q: QuestionEntity, selected: Set<Int>): Boolean =
        selected.isNotEmpty() && selected == q.correctIndices

    fun gradeFill(q: QuestionEntity, input: String): Boolean {
        val n = normalize(input)
        if (n.isEmpty()) return false
        return q.answers.any { normalize(it) == n }
    }

    /** 全形轉半形、忽略大小寫、空白與結尾標點 */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("\\s+"), "")
            .trimEnd('.', '。', ',', '，', ';', '；')
}
