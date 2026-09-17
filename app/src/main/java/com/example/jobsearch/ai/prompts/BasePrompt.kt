package com.example.jobsearch.ai.prompts

object BasePrompt {
    const val MAX_RESUME_WORDS = 800
    const val MAX_JOB_WORDS = 300
    const val MAX_COMPANY_WORDS = 30

    const val BASE_SYSTEM =
        "You are an expert professional resume writer. " +
        "Base everything on the candidate's real resume. Never invent facts."

    fun truncateWords(text: String, maxWords: Int): String {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        val words = cleaned.split(" ")
        return if (words.size <= maxWords) cleaned else words.take(maxWords).joinToString(" ")
    }
}
