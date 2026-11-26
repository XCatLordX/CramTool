package com.cramtool.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class QuestionType(val label: String) {
    SINGLE("單選"),
    MULTIPLE("多選"),
    TRUE_FALSE("是非"),
    FILL("填空"),
    SHORT("簡答");

    val isChoice: Boolean get() = this == SINGLE || this == MULTIPLE || this == TRUE_FALSE
}

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 題目＋作答統計。
 *
 * answers 的意義依題型而定：
 *  - SINGLE / MULTIPLE / TRUE_FALSE：正確選項的索引（0 起算）字串，例如 ["0","2"]
 *  - FILL：所有可接受的答案文字
 *  - SHORT：參考答案（一筆）
 */
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = BankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bankId"), Index("inWrongBook")],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: Long,
    val chapter: String,
    val type: QuestionType,
    val stem: String,
    val options: List<String>,
    val answers: List<String>,
    val explanation: String,
    val orderInBank: Int,
    // ---- 統計 ----
    val attempts: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastAnsweredAt: Long? = null,
    val lastResultCorrect: Boolean? = null,
    /** 是否在錯題本中 */
    val inWrongBook: Boolean = false,
    /** 進入錯題本後連續答對的次數，達門檻即自動移出 */
    val reviewStreak: Int = 0,
    val starred: Boolean = false,
) {
    val correctIndices: Set<Int> get() = answers.mapNotNull { it.toIntOrNull() }.toSet()
}

data class BankSummary(
    @Embedded val bank: BankEntity,
    val total: Int,
    val answered: Int,
    val wrong: Int,
)

data class ChapterSummary(
    val chapter: String,
    val total: Int,
    val answered: Int,
    val wrong: Int,
    val firstOrder: Int,
)

data class WrongItem(
    @Embedded val question: QuestionEntity,
    @ColumnInfo(name = "bankName") val bankName: String,
)
