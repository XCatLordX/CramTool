# CramTool 刷題

一個 Android 考前複習 App：把 LLM 幫你出的期中考題目匯入，依章節挑範圍練習，自動收集錯題並複習；
也能存放 LLM 整理的 Markdown 筆記，用資料夾分類，像閱讀器一樣用大綱快速跳轉。

## 功能

- **匯入題庫**：選擇 `.json` 檔、從剪貼簿貼上，或在 ChatGPT／Claude App 中「分享」文字給 CramTool。
  內建「出題提示詞」一鍵複製，貼給 LLM 就會產出正確格式（見 [docs/LLM_PROMPT.md](docs/LLM_PROMPT.md)）。
  可建立新題庫，或把新題目加入既有題庫。
- **五種題型**：單選、多選、是非、填空（自動批改，忽略大小寫／全半形／空白，可手動改判）、簡答（看參考答案後自評）。
- **挑範圍刷題**：勾選章節 × 題目來源（全部／未作答／錯題本／曾答錯／已收藏）× 題數（10/20/50/全部）× 隨機或依序。
- **錯題本**：答錯自動收錄；之後再答對達門檻次數（預設 2，可調 1/2/3/5）自動移出，也可手動移出。
  可依題庫／章節篩選後複習。
- **練習結果**：正確率、本次錯題與解析，一鍵「重做錯題」或「同一批再刷一次」。
- **瀏覽題庫**：搜尋題目、查看答案與解析、收藏題目。

- **筆記**（下方分頁切換）
  - 新增筆記：從剪貼簿貼上、選擇 `.md`／`.txt` 檔，或從 LLM App 直接「分享」過來（非題庫 JSON 的文字會自動當作筆記）。
    內建「整理筆記提示詞」，讓 LLM 用 `#`／`##`／`###` 分層整理。
  - 閱讀器：支援標題、條列、待辦、引用、表格、程式碼、數學式、粗體／斜體／行內程式碼；
    「大綱」按鈕列出所有標題，點擊即跳轉；頂部顯示目前章節與閱讀進度；自動記住讀到哪裡；可調整字體大小；可選取文字複製。
  - 檔案整理：可巢狀的資料夾；筆記與資料夾都能重新命名、移動、刪除（刪資料夾時內容會移到上一層，不會遺失）；
    依最近更新／最近閱讀／名稱排序；全文搜尋所有筆記。

## 題庫 JSON 格式

```json
{
  "bank": "資料結構 期中考",
  "questions": [
    { "chapter": "Ch1", "type": "single",    "question": "…", "options": ["…","…","…","…"], "answer": "B", "explanation": "…" },
    { "chapter": "Ch1", "type": "multiple",  "question": "…", "options": ["…","…","…"], "answer": ["A","C"] },
    { "chapter": "Ch2", "type": "truefalse", "question": "…", "answer": true },
    { "chapter": "Ch2", "type": "fill",      "question": "…", "answer": ["O(n)", "O(N)"] },
    { "chapter": "Ch3", "type": "short",     "question": "…", "answer": "參考答案…" }
  ]
}
```

完整範例：[samples/sample_bank.json](samples/sample_bank.json)。`type` 可省略，會依選項與答案自動判斷。

## 建置

需求：JDK 17+、Android SDK（compileSdk 35）。

```bash
./gradlew assembleDebug          # 產出 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 解析器、批改與 UI 流程測試（Robolectric）
```

推送到 GitHub 後，Actions 會自動建置 APK，可在該次 workflow 的 Artifacts 下載。

## 技術

Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose · kotlinx.serialization

## 規劃中

- PDF 閱讀（講義對照）
- 筆記與題庫章節互相連結
- 匯出／備份題庫與作答紀錄
- 間隔重複（SRS）排程
