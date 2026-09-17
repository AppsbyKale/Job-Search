package com.example.jobsearch.ai.prompts

import com.example.jobsearch.data.Job
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_JOB_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_RESUME_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.truncateWords

object InterviewPromptBuilder {
    fun interviewQuestionsPrompt(job: Job, resumeText: String, count: Int): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            You are an expert interview coach preparing a candidate for a job interview.
            Based on the job posting and the candidate's resume below, write $count realistic interview questions.

            STRICT RULES:
            - Mix behavioral questions ("Tell me about a time...") with role-specific questions drawn from the job description and the candidate's experience.
            - Questions must be answerable from the candidate's own background. Do not ask about skills the candidate clearly lacks.
            - Vary the difficulty. Make some questions pointed so the candidate has to think.
            - Output ONLY a numbered list, one question per line, formatted exactly like this:
            1. <question>
            2. <question>
            - No intro text, no explanations, no markdown.

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <COMPANY>
            ${truncateWords(job.company, BasePrompt.MAX_COMPANY_WORDS)}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }

    fun interviewFeedbackPrompt(job: Job, resumeText: String, question: String, answer: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            You are an expert interview coach. A candidate answered an interview question during practice.
            Evaluate the answer against the job posting and the candidate's resume.

            The candidate's answer is a speech-to-text transcript; it may contain transcription errors, filler words and incomplete sentences. Judge the substance of the answer, not its wording.

            STRICT RULES:
            - Be concrete and honest. Do not inflate the score.
            - Output EXACTLY this structure:
            SCORE: <a single integer from 1 to 10>
            STRENGTHS:
            - <one or two bullet lines>
            WEAKNESSES:
            - <one or two bullet lines>
            HOW TO IMPROVE:
            - <two or three actionable bullet lines, specific to this answer and this job>
            MODEL ANSWER:
            <a strong 3-5 sentence model answer the candidate could give>
            - Keep every section concise. Plain text, no markdown, no extra text before or after.

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <JOB DESCRIPTION>
            $description

            <QUESTION>
            $question

            <CANDIDATE ANSWER>
            $answer
        """.trimIndent()
    }

    fun interviewOverallPrompt(job: Job, resumeText: String, items: List<Pair<String, String>>): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        val qa = items.mapIndexed { index, (question, answer) ->
            "Q${index + 1}: $question\nA${index + 1}: ${answer.take(1200)}"
        }.joinToString("\n\n")
        return """
            You are an expert interview coach. Review a full mock interview for the job below.

            STRICT RULES:
            - Give an overall verdict on the candidate's readiness for this specific role.
            - Cover: the strongest moments, the biggest weaknesses, any pattern across the answers, and 3-5 specific things to work on before the real interview.
            - Be direct and practical. Do not inflate.
            - Plain text, no markdown. Use short paragraphs and simple "- " bullets.

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <JOB DESCRIPTION>
            $description

            <QUESTIONS AND ANSWERS>
            $qa
        """.trimIndent()
    }

    fun parseQuestions(text: String): List<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val match = Regex("^(?:\\d+[.)]|\\w[.)]|[•\\-*])\\s*").find(line)
                var cleaned = if (match != null) {
                    line.substring(match.range.last + 1).trim()
                } else {
                    line
                }
                cleaned = cleaned.replace(Regex("^(?:[Qq]\\d*[:.)]|[Aa][:.)]|\\w[.)])\\s*"), "").trim()
                cleaned.takeIf { it.length > 3 }
            }
            .toList()
    }

    data class Feedback(val score: Int?, val feedback: String)

    fun parseFeedback(text: String): Feedback {
        val trimmed = text.trim()
        val score = Regex("SCORE:\\s*(\\d{1,2})", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.coerceIn(0, 10)
        return Feedback(score, trimmed)
    }

    fun splitModelAnswer(feedback: String): Pair<String, String> {
        val marker = "MODEL ANSWER:"
        val index = feedback.indexOf(marker, ignoreCase = true)
        if (index < 0) return feedback to ""
        val before = feedback.substring(0, index).trim()
        val after = feedback.substring(index + marker.length).trim()
        return before to after
    }
}
