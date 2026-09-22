package com.example.jobsearch.ai.prompts

import com.example.jobsearch.data.Job
import com.example.jobsearch.ai.prompts.BasePrompt.BASE_SYSTEM
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_COMPANY_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_JOB_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_RESUME_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.truncateWords
import com.example.jobsearch.util.DateFormatter

object EmailPromptBuilder {
    fun initialEmailPrompt(job: Job, resumeText: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            $BASE_SYSTEM
            Write an introductory email for the candidate to send alongside their resume and cover letter when applying for the role below.
            
            STRICT RULES:
            - Keep it welcoming, introductory, and professional. Mention that the resume and cover letter are attached for review.
            - Output ONLY the email body text.
            - No subject line, no placeholders like "[Name]", use real names from resume/job if available.

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <COMPANY>
            ${truncateWords(job.company, MAX_COMPANY_WORDS)}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }

    fun followUpEmailPrompt(job: Job, resumeText: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        val appliedDateText = if (job.dateApplied != null) {
            "\n<APPLICATION DATE>\nApplied on: ${DateFormatter.formatDate(job.dateApplied)}\n"
        } else ""

        return """
            $BASE_SYSTEM
            Write a professional follow-up email for the candidate to send after an application for the role below (mentioning that they applied on the application date provided).
            Use the candidate's resume for specific details.

            STRICT RULES:
            - Keep it professional, concise, and enthusiastic.
            - Output ONLY the email body text.
            - No subject line, no placeholders like "[Name]", use the real names from the resume/job if available.
            $appliedDateText
            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <COMPANY>
            ${truncateWords(job.company, MAX_COMPANY_WORDS)}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }

    fun thankYouEmailPrompt(job: Job, resumeText: String, interviewNotes: String? = null): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        val notesText = if (!interviewNotes.isNullOrBlank()) {
            "\n<PERSONALIZED INTERVIEW NOTES / HIGHLIGHTS>\n$interviewNotes\n"
        } else ""

        return """
            $BASE_SYSTEM
            Write a professional post-interview thank-you email for the candidate to send to the interviewer(s) for the role below.
            Use the candidate's resume and any personalized interview notes provided below.

            STRICT RULES:
            - Keep it professional, appreciative, concise, and enthusiastic.
            - Output ONLY the email body text.
            - No subject line, no placeholders like "[Name]", use real names from resume/job if available.
            $notesText
            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <COMPANY>
            ${truncateWords(job.company, MAX_COMPANY_WORDS)}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }
}
