package com.example.jobsearch.data

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
    val aboutCompany: String = "",
    val relevantSkills: List<String> = emptyList(),
    val keyHighlights: List<String> = emptyList(),
    val toughQuestions: List<ToughQuestion> = emptyList(),
    val notes: String = ""
) {
    fun isSubstantial(): Boolean = aboutCompany.isNotBlank() || relevantSkills.isNotEmpty() || keyHighlights.isNotEmpty() || toughQuestions.isNotEmpty() || notes.isNotBlank()

    fun toJson(): String {
        val json = JSONObject().apply {
            put("aboutCompany", aboutCompany)
            put("relevantSkills", JSONArray(relevantSkills))
            put("keyHighlights", JSONArray(keyHighlights))
            val questionsArray = JSONArray()
            toughQuestions.forEach { tq ->
                val obj = JSONObject()
                obj.put("question", tq.question)
                obj.put("strategy", tq.strategy)
                obj.put("exampleAnswer", tq.exampleAnswer)
                questionsArray.put(obj)
            }
            put("toughQuestions", questionsArray)
            put("notes", notes)
        }
        return json.toString()
    }

    fun toHumanReadableText(): String {
        val sb = StringBuilder()
        
        sb.append("ABOUT THE COMPANY\n")
        if (aboutCompany.isNotBlank()) {
            sb.append("$aboutCompany\n\n")
        } else {
            sb.append("No company overview available.\n\n")
        }

        if (relevantSkills.isNotEmpty()) {
            sb.append("RELEVANT SKILLS\n")
            sb.append(relevantSkills.joinToString(", ")).append("\n\n")
        }

        if (keyHighlights.isNotEmpty()) {
            sb.append("KEY HIGHLIGHTS\n")
            keyHighlights.forEach { sb.append("• $it\n") }
            sb.append("\n")
        }

        if (toughQuestions.isNotEmpty()) {
            sb.append("Q&A\n")
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

        if (notes.isNotBlank()) {
            sb.append("NOTES\n")
            sb.append("$notes\n")
        }

        return sb.toString().trim()
    }

    companion object {
        fun fromJson(jsonStr: String): CheatSheetData? {
            val parsed = runCatching {
                val scrubbed = JsonScrubber.scrub(jsonStr)
                if (scrubbed.isBlank() || !scrubbed.contains("{")) return@runCatching null
                val json = JSONObject(scrubbed)
                
                val about = json.optString("aboutCompany", "").ifBlank { json.optString("about_company", "") }

                val skills = mutableListOf<String>()
                val skillKeys = listOf("relevantSkills", "relevant_skills", "skills")
                for (key in skillKeys) {
                    val array = json.optJSONArray(key)
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val str = array.optString(i).trim()
                            if (str.isNotBlank()) skills.add(str)
                        }
                        if (skills.isNotEmpty()) break
                    }
                }

                val highlights = mutableListOf<String>()
                val hlKeys = listOf("keyHighlights", "key_highlights", "highlights", "sellingPoints")
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
                val qKeys = listOf("toughQuestions", "tough_questions", "questions", "qa")
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

                val notesStr = json.optString("notes", "").trim()

                CheatSheetData(about, skills, highlights, questions, notesStr)
            }.getOrNull()

            if (parsed != null && parsed.isSubstantial()) {
                return parsed
            }

            return fromText(jsonStr)
        }

        fun fromText(text: String): CheatSheetData? {
            if (text.isBlank()) return null

            var about = ""
            val skills = mutableListOf<String>()
            val highlights = mutableListOf<String>()
            val questions = mutableListOf<ToughQuestion>()
            val notesBuilder = StringBuilder()

            var section = 0 // 1: About, 2: Skills, 3: Highlights, 4: QA, 5: Notes
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

            val notesIdx = text.indexOf("NOTES", ignoreCase = true)
            val mainText = if (notesIdx >= 0) {
                notesBuilder.append(text.substring(notesIdx + 5).removePrefix(":").trim())
                text.substring(0, notesIdx)
            } else {
                text
            }

            val lines = mainText.replace("\r\n", "\n").replace('\r', '\n').split("\n")
            for (rawLine in lines) {
                val line = rawLine.trim()
                val lower = line.lowercase()

                if (lower.contains("about the company") || lower.contains("about company")) {
                    commitQuestion()
                    section = 1
                    continue
                } else if (lower.contains("relevant skill") || lower.contains("skills")) {
                    commitQuestion()
                    section = 2
                    continue
                } else if (lower.contains("key highlight") || lower.contains("highlights")) {
                    commitQuestion()
                    section = 3
                    continue
                } else if (lower.contains("q&a") || lower.contains("tough question") || lower.contains("questions")) {
                    commitQuestion()
                    section = 4
                    continue
                }

                when (section) {
                    1 -> {
                        if (line.isNotBlank() && !line.startsWith("---") && !line.startsWith("===")) {
                            about = if (about.isBlank()) line else "$about $line"
                        }
                    }
                    2 -> {
                        if (line.isNotBlank() && !line.startsWith("---") && !line.startsWith("===")) {
                            line.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { skills.add(it) }
                        }
                    }
                    3 -> {
                        val clean = line.removePrefix("•").removePrefix("-").removePrefix("*").trim()
                        if (clean.isNotBlank() && !clean.startsWith("---") && !clean.startsWith("===")) {
                            highlights.add(clean)
                        }
                    }
                    4 -> {
                        if (line.startsWith("Q:") || line.startsWith("Question:") || line.startsWith("q:")) {
                            commitQuestion()
                            currentQ = line.substringAfter(":").trim()
                        } else if (line.startsWith("STRATEGY:") || line.startsWith("Strategy:") || line.startsWith("strategy:")) {
                            currentS = line.substringAfter(":").trim()
                        } else if (line.startsWith("EXAMPLE ANSWER:") || line.startsWith("Example Answer:") || line.startsWith("exampleAnswer:")) {
                            currentA.append(line.substringAfter(":").trim()).append("\n")
                        } else if (currentA.toString().isNotBlank() && line.isNotBlank() && !line.contains(":") && !line.startsWith("Q")) {
                            commitQuestion()
                            section = 5
                            notesBuilder.append(rawLine).append("\n")
                        } else if (currentQ.isNotBlank()) {
                            currentA.append(line).append("\n")
                        }
                    }
                }
            }
            commitQuestion()

            return CheatSheetData(about, skills, highlights, questions, notesBuilder.toString().trim())
        }

        fun extractJson(text: String): String = JsonScrubber.scrub(text)
    }
}
