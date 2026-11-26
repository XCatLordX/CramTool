package com.cramtool.app.notes

/**
 * 課程式分頁：把一篇筆記依標題切成一頁一頁的小節。
 *
 * 規則：
 *  - 有「###」就以 ### 為一節（一頁）；否則以「##」為一節；都沒有就整篇一頁。
 *  - 比小節大一層的章標題（例如 ##）若底下有導覽文字，自成「章節導讀」頁；
 *    若沒有內容，則併到下一頁最上方，讓讀者知道進入了新的一章。
 *  - 「#」課程標題與開頭簡介合成「導讀」頁。
 *  - 小節前後的分隔線（---）會被去掉。
 */
data class LessonPage(
    /** 頁面標題（小節名稱），也是完成紀錄的 key */
    val title: String,
    /** 所屬章名（章節導讀頁本身為 null） */
    val chapter: String?,
    /** 0 = 導讀、1 = 章、2 = 節（用於目錄縮排） */
    val depth: Int,
    val blocks: List<MdBlock>,
)

object Lesson {
    const val INTRO_TITLE = "導讀"

    fun paginate(blocks: List<MdBlock>): List<LessonPage> {
        val levels = blocks.filterIsInstance<MdBlock.Heading>().map { it.level }.toSet()
        val split = when {
            3 in levels -> 3
            2 in levels -> 2
            else -> return listOf(LessonPage(firstHeading(blocks) ?: INTRO_TITLE, null, 0, trimRules(blocks)))
        }
        val chapterLevel = split - 1 // 2 當 split=3；1 當 split=2（此時 # 視為課程標題，不當章）

        val pages = mutableListOf<LessonPage>()
        var cur = mutableListOf<MdBlock>()
        var curTitle: String = INTRO_TITLE
        var curChapter: String? = null
        var curDepth = 0
        var chapter: String? = null

        fun hasContent(bs: List<MdBlock>) = bs.any { it !is MdBlock.Heading && it !is MdBlock.Rule }

        fun flush(force: Boolean = false): List<MdBlock> {
            // 只有標題沒有內容：不成頁，標題帶到下一頁
            if (!force && !hasContent(cur)) return cur.filterIsInstance<MdBlock.Heading>()
            pages += LessonPage(curTitle, curChapter, curDepth, trimRules(cur))
            return emptyList()
        }

        for (b in blocks) {
            if (b is MdBlock.Heading && b.level in 2..split) {
                val carry = flush()
                val title = Markdown.plainText(b.text)
                if (b.level < split && b.level >= 2 && chapterLevel >= 2) {
                    chapter = title
                    curTitle = title; curChapter = null; curDepth = 1
                } else {
                    curTitle = title; curChapter = chapter; curDepth = if (chapterLevel >= 2) 2 else 1
                }
                cur = (carry + b).toMutableList()
            } else {
                cur += b
            }
        }
        if (cur.isNotEmpty()) flush(force = pages.isEmpty() || hasContent(cur))
        return pages.ifEmpty { listOf(LessonPage(INTRO_TITLE, null, 0, emptyList())) }
    }

    private fun firstHeading(blocks: List<MdBlock>) =
        (blocks.firstOrNull { it is MdBlock.Heading } as? MdBlock.Heading)?.let { Markdown.plainText(it.text) }

    private fun trimRules(bs: List<MdBlock>) = bs.dropWhile { it is MdBlock.Rule }.dropLastWhile { it is MdBlock.Rule }

    /** LLM 常把整篇包在 ```markdown … ``` 裡，去掉外層 fence */
    fun stripOuterFence(text: String): String {
        val t = text.trim()
        val m = Regex("""^```(?:markdown|md|Markdown)?[ \t]*\n([\s\S]*?)\n?```$""").find(t) ?: return text
        return m.groupValues[1]
    }

    /** 「繼續」接續貼上：去掉原文結尾的「（未完…）」提示，再接上新內容 */
    fun appendContinuation(existing: String, addition: String): String {
        val kept = existing.trimEnd().lines().dropLastWhile { it.isBlank() || it.contains("未完") }.joinToString("\n")
        val add = stripOuterFence(addition).trim()
        return if (kept.isBlank()) add else "$kept\n\n$add"
    }
}
