package com.cramtool.app.importer

import com.cramtool.app.data.QuestionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class ParsedQuestion(
    val chapter: String,
    val type: QuestionType,
    val stem: String,
    val options: List<String>,
    val answers: List<String>,
    val explanation: String,
)

data class ParseResult(
    val bankName: String?,
    val description: String,
    val questions: List<ParsedQuestion>,
    val warnings: List<String>,
    /** 內容被截斷（例如剪貼簿或 LLM 輸出長度限制），只救回完整的題目 */
    val truncated: Boolean = false,
)

class ParseException(message: String) : Exception(message)

/**
 * 寬鬆的題庫 JSON 解析器，盡量容忍 LLM 輸出的各種格式差異：
 *  - 外層包了 ```json ... ``` 或前後有說明文字
 *  - 根節點是物件（含 questions）或直接是陣列
 *  - 欄位名稱中英文皆可（question / stem / 題目 …）
 *  - 選項帶 "A. " "(B)" 前綴、或以 {"A": "...", "B": "..."} 物件表示
 *  - 答案用字母、字母字串（"AC"）、選項原文、數字索引、布林值表示
 */
object QuestionParser {
    const val DEFAULT_CHAPTER = "未分類"
    val TRUE_FALSE_OPTIONS = listOf("正確 (O)", "錯誤 (X)")

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val BANK_KEYS = listOf("bank", "title", "name", "subject", "題庫", "科目")
    private val DESC_KEYS = listOf("description", "desc", "說明")
    private val LIST_KEYS = listOf("questions", "items", "data", "題目")
    private val STEM_KEYS = listOf("question", "stem", "q", "text", "prompt", "題目", "題幹")
    private val OPTION_KEYS = listOf("options", "choices", "option", "選項")
    private val ANSWER_KEYS = listOf("answer", "answers", "correct", "correct_answer", "correctAnswer", "答案")
    private val INDEX_KEYS = listOf("answerIndex", "answer_index", "correct_index", "correctIndex")
    private val TYPE_KEYS = listOf("type", "question_type", "questionType", "kind", "題型")
    private val CHAPTER_KEYS = listOf("chapter", "section", "topic", "unit", "category", "章節", "範圍", "單元")
    private val EXPLAIN_KEYS = listOf("explanation", "analysis", "reason", "rationale", "solution", "解析", "詳解", "說明")

    private val OPTION_PREFIX = Regex("""^\s*[(（\[]?\s*([A-Za-z])\s*[)）\].、:：．]\s*""")

    fun parse(raw: String): ParseResult {
        val root = try {
            json.parseToJsonElement(extractJson(raw))
        } catch (e: Exception) {
            // 整份解析失敗：多半是內容被截斷，嘗試救回完整的題目
            salvage(raw)?.let { return it }
            throw ParseException("無法解析 JSON：${e.message?.lineSequence()?.firstOrNull() ?: "格式錯誤"}")
        }

        val (meta, list) = when (root) {
            is JsonArray -> null to root
            is JsonObject -> {
                val arr = LIST_KEYS.firstNotNullOfOrNull { root[it] as? JsonArray }
                    ?: chaptersAsArray(root)
                    ?: throw ParseException("找不到題目陣列（請使用 \"questions\": [...]）")
                root to arr
            }
            else -> throw ParseException("JSON 根節點必須是物件或陣列")
        }

        val warnings = mutableListOf<String>()
        val questions = mutableListOf<ParsedQuestion>()
        list.forEachIndexed { i, el ->
            val obj = el as? JsonObject
            if (obj == null) {
                warnings += "第 ${i + 1} 題：不是物件，已略過"
                return@forEachIndexed
            }
            try {
                questions += parseQuestion(obj)
            } catch (e: ParseException) {
                warnings += "第 ${i + 1} 題：${e.message}，已略過"
            }
        }
        if (questions.isEmpty()) throw ParseException(
            "沒有可匯入的題目" + if (warnings.isNotEmpty()) "\n" + warnings.joinToString("\n") else ""
        )
        return ParseResult(
            bankName = meta?.let { str(it, BANK_KEYS) }?.takeIf { it.isNotBlank() },
            description = meta?.let { str(it, DESC_KEYS) }.orEmpty(),
            questions = questions,
            warnings = warnings,
        )
    }

    /**
     * 截斷救援：找到題目陣列後，逐一取出「括號完整配對」的題目物件分別解析，
     * 遇到不完整的最後一題就停止。完全救不到題目則回傳 null。
     */
    internal fun salvage(raw: String): ParseResult? {
        val s = raw.removePrefix("\uFEFF")
        // 找題目陣列的起點：優先找 "questions": [，否則第一個 [
        val keyMatch = Regex("\"(?:${LIST_KEYS.joinToString("|")})\"\\s*:\\s*\\[").find(s)
        val arrStart = keyMatch?.let { it.range.last } ?: s.indexOf('[').takeIf { it >= 0 } ?: return null
        val objects = mutableListOf<String>()
        var depth = 0; var inStr = false; var esc = false; var objStart = -1
        var i = arrStart + 1
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                when { esc -> esc = false; c == '\\' -> esc = true; c == '"' -> inStr = false }
            } else when (c) {
                '"' -> inStr = true
                '{' -> { if (depth == 0) objStart = i; depth++ }
                '}' -> { depth--; if (depth == 0 && objStart >= 0) { objects += s.substring(objStart, i + 1); objStart = -1 } }
                ']' -> if (depth == 0) break // 陣列正常結束（不是截斷，可能是其他格式問題）
            }
            i++
        }
        val truncated = i >= s.length
        val warnings = mutableListOf<String>()
        val questions = mutableListOf<ParsedQuestion>()
        objects.forEachIndexed { idx, text ->
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (obj == null) { warnings += "第 ${idx + 1} 題：格式錯誤，已略過"; return@forEachIndexed }
            try { questions += parseQuestion(obj) } catch (e: ParseException) { warnings += "第 ${idx + 1} 題：${e.message}，已略過" }
        }
        if (questions.isEmpty()) return null
        if (truncated) warnings += "內容在第 ${objects.size + 1} 題被截斷，已救回前 ${questions.size} 題"
        fun header(keys: List<String>) = keys.firstNotNullOfOrNull { k ->
            Regex("\"$k\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(s.substring(0, arrStart))?.groupValues?.get(1)
        }?.let { v -> runCatching { (json.parseToJsonElement("\"$v\"") as JsonPrimitive).content }.getOrDefault(v) }
        return ParseResult(
            bankName = header(BANK_KEYS)?.takeIf { it.isNotBlank() },
            description = header(DESC_KEYS).orEmpty(),
            questions = questions,
            warnings = warnings,
            truncated = truncated,
        )
    }

    /** 合併多段匯入結果（分批貼上時用）：題目文字相同的視為重複，略過 */
    fun merge(a: ParseResult, b: ParseResult): Pair<ParseResult, Int> {
        val seen = a.questions.map { it.stem.trim() }.toMutableSet()
        val added = b.questions.filter { seen.add(it.stem.trim()) }
        return a.copy(
            bankName = a.bankName ?: b.bankName,
            questions = a.questions + added,
            warnings = a.warnings.filterNot { it.contains("被截斷") } + b.warnings,
            truncated = b.truncated,
        ) to (b.questions.size - added.size)
    }

    /** 支援 {"chapters":[{"name":"Ch1","questions":[...]}]} 這種巢狀格式，攤平並把章節名稱帶入 */
    private fun chaptersAsArray(root: JsonObject): JsonArray? {
        val chapters = (root["chapters"] ?: root["sections"] ?: root["章節"]) as? JsonArray ?: return null
        val flat = chapters.flatMap { c ->
            val co = c as? JsonObject ?: return@flatMap emptyList()
            val name = str(co, listOf("name", "title", "chapter", "章節")) ?: DEFAULT_CHAPTER
            val qs = LIST_KEYS.firstNotNullOfOrNull { co[it] as? JsonArray } ?: return@flatMap emptyList()
            qs.map { q ->
                if (q is JsonObject && CHAPTER_KEYS.none { q.containsKey(it) })
                    JsonObject(q + ("chapter" to JsonPrimitive(name))) else q
            }
        }
        return JsonArray(flat)
    }

    /** 從可能夾雜說明文字或 markdown code fence 的字串中取出 JSON 部分 */
    internal fun extractJson(raw: String): String {
        var s = raw.trim().removePrefix("﻿")
        val fence = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").find(s)
        if (fence != null) s = fence.groupValues[1].trim()
        val start = s.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) throw ParseException("內容中找不到 JSON")
        val close = if (s[start] == '{') '}' else ']'
        val end = s.lastIndexOf(close)
        if (end <= start) throw ParseException("JSON 不完整（可能被截斷）")
        return s.substring(start, end + 1)
    }

    private fun parseQuestion(o: JsonObject): ParsedQuestion {
        val stem = str(o, STEM_KEYS)?.trim()
        if (stem.isNullOrEmpty()) throw ParseException("缺少題目文字")

        val rawOptions = parseOptions(OPTION_KEYS.firstNotNullOfOrNull { o[it] })
        val options = stripOptionPrefixes(rawOptions)
        val answerEl = ANSWER_KEYS.firstNotNullOfOrNull { k -> o[k]?.takeUnless { it is JsonNull } }
        val indexEl = INDEX_KEYS.firstNotNullOfOrNull { o[it] }
        val explicitType = str(o, TYPE_KEYS)?.let { mapType(it) }

        val type = explicitType ?: inferType(options, answerEl)
        val chapter = str(o, CHAPTER_KEYS)?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CHAPTER
        val explanation = str(o, EXPLAIN_KEYS)?.trim().orEmpty()

        return when (type) {
            QuestionType.TRUE_FALSE -> {
                val v = answerEl?.let { toBool(it) } ?: throw ParseException("是非題答案無法判讀")
                ParsedQuestion(chapter, type, stem, TRUE_FALSE_OPTIONS, listOf(if (v) "0" else "1"), explanation)
            }
            QuestionType.SINGLE, QuestionType.MULTIPLE -> {
                if (options.size < 2) throw ParseException("選擇題至少需要 2 個選項")
                val idx = when {
                    indexEl != null -> toIndices(indexEl, options, zeroBasedNumbers = true)
                    answerEl != null -> toIndices(answerEl, options, zeroBasedNumbers = null)
                    else -> throw ParseException("缺少答案")
                }
                if (idx.isEmpty()) throw ParseException("答案無法對應到任何選項")
                val finalType = when {
                    explicitType == null -> if (idx.size > 1) QuestionType.MULTIPLE else QuestionType.SINGLE
                    type == QuestionType.SINGLE && idx.size > 1 -> QuestionType.MULTIPLE
                    else -> type
                }
                ParsedQuestion(chapter, finalType, stem, options, idx.sorted().map { it.toString() }, explanation)
            }
            QuestionType.FILL, QuestionType.SHORT -> {
                val ans = when (answerEl) {
                    null -> emptyList()
                    is JsonArray -> answerEl.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                    is JsonPrimitive -> listOfNotNull(answerEl.contentOrNull?.trim())
                    else -> emptyList()
                }.filter { it.isNotEmpty() }
                if (ans.isEmpty()) throw ParseException("缺少答案")
                ParsedQuestion(chapter, type, stem, emptyList(), ans, explanation)
            }
        }
    }

    private fun parseOptions(el: JsonElement?): List<String> = when (el) {
        is JsonArray -> el.mapNotNull { e ->
            when (e) {
                is JsonPrimitive -> e.contentOrNull
                is JsonObject -> str(e, listOf("text", "content", "label", "value"))
                else -> null
            }
        }
        // {"A": "...", "B": "..."}
        is JsonObject -> el.entries.sortedBy { it.key }.mapNotNull { (it.value as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> el.contentOrNull?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        else -> emptyList()
    }

    /** 若所有選項皆以 A. B. C. … 依序開頭，就把前綴拿掉 */
    internal fun stripOptionPrefixes(options: List<String>): List<String> {
        if (options.isEmpty()) return options
        val allPrefixed = options.withIndex().all { (i, s) ->
            val m = OPTION_PREFIX.find(s)
            m != null && m.groupValues[1].uppercase()[0] == 'A' + i
        }
        return if (allPrefixed) options.map { it.replaceFirst(OPTION_PREFIX, "").trim() } else options.map { it.trim() }
    }

    private fun mapType(t: String): QuestionType? {
        val s = t.lowercase().replace(Regex("[\\s_\\-]"), "")
        return when {
            s in setOf("single", "singlechoice", "mcq", "choice", "multiplechoice", "radio", "sc") || s.contains("單選") -> QuestionType.SINGLE
            s in setOf("multiple", "multi", "multiselect", "multiplechoicemultiple", "checkbox", "multianswer", "mc", "multipleanswer", "multipleselect") || s.contains("多選") || s.contains("複選") -> QuestionType.MULTIPLE
            s in setOf("truefalse", "tf", "boolean", "bool", "judge", "yesno") || s.contains("是非") || s.contains("判斷") -> QuestionType.TRUE_FALSE
            s in setOf("fill", "blank", "fillin", "fillintheblank", "fillblank", "cloze") || s.contains("填空") -> QuestionType.FILL
            s in setOf("short", "shortanswer", "essay", "open", "openended", "text") || s.contains("簡答") || s.contains("問答") || s.contains("申論") -> QuestionType.SHORT
            else -> null
        }
    }

    private fun inferType(options: List<String>, answer: JsonElement?): QuestionType {
        if (answer is JsonPrimitive && answer.booleanOrNull != null) return QuestionType.TRUE_FALSE
        if (options.size >= 2) {
            val multi = when (answer) {
                is JsonArray -> answer.size > 1
                is JsonPrimitive -> {
                    val c = answer.contentOrNull.orEmpty().trim()
                    val letters = c.uppercase().filter { it.isLetter() }
                    Regex("^[A-Za-z](\\s*[,，、/&]?\\s*[A-Za-z])+$").matches(c) &&
                        letters.all { it in 'A' until 'A' + options.size } &&
                        options.none { it.equals(c, ignoreCase = true) }
                }
                else -> false
            }
            return if (multi) QuestionType.MULTIPLE else QuestionType.SINGLE
        }
        if (answer is JsonPrimitive && answer.isString && toBool(answer) != null && answer.content.length <= 5) {
            return QuestionType.TRUE_FALSE
        }
        val text = (answer as? JsonPrimitive)?.contentOrNull.orEmpty()
        return if (text.length > 40) QuestionType.SHORT else QuestionType.FILL
    }

    private fun toBool(el: JsonElement): Boolean? {
        val p = el as? JsonPrimitive ?: (el as? JsonArray)?.firstOrNull() as? JsonPrimitive ?: return null
        p.booleanOrNull?.let { return it }
        return when (p.content.trim().lowercase()) {
            "true", "t", "o", "○", "yes", "y", "對", "是", "正確", "√", "✓", "1" -> true
            "false", "f", "x", "×", "no", "n", "錯", "否", "錯誤", "✗", "0" -> false
            else -> null
        }
    }

    /**
     * @param zeroBasedNumbers true=數字一律視為 0 起算；null=自動判斷（出現 0 → 0 起算；出現 size → 1 起算；否則 0 起算）
     */
    private fun toIndices(el: JsonElement, options: List<String>, zeroBasedNumbers: Boolean?): Set<Int> {
        val prims: List<JsonPrimitive> = when (el) {
            is JsonArray -> el.mapNotNull { it as? JsonPrimitive }
            is JsonPrimitive -> listOf(el)
            else -> emptyList()
        }
        val n = options.size
        val numbers = prims.filter { !it.isString }.mapNotNull { it.intOrNull }
        val oneBased = when (zeroBasedNumbers) {
            true -> false
            else -> numbers.none { it == 0 } && numbers.any { it == n }
        }
        val result = mutableSetOf<Int>()
        for (p in prims) {
            if (!p.isString) {
                p.intOrNull?.let { v -> (if (oneBased) v - 1 else v).takeIf { it in 0 until n }?.let(result::add) }
                continue
            }
            result += stringToIndices(p.content.trim(), options)
        }
        return result
    }

    private fun stringToIndices(s: String, options: List<String>): Set<Int> {
        val n = options.size
        if (s.isEmpty()) return emptySet()
        // 與選項原文完全相同
        val exact = options.indexOfFirst { it.equals(s, ignoreCase = true) }
        if (exact >= 0) return setOf(exact)
        // 字母開頭但後面接選項內容，例如 "B. 堆疊"
        OPTION_PREFIX.find(s)?.let { m ->
            val rest = s.substring(m.range.last + 1).trim()
            val i = m.groupValues[1].uppercase()[0] - 'A'
            if (rest.isNotEmpty() && i in 0 until n) return setOf(i)
        }
        // 純字母：「B」「AC」「A, C」「(A)(C)」
        val letters = s.uppercase().filter { it.isLetter() }
        val cleaned = s.uppercase().replace(Regex("[\\s,，、/&()（）\\[\\]和及與]|AND"), "")
        if (letters.isNotEmpty() && cleaned == letters && letters.all { it in 'A' until 'A' + n }) {
            return letters.map { it - 'A' }.toSet()
        }
        // 數字字串「2」視為 1 起算（人類習慣）
        s.toIntOrNull()?.let { v -> if (v in 1..n) return setOf(v - 1) }
        // 選項內容包含
        val contains = options.indexOfFirst { it.isNotBlank() && (s.contains(it) || it.contains(s)) }
        if (contains >= 0) return setOf(contains)
        return emptySet()
    }

    private fun str(o: JsonObject, keys: List<String>): String? = keys.firstNotNullOfOrNull { k ->
        when (val v = o[k]) {
            is JsonPrimitive -> v.contentOrNull
            is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n").takeIf { it.isNotEmpty() }
            else -> null
        }
    }
}
