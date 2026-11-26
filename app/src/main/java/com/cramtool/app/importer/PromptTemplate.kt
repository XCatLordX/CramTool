package com.cramtool.app.importer

object PromptTemplate {
    val text = """
請根據我提供的課程內容（講義／課本章節／筆記），幫我出期中考練習題，並「只輸出」一個 JSON（可以放在 ```json 區塊中），不要有其他說明文字。

格式如下：
{
  "bank": "題庫名稱（例如：資料結構 期中考）",
  "description": "簡短說明（可省略）",
  "questions": [
    {
      "chapter": "章節名稱，例如 Ch2 Linked List",
      "type": "single",
      "question": "題目內容",
      "options": ["選項一", "選項二", "選項三", "選項四"],
      "answer": "B",
      "explanation": "解析：為什麼是這個答案、其他選項錯在哪"
    }
  ]
}

規則：
1. type 只能是：single（單選）、multiple（多選）、truefalse（是非）、fill（填空）、short（簡答）。
2. 單選 answer 用一個字母，例如 "B"；多選用字母陣列，例如 ["A","C"]。
3. options 內不要加 "A." "(B)" 之類的編號。
4. 是非題 answer 用 true 或 false，不需要 options。
5. 填空題 answer 用字串陣列，列出所有可接受的寫法，例如 ["O(n log n)", "nlogn"]。
6. 簡答題 answer 放參考答案（一段文字）。
7. 每題都要有 chapter 與 explanation，chapter 名稱同一章要完全一致，方便我依章節挑範圍練習。
8. 題目請涵蓋重要觀念，難度接近大學期中考，題數：［請填，例如每章 10 題］。
9. 題數超過 20 題時請分批輸出：每批最多 20 題，每批都是一份「完整、可單獨解析」的 JSON（含 bank 與 questions）。
   輸出完一批就停下，等我回覆「繼續」再輸出下一批，不要重複已出過的題目。

課程內容如下：
［在這裡貼上講義內容，或說明要出題的範圍］
""".trimIndent()
}
