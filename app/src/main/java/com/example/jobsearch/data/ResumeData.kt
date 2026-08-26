package com.example.jobsearch.data

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private val jsonFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    allowSpecialFloatingPointValues = true
    coerceInputValues = true
}

/**
 * Aggressive cleaning for AI-generated JSON that often contains markdown markers,
 * conversational filler, or double-bracing.
 */
object JsonScrubber {
    fun scrub(text: String): String {
        Log.d("JobSearch", "JsonScrubber.scrub: input length=${text.length}")
        // 1. Find the largest balanced JSON block
        val blocks = mutableListOf<String>()
        for (i in text.indices) {
            if (text[i] == '{') {
                var balance = 0
                var inString = false
                var isEscaped = false
                for (j in i until text.length) {
                    val c = text[j]
                    if (c == '\\' && !isEscaped) {
                        isEscaped = true
                        continue
                    }
                    if (c == '\"' && !isEscaped) inString = !inString
                    if (!inString) {
                        if (c == '{') balance++
                        else if (c == '}') balance--
                    }
                    isEscaped = false
                    if (balance == 0) {
                        blocks.add(text.substring(i, j + 1))
                        break
                    }
                }
            }
        }
        
        val rawJson = blocks.maxByOrNull { it.length } ?: text.trim()
        Log.d("JobSearch", "JsonScrubber.scrub: balanced block found=${blocks.isNotEmpty()}, length=${rawJson.length}")

        val clean = rawJson.lines()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
        Log.d("JobSearch", "JsonScrubber.scrub: before escaping length=${clean.length}, preview=${clean.take(100).replace("\n", "\\n")}")

        val result = escapeInternalQuotesAndWhitespace(clean)
        Log.d("JobSearch", "JsonScrubber.scrub: after escaping length=${result.length}, preview=${result.take(100).replace("\n", "\\n")}")
        return result
    }

    /**
     * State machine to escape internal quotes and raw whitespace.
     */
    private fun escapeInternalQuotesAndWhitespace(json: String): String {
        val sb = StringBuilder()
        var inString = false
        var isEscaped = false
        
        for (i in json.indices) {
            val char = json[i]
            
            if (char == '\\' && !isEscaped) {
                sb.append(char)
                isEscaped = true
                continue
            }

            if (char == '\"' && !isEscaped) {
                if (!inString) {
                    inString = true
                    sb.append(char)
                } else {
                    // Possible end of string or internal quote.
                    // Look ahead for structural characters: , } ]
                    // A closing quote of a string value is almost always followed by one of these.
                    // A quote followed by a colon is a key, which we assume is correctly quoted.
                    var j = i + 1
                    while (j < json.length && json[j].isWhitespace()) j++
                    val next = json.getOrNull(j)
                    val isEnd = next == ',' || next == '}' || next == ']' || next == null
                    
                    if (isEnd) {
                        inString = false
                        sb.append(char)
                    } else {
                        // If it's followed by a colon, it might be a key.
                        if (next == ':') {
                            inString = false
                            sb.append(char)
                        } else {
                            sb.append("\\\"")
                        }
                    }
                }
            } else if (inString) {
                when (char) {
                    '\n' -> sb.append("\\n")
                    '\r' -> {}
                    '\t' -> sb.append("\\t")
                    else -> sb.append(char)
                }
            } else {
                sb.append(char)
            }
            isEscaped = false
        }
        return sb.toString()
    }
}

@Serializable
data class ResumeData(
    val name: String = "",
    val contact: String = "",
    val summary: String = "",
    @Serializable(with = SkillCategoryListSerializer::class)
    val skills: List<SkillCategory> = emptyList(),
    val experience: List<ExperienceItem> = emptyList(),
    val education: List<EducationItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList()
) {
    fun toJson(): String = jsonFormat.encodeToString(this)

    fun isSubstantial(): Boolean = summary.isNotBlank() || 
            experience.isNotEmpty() || 
            skills.isNotEmpty() || 
            education.isNotEmpty() ||
            projects.isNotEmpty()

    fun toHumanReadableText(): String = buildString {
        if (name.isNotBlank()) appendLine(name)
        if (contact.isNotBlank()) appendLine(contact)
        if (name.isNotBlank() || contact.isNotBlank()) appendLine()

        if (summary.isNotBlank()) {
            appendLine("SUMMARY")
            appendLine(summary)
            appendLine()
        }

        if (skills.isNotEmpty()) {
            appendLine("SKILLS")
            for (cat in skills) {
                if (cat.name.isNotBlank()) appendLine(cat.name)
                appendLine("- " + cat.skills.joinToString(", "))
            }
            appendLine()
        }

        if (experience.isNotEmpty()) {
            appendLine("EXPERIENCE")
            for (exp in experience) {
                val h = listOf(exp.title, exp.company, exp.location).filter { it.isNotBlank() }.joinToString(" | ")
                appendLine(h)
                if (exp.dates.isNotBlank()) appendLine(exp.dates)
                for (bullet in exp.bullets) appendLine("- $bullet")
                appendLine()
            }
        }

        if (education.isNotEmpty()) {
            appendLine("EDUCATION")
            for (edu in education) {
                val h = listOf(edu.degree, edu.school).filter { it.isNotBlank() }.joinToString(", ")
                appendLine(h)
                if (edu.dates.isNotBlank()) appendLine(edu.dates)
                appendLine()
            }
        }

        if (projects.isNotEmpty()) {
            appendLine("PROJECTS")
            for (proj in projects) {
                appendLine(proj.name)
                for (bullet in proj.bullets) appendLine("- $bullet")
                appendLine()
            }
        }
    }.trim()

    companion object {
        fun fromJson(json: String): ResumeData? = try {
            val clean = extractJson(json)
            jsonFormat.decodeFromString<ResumeData>(clean)
        } catch (e: Exception) {
            Log.d("JobSearch", "ResumeData.fromJson: decode failed: ${e.message}")
            null
        }

        fun extractJson(text: String): String = JsonScrubber.scrub(text)

        /**
         * Fallback: if the text is not JSON, try to parse it using the old
         * line-based section logic so existing resumes don't break.
         */
        fun fromText(text: String): ResumeData {
            val fromJson = fromJson(text)
            if (fromJson != null) {
                Log.d("JobSearch", "ResumeData.fromText: path=JSON")
                return fromJson
            }

            Log.d("JobSearch", "ResumeData.fromText: path=HEURISTIC")
            val trimmed = text.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("```json")) {
                // If it looks like JSON but failed, don't try to parse it as headers.
                // This prevents "Data Loss" where the first line of JSON is taken as a name.
                return ResumeData() 
            }

            val sections = mutableListOf<Pair<String, List<String>>>()
            var currentHeader = ""
            var currentLines = mutableListOf<String>()
            
            for (line in text.replace("\r\n", "\n").split("\n")) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                // Check if line starts with a known header followed by content on same line
                var matchedHeader = ""
                for (h in KNOWN_HEADERS) {
                    if (trimmed.lowercase().startsWith("$h:")) {
                        matchedHeader = h
                        break
                    }
                }

                if (matchedHeader.isNotEmpty()) {
                    sections.add(currentHeader to currentLines)
                    currentHeader = matchedHeader
                    val content = trimmed.substring(matchedHeader.length + 1).trim()
                    currentLines = if (content.isNotEmpty()) mutableListOf(content) else mutableListOf()
                } else {
                    val lower = trimmed.lowercase().trimEnd(':')
                    if (lower in KNOWN_HEADERS) {
                        sections.add(currentHeader to currentLines)
                        currentHeader = lower
                        currentLines = mutableListOf()
                    } else {
                        currentLines.add(line)
                    }
                }
            }
            sections.add(currentHeader to currentLines)

            var name = ""
            var contact = ""
            var summary = ""
            val skills = mutableListOf<SkillCategory>()
            val experience = mutableListOf<ExperienceItem>()
            val education = mutableListOf<EducationItem>()
            val projects = mutableListOf<ProjectItem>()

            for ((header, lines) in sections) {
                when (header) {
                    "" -> {
                        val nonBlank = lines.filter { it.isNotBlank() }
                        name = nonBlank.getOrNull(0)?.trim() ?: ""
                        contact = nonBlank.getOrNull(1)?.trim() ?: ""
                    }
                    "summary", "professional summary" -> {
                        summary = lines.joinToString(" ").trim().replace(Regex("\\s+"), " ")
                    }
                    "skills", "core skills" -> {
                        var catName = ""
                        for (line in lines) {
                            val t = line.trim()
                            if (t.isEmpty()) continue
                            if (t.startsWith("-")) {
                                val s = t.removePrefix("-").trim().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                skills.add(SkillCategory(catName, s))
                                catName = ""
                            } else {
                                catName = t
                            }
                        }
                    }
                    "experience", "relevant work experience", "work experience" -> {
                        var i = 0
                        while (i < lines.size) {
                            val headerLine = lines[i].trim()
                            if (headerLine.isEmpty()) { i++; continue }
                            val datesLine = lines.getOrNull(i+1)?.trim() ?: ""
                            val bullets = mutableListOf<String>()
                            var j = i + 2
                            while (j < lines.size && (lines[j].trim().startsWith("-") || lines[j].isBlank())) {
                                if (lines[j].isNotBlank()) bullets.add(lines[j].trim().removePrefix("-").trim())
                                j++
                            }
                            val parts = headerLine.split("|").map { it.trim() }
                            experience.add(ExperienceItem(
                                title = parts.getOrNull(0) ?: "",
                                company = parts.getOrNull(1) ?: "",
                                location = parts.getOrNull(2) ?: "",
                                dates = datesLine,
                                bullets = bullets
                            ))
                            i = j
                        }
                    }
                    "education" -> {
                        var i = 0
                        while (i < lines.size) {
                            val deg = lines[i].trim()
                            if (deg.isEmpty()) { i++; continue }
                            val dates = lines.getOrNull(i+1)?.trim() ?: ""
                            education.add(EducationItem(deg, "", dates)) // Old format didn't split deg/school well
                            i += 2
                        }
                    }
                    "projects" -> {
                        var i = 0
                        while (i < lines.size) {
                            val pname = lines[i].trim()
                            if (pname.isEmpty()) { i++; continue }
                            val bullets = mutableListOf<String>()
                            var j = i + 1
                            while (j < lines.size && (lines[j].trim().startsWith("-") || lines[j].isBlank())) {
                                if (lines[j].isNotBlank()) bullets.add(lines[j].trim().removePrefix("-").trim())
                                j++
                            }
                            projects.add(ProjectItem(pname, bullets))
                            i = j
                        }
                    }
                }
            }
            return ResumeData(name, contact, summary, skills, experience, education, projects)
        }

        private val KNOWN_HEADERS = setOf(
            "personal info", "contact", "contact info", "skills", "core skills",
            "relevant work experience", "work experience", "experience",
            "education", "projects", "professional summary", "summary"
        )
    }
}

@Serializable
data class CoverLetterData(
    val name: String = "",
    val contact: String = "",
    val companyBlock: String = "",
    val salutation: String = "Dear Hiring Manager,",
    val paragraphs: List<String> = emptyList(),
    val closing: String = "Sincerely,",
    val signature: String = ""
) {
    fun toJson(): String = jsonFormat.encodeToString(this)

    fun isSubstantial(): Boolean = paragraphs.isNotEmpty()

    fun toHumanReadableText(): String = buildString {
        if (name.isNotBlank()) appendLine(name)
        if (contact.isNotBlank()) appendLine(contact)
        if (name.isNotBlank() || contact.isNotBlank()) appendLine()
        
        if (companyBlock.isNotBlank()) {
            appendLine(companyBlock)
            appendLine()
        }
        
        appendLine(salutation)
        appendLine()
        for (p in paragraphs) {
            appendLine(p)
            appendLine()
        }
        appendLine(closing)
        if (signature.isNotBlank()) appendLine(signature)
    }.trim()

    companion object {
        fun fromJson(json: String): CoverLetterData? = try {
            val clean = extractJson(json)
            jsonFormat.decodeFromString<CoverLetterData>(clean)
        } catch (e: Exception) {
            Log.d("JobSearch", "CoverLetterData.fromJson: decode failed: ${e.message}")
            null
        }

        fun extractJson(text: String): String = JsonScrubber.scrub(text)

        fun fromText(text: String): CoverLetterData {
            val fromJson = fromJson(text)
            if (fromJson != null) {
                Log.d("JobSearch", "CoverLetterData.fromText: path=JSON")
                return fromJson
            }

            Log.d("JobSearch", "CoverLetterData.fromText: path=HEURISTIC")
            val trimmed = text.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("```json")) {
                return CoverLetterData()
            }

            val lines = text.replace("\r\n", "\n").split("\n").map { it.trim() }
            var name = ""
            var contact = ""
            var companyBlock = ""
            var salutation = "Dear Hiring Manager,"
            val paragraphs = mutableListOf<String>()
            var closing = "Sincerely,"
            var signature = ""

            // Very basic heuristic for parsing unstructured text back into fields
            var i = 0
            while (i < lines.size && lines[i].isBlank()) i++
            if (i < lines.size && lines[i].isNotBlank()) {
                val line = lines[i]
                if (!line.startsWith("Dear", ignoreCase = true) && !line.startsWith("To ", ignoreCase = true)) {
                    name = line
                    i++
                }
            }
            while (i < lines.size && lines[i].isBlank()) i++
            if (i < lines.size && lines[i].isNotBlank() && (lines[i].contains("|") || lines[i].contains("@"))) contact = lines[i++]
            
            // Skip to salutation
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("Dear") || line.endsWith(",")) {
                    salutation = line
                    i++
                    break
                }
                if (line.isNotBlank() && companyBlock.isBlank()) companyBlock = line
                i++
            }

            var currentP = StringBuilder()
            while (i < lines.size) {
                val line = lines[i]
                if (line.lowercase().startsWith("sincerely") || line.lowercase().startsWith("best regards")) {
                    closing = line
                    i++
                    if (i < lines.size) signature = lines[i]
                    break
                }
                if (line.isBlank()) {
                    if (currentP.isNotEmpty()) {
                        paragraphs.add(currentP.toString())
                        currentP = StringBuilder()
                    }
                } else {
                    if (currentP.isNotEmpty()) currentP.append(" ")
                    currentP.append(line)
                }
                i++
            }
            if (currentP.isNotEmpty()) paragraphs.add(currentP.toString())

            return CoverLetterData(name, contact, companyBlock, salutation, paragraphs, closing, signature)
        }
    }
}

@Serializable
data class SkillCategory(val name: String, val skills: List<String>)

object SkillCategoryListSerializer : KSerializer<List<SkillCategory>> {
    private val delegate = ListSerializer(SkillCategory.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<SkillCategory> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        val element = jsonDecoder.decodeJsonElement()

        return if (element is JsonArray) {
            val result = mutableListOf<SkillCategory>()
            element.forEach { item ->
                when (item) {
                    is JsonObject -> {
                        result.add(jsonDecoder.json.decodeFromJsonElement(SkillCategory.serializer(), item))
                    }
                    is JsonPrimitive -> {
                        if (item.isString) {
                            result.add(SkillCategory("Skills", listOf(item.content)))
                        }
                    }
                    else -> {}
                }
            }
            // Group simple ones
            val (simple, objects) = result.partition { it.name == "Skills" }
            if (simple.isNotEmpty()) {
                val combinedSkills = simple.flatMap { it.skills }
                listOf(SkillCategory("Skills", combinedSkills)) + objects
            } else {
                objects
            }
        } else {
            emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<SkillCategory>) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(jsonEncoder.json.encodeToJsonElement(delegate, value))
    }
}

@Serializable
data class ExperienceItem(
    val title: String,
    val company: String,
    val location: String,
    val dates: String,
    val bullets: List<String>
)

@Serializable
data class EducationItem(val degree: String, val school: String, val dates: String)

@Serializable
data class ProjectItem(val name: String, val bullets: List<String>)

/**
 * Utility to get a human-readable snippet for document previews.
 */
fun getDisplayPreview(rawText: String, isResume: Boolean): String {
    if (rawText.isBlank()) return ""

    return try {
        if (isResume) {
            val data = ResumeData.fromJson(rawText)
            data?.summary?.ifBlank { scrubAndSnippet(rawText) } ?: scrubAndSnippet(rawText)
        } else {
            // Try Cover Letter first
            val cover = CoverLetterData.fromJson(rawText)
            if (cover != null && cover.paragraphs.isNotEmpty()) {
                return cover.paragraphs.take(2).joinToString("\n\n")
            }

            // Try Cheat Sheet
            val cheat = CheatSheetData.fromJson(rawText)
            if (cheat != null && cheat.keyHighlights.isNotEmpty()) {
                return cheat.keyHighlights.joinToString("\n") { "• $it" }
            }

            // Fallback to Email / Snippet
            if (rawText.trim().startsWith("{")) {
                scrubAndSnippet(rawText)
            } else {
                val cleaned = rawText.trim()
                if (cleaned.length > 300) cleaned.take(300) + "..." else cleaned
            }
        }
    } catch (e: Exception) {
        scrubAndSnippet(rawText)
    }
}

private fun scrubAndSnippet(text: String): String {
    val scrubbed = JsonScrubber.scrub(text)
    // Remove JSON structure characters and common personal info headers
    val filtered = scrubbed.lines()
        .map { it.trim() }
        .filterNot { line ->
            line.startsWith("{") || line.startsWith("}") || line.startsWith("[") || line.startsWith("]") ||
            line.startsWith("\"name\"") || line.startsWith("\"contact\"") || line.isBlank()
        }
        .joinToString(" ")
        .replace(Regex("\"[a-zA-Z0-9]+\":"), "") // Remove keys
        .replace("\"", "") // Remove quotes
        .replace(Regex("\\s+"), " ")
        .trim()

    return if (filtered.length > 250) filtered.take(250) + "..." else filtered
}
