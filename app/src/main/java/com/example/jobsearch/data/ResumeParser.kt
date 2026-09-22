package com.example.jobsearch.data

import android.util.Log

object ResumeParser {
    private val KNOWN_HEADERS = setOf(
        "summary", "professional summary",
        "experience", "relevant work experience", "work experience",
        "skills", "core skills",
        "education",
        "projects", "personal projects"
    )

    fun fromText(text: String): ResumeData {
        val fromJson = ResumeData.fromJson(text)
        if (fromJson != null) {
            Log.d("JobSearch", "ResumeParser.fromText: path=JSON")
            return fromJson
        }

        Log.d("JobSearch", "ResumeParser.fromText: path=HEURISTIC")
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("```json")) {
            return ResumeData()
        }

        val sections = mutableListOf<Pair<String, List<String>>>()
        var currentHeader = ""
        var currentLines = mutableListOf<String>()
        
        for (line in text.replace("\r\n", "\n").split("\n")) {
            val lineTrimmed = line.trim()
            if (lineTrimmed.isEmpty()) continue
            
            var matchedHeader = ""
            for (h in KNOWN_HEADERS) {
                if (lineTrimmed.lowercase().startsWith("$h:")) {
                    matchedHeader = h
                    break
                }
            }

            if (matchedHeader.isNotEmpty()) {
                sections.add(currentHeader to currentLines)
                currentHeader = matchedHeader
                val content = lineTrimmed.substring(matchedHeader.length + 1).trim()
                currentLines = if (content.isNotEmpty()) mutableListOf(content) else mutableListOf()
            } else {
                val lower = lineTrimmed.lowercase().trimEnd(':')
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
                        val line1 = lines.getOrNull(i)?.trim() ?: ""
                        if (line1.isEmpty()) { i++; continue }
                        val line2 = lines.getOrNull(i+1)?.trim() ?: ""
                        val line3 = lines.getOrNull(i+2)?.trim() ?: ""

                        val isLine2Dates = line2.contains(Regex("\\d{4}")) || line2.contains("Present", true) || line2.contains("Current", true)
                        val isLine3Dates = line3.contains(Regex("\\d{4}")) || line3.contains("Present", true) || line3.contains("Current", true)

                        var headerStr = line1
                        val datesLine: String
                        val bulletsStartIndex: Int

                        if (isLine2Dates) {
                            datesLine = line2
                            bulletsStartIndex = i + 2
                        } else if (isLine3Dates) {
                            headerStr = "$line1 | $line2"
                            datesLine = line3
                            bulletsStartIndex = i + 3
                        } else {
                            datesLine = line2
                            bulletsStartIndex = i + 2
                        }

                        val bullets = mutableListOf<String>()
                        var j = bulletsStartIndex
                        while (j < lines.size && (lines[j].trim().startsWith("-") || lines[j].trim().startsWith("•") || lines[j].isBlank())) {
                            val b = lines[j].trim()
                            if (b.isNotBlank()) bullets.add(b.removePrefix("-").removePrefix("•").trim())
                            j++
                        }

                        val delimiters = listOf("|", "–", "—", " at ", ",", "-")
                        var parts = listOf(headerStr)
                        for (d in delimiters) {
                            if (headerStr.contains(d)) {
                                parts = headerStr.split(d).map { it.trim() }.filter { it.isNotEmpty() }
                                if (parts.size >= 2) break
                            }
                        }

                        experience.add(ExperienceItem(
                            title = parts.getOrNull(0) ?: headerStr,
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
                        education.add(EducationItem(deg, "", dates))
                        i += 2
                    }
                }
                "projects", "personal projects" -> {
                    var i = 0
                    while (i < lines.size) {
                        val pname = lines[i].trim()
                        if (pname.isEmpty()) { i++; continue }
                        val bullets = mutableListOf<String>()
                        var j = i + 1
                        while (j < lines.size && (lines[j].trim().startsWith("-") || lines[j].trim().startsWith("•") || lines[j].isBlank())) {
                            val b = lines[j].trim()
                            if (b.isNotBlank()) bullets.add(b.removePrefix("-").removePrefix("•").trim())
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
}
