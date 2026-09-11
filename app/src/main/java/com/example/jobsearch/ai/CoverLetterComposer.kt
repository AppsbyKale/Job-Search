package com.example.jobsearch.ai

import com.example.jobsearch.data.CoverLetterData
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.ResumeData

/**
 * Builds the full cover letter stored on a job: the resume header (name +
 * contact), then a left-aligned company block, then the AI-generated body.
 * The header is lifted straight from the job's resume so it always matches.
 */
object CoverLetterComposer {

    private val SKIPPED_HEADER_LINES = setOf(
        "personal info", "contact", "contact info", "resume",
        "skills", "core skills", "relevant work experience", "work experience",
        "experience", "education", "projects", "professional summary", "summary"
    )

    fun compose(bodyJson: String, resumeText: String, job: Job): String {
        val data = CoverLetterData.fromJson(bodyJson)
        if (data != null) {
            val resumeData = ResumeData.fromJson(resumeText)
            val headerName = resumeData?.name ?: extractHeader(resumeText)?.split("\n")?.getOrNull(0) ?: ""
            val headerContact = resumeData?.contact ?: extractHeader(resumeText)?.split("\n")?.getOrNull(1) ?: ""
            
            return data.copy(
                name = headerName,
                contact = headerContact,
                companyBlock = companyBlock(job)
            ).toJson()
        }

        // Fallback for legacy text
        val header = extractHeader(resumeText)
        val block = companyBlock(job)
        val prefix = listOfNotNull(header, block)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val text = bodyJson.trim()
        return if (prefix.isBlank()) text else "$prefix\n\n$text"
    }

    /** The first two meaningful lines of the resume: the name, then the contact line. */
    fun extractHeader(resumeText: String): String? {
        val data = ResumeData.fromJson(resumeText)
        if (data != null) {
            val lines = listOf(data.name, data.contact).filter { it.isNotBlank() }
            return if (lines.isEmpty()) null else lines.joinToString("\n")
        }

        val lines = mutableListOf<String>()
        for (raw in resumeText.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            val t = raw.trim()
            if (t.isBlank() || t.startsWith("```") || t.contains("{") || t.contains("}") || t.contains("`")) continue
            val lower = t.lowercase().trimEnd(':')
            if (lower in SKIPPED_HEADER_LINES) continue
            if (lower.startsWith("name:") || lower.startsWith("phone") || lower.startsWith("email") ||
                lower.startsWith("address") || lower.startsWith("linkedin") || lower.startsWith("location") ||
                lower.startsWith("city") || lower.startsWith("url") || lower.startsWith("website")
            ) continue
            lines.add(t)
            if (lines.size == 2) break
        }
        return lines.joinToString("\n").ifBlank { null }
    }

    /** Company name, plus a location line pulled from the description when one is found. */
    fun companyBlock(job: Job): String {
        val lines = buildList {
            if (job.company.isNotBlank()) add(job.company.trim())
            extractLocation(job.description)?.let { add(it) }
        }
        return lines.joinToString("\n")
    }

    fun extractLocation(description: String): String? {
        if (description.isBlank()) return null
        val cityState = Regex("""\b[A-Z][A-Za-z]+(?:[\s'-][A-Z][A-Za-z]+){0,2}\s*,\s*[A-Z]{2}\b""")
        val labeled = Regex("""(?i)(?:location|worksite|office|site|based in|located in)[\s:]+([^\n]{1,80})""")
            .find(description)
        val labeledText = labeled?.groupValues?.get(1)
        val inLabeled = labeledText?.let { cityState.find(it)?.value }
        val candidate = when {
            inLabeled != null -> inLabeled
            else -> cityState.find(description)?.value ?: labeledText
        }
        return candidate?.trim()?.removeSuffix(".")?.trim()?.takeIf { it.length in 3..60 }
    }
}
