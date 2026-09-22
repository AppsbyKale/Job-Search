package com.example.jobsearch.document

import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExporter {

    fun csvFor(jobs: List<Job>): String {
        val sb = StringBuilder()
        sb.append("Date,Title,Company,Status,URL,Tags,Has Resume,Has Cover Letter,Has Initial Email,Has Cheat Sheet,Has Follow-up\n")
        for (job in jobs) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(job.dateAdded))
            sb.append(csvCell(date)).append(',')
                .append(csvCell(job.title)).append(',')
                .append(csvCell(job.company)).append(',')
                .append(csvCell(job.status)).append(',')
                .append(csvCell(job.url)).append(',')
                .append(csvCell(job.tags)).append(',')
                .append(if (job.hasResume) "yes" else "no").append(',')
                .append(if (job.hasCoverLetter) "yes" else "no").append(',')
                .append(if (job.hasInitialEmail) "yes" else "no").append(',')
                .append(if (job.hasCheatSheet) "yes" else "no").append(',')
                .append(if (job.hasFollowUpEmail) "yes" else "no")
                .append('\n')
        }
        return sb.toString()
    }

    fun parseCsv(text: String): List<Job> {
        val jobs = mutableListOf<Job>()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return jobs
        for (i in 1 until lines.size) {
            val line = lines[i]
            val cols = parseCsvLine(line)
            if (cols.size >= 5) {
                val title = cols.getOrNull(1) ?: "Untitled job"
                val company = cols.getOrNull(2) ?: ""
                val status = cols.getOrNull(3) ?: JobStatus.SAVED.name
                val url = cols.getOrNull(4) ?: ""
                val tags = cols.getOrNull(5) ?: ""
                jobs.add(
                    Job(
                        title = title,
                        company = company,
                        status = if (JobStatus.entries.any { it.name == status }) status else JobStatus.SAVED.name,
                        url = url,
                        tags = tags,
                        dateAdded = System.currentTimeMillis()
                    )
                )
            }
        }
        return jobs
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false
        var idx = 0
        while (idx < line.length) {
            val c = line[idx]
            if (c == '"') {
                if (inQuotes && idx + 1 < line.length && line[idx + 1] == '"') {
                    cur.append('"')
                    idx++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString())
                cur = StringBuilder()
            } else {
                cur.append(c)
            }
            idx++
        }
        result.add(cur.toString())
        return result
    }
}
