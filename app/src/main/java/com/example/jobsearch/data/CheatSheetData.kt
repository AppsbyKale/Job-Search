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
            val scrubbed = JsonScrubber.scrub(jsonStr)
            val json = JSONObject(scrubbed)
            parseFromObject(json)
        }.getOrNull()

        fun parseFromObject(obj: JSONObject): ToughQuestion {
            val q = obj.optString("question").ifBlank {
                obj.optString("q").ifBlank {
                    obj.optString("title").ifBlank { obj.optString("prompt") }
                }
            }
            val s = obj.optString("strategy").ifBlank {
                obj.optString("approach").ifBlank {
                    obj.optString("tip").ifBlank { obj.optString("notes") }
                }
            }
            val a = obj.optString("exampleAnswer").ifBlank {
                obj.optString("example_answer").ifBlank {
                    obj.optString("answer").ifBlank { obj.optString("sampleAnswer").ifBlank { obj.optString("a") } }
                }
            }
            return ToughQuestion(q.trim(), s.trim(), a.trim())
        }
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
                if (tq.strategy.isNotBlank()) {
                    sb.append("STRATEGY: ${tq.strategy}\n")
                }
                if (tq.exampleAnswer.isNotBlank()) {
                    sb.append("EXAMPLE ANSWER:\n${tq.exampleAnswer}\n")
                }
                sb.append("\n")
            }
        }
        return sb.toString().trim()
    }

    companion object {
        fun fromJson(jsonStr: String): CheatSheetData? {
            val parsed = runCatching {
                val scrubbed = JsonScrubber.scrub(jsonStr)
                if (scrubbed.isBlank() || !scrubbed.contains("{")) return@runCatching null
                val json = JSONObject(scrubbed)
                
                val highlights = mutableListOf<String>()
                val hlKeys = listOf("keyHighlights", "key_highlights", "highlights", "sellingPoints", "selling_points", "keyPoints", "key_points")
                for (key in hlKeys) {
                    val array = json.optJSONArray(key)
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val str = array.optString(i).trim()
                            if (str.isNotBlank()) highlights.add(str)
                        }
                        if (highlights.isNotEmpty()) break
                    }
                }

                val questions = mutableListOf<ToughQuestion>()
                val qKeys = listOf("toughQuestions", "tough_questions", "questions", "interviewQuestions", "interview_questions", "qa")
                for (key in qKeys) {
                    val array = json.optJSONArray(key)
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i)
                            if (obj != null) {
                                val tq = ToughQuestion.parseFromObject(obj)
                                if (tq.question.isNotBlank() || tq.strategy.isNotBlank()) {
                                    questions.add(tq)
                                }
                            }
                        }
                        if (questions.isNotEmpty()) break
                    }
                }
                CheatSheetData(highlights, questions)
            }.onFailure {
                Log.e("CheatSheetData", "Failed to parse JSON", it)
            }.getOrNull()

            if (parsed != null && parsed.isSubstantial()) {
                return parsed
            }

            // Fallback to text parser if JSON parsing failed or yielded empty lists
            return fromText(jsonStr)
        }

        fun fromText(text: String): CheatSheetData? {
            if (text.isBlank()) return null

            val highlights = mutableListOf<String>()
            val questions = mutableListOf<ToughQuestion>()

            var currentMode = "" // "highlights" or "questions"
            var currentQ = ""
            var currentS = ""
            var currentA = StringBuilder()

            fun commitQuestion() {
                if (currentQ.isNotBlank() || currentS.isNotBlank() || currentA.isNotBlank()) {
                    questions.add(ToughQuestion(currentQ.trim(), currentS.trim(), currentA.toString().trim()))
                    currentQ = ""
                    currentS = ""
                    currentA = StringBuilder()
                }
            }

            val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
            for (rawLine in lines) {
                val line = rawLine.trim()
                val lower = line.lowercase()

                if (lower.contains("key highlight") || lower.contains("highlights") || lower.contains("selling point")) {
                    commitQuestion()
                    currentMode = "highlights"
                    continue
                } else if (lower.contains("tough question") || lower.contains("questions &") || lower.contains("interview cheat sheet")) {
                    commitQuestion()
                    currentMode = "questions"
                    continue
                }

                if (currentMode == "highlights") {
                    val clean = line.removePrefix("•").removePrefix("-").removePrefix("*").trim()
                    if (clean.isNotBlank()) {
                        highlights.add(clean)
                    }
                } else {
                    if (line.startsWith("Q:") || line.startsWith("Question:")) {
                        commitQuestion()
                        currentQ = line.substringAfter(":").trim()
                    } else if (line.startsWith("STRATEGY:") || line.startsWith("Strategy:")) {
                        currentS = line.substringAfter(":").trim()
                    } else if (line.startsWith("EXAMPLE ANSWER:") || line.startsWith("Example Answer:")) {
                        val remaining = line.substringAfter(":").trim()
                        if (remaining.isNotBlank()) currentA.append(remaining).append("\n")
                    } else if (currentQ.isNotBlank()) {
                        currentA.append(line).append("\n")
                    } else if (line.isNotBlank()) {
                        val clean = line.removePrefix("•").removePrefix("-").removePrefix("*").trim()
                        if (clean.isNotBlank()) highlights.add(clean)
                    }
                }
            }
            commitQuestion()

            val data = CheatSheetData(highlights, questions)
            return if (data.isSubstantial()) data else null
        }

        fun extractJson(text: String): String = JsonScrubber.scrub(text)
    }
}
