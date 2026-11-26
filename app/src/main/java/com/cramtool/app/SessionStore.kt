package com.cramtool.app

import kotlinx.coroutines.flow.MutableStateFlow

/** 一次刷題的設定：要做哪些題目（已排序） */
data class QuizRequest(
    val title: String,
    val questionIds: List<Long>,
    /** 錯題複習模式：結果頁會顯示移出錯題本的題數 */
    val isReview: Boolean = false,
)

/** 畫面間傳遞較大資料用的簡單記憶體存放區 */
object SessionStore {
    var quiz: QuizRequest? = null
    /** 由分享／開啟檔案帶進來、等待匯入的文字 */
    val pendingImport = MutableStateFlow<String?>(null)
}
