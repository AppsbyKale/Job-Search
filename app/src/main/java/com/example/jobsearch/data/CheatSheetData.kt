package com.example.jobsearch.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ToughQuestion(
    val question: String,
    val strategy: String,
    val exampleAnswer: String
) {
    companion object {
        fun fromJson(jsonStr: String): ToughQuestion? = runCatching {
            val json = JSONObject(JsonScrubber.scrub(jsonStr))
            ToughQuestion(
                question = json.getString("question"),
                strategy = json.getString("strategy"),
                exampleAnswer = json.optString("exampleAnswer")
            )
        }.getOrNull()
    }
}

data class CheatSheetData(
    val keyHighlights: List<String> = emptyList(),
    val toughQuestions: List<ToughQuestion> = emptyList()
) {
    fun isSubstantial(): Boolean = keyHighlights.isNotEmpty() || toughQuestions.isNotEmpty()

    fun toJson(): String {
        val json = JSONObject()
        json.put("keyHighlights", JSONArray(keyHighlights))
        val questionsArray = JSONArray()
        toughQuestions.forEach { tq ->
            val obj = JSONObject()
            obj.put("question", tq.question)
            obj.put("strategy", tq.strategy)
            obj.put("exampleAnswer", tq.exampleAnswer)
            questionsArray.put(obj)
        }
        json.put("toughQuestions", questionsArray)
        return json.toString()
    }

    fun toHumanReadableText(): String {
        val sb = StringBuilder()
        if (keyHighlights.isNotEmpty()) {
            sb.append("KEY HIGHLIGHTS\n")
            keyHighlights.forEach { sb.append("• $it\n") }
            sb.append("\n")
        }
        if (toughQuestions.isNotEmpty()) {
            sb.append("TOUGH QUESTIONS & STRATEGIES\n")
            toughQuestions.forEach { tq ->
                sb.append("Q: ${tq.question}\n")
                sb.append("STRATEGY: ${tq.strategy}\n")
                if (tq.exampleAnswer.isNotBlank()) {
                    sb.append("EXAMPLE ANSWER:\n${tq.exampleAnswer}\n")
                }
                sb.append("\n")
            }
        }
        return sb.toString().trim()
    }

    companion object {
        fun fromJson(jsonStr: String): CheatSheetData? = runCatching {
            val scrubbed = JsonScrubber.scrub(jsonStr)
            val json = JSONObject(scrubbed)
            val highlights = mutableListOf<String>()
            val hlArray = json.optJSONArray("keyHighlights")
            if (hlArray != null) {
                for (i in 0 until hlArray.length()) {
                    highlights.add(hlArray.getString(i))
                }
            }

            val questions = mutableListOf<ToughQuestion>()
            val qArray = json.optJSONArray("toughQuestions")
            if (qArray != null) {
                for (i in 0 until qArray.length()) {
                    val obj = qArray.getJSONObject(i)
                    questions.add(ToughQuestion(
                        question = obj.getString("question"),
                        strategy = obj.getString("strategy"),
                        exampleAnswer = obj.optString("exampleAnswer")
                    ))
                }
            }
            CheatSheetData(highlights, questions)
        }.onFailure {
            Log.e("CheatSheetData", "Failed to parse JSON", it)
        }.getOrNull()

        fun extractJson(text: String): String = JsonScrubber.scrub(text)
    }
}
