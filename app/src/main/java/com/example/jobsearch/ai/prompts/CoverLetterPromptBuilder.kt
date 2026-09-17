package com.example.jobsearch.ai.prompts

import com.example.jobsearch.data.Job
import com.example.jobsearch.ai.prompts.BasePrompt.BASE_SYSTEM
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_COMPANY_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.truncateWords

object CoverLetterPromptBuilder {
    fun coverLetterPrompt(job: Job, tailoredResume: String, qa: List<Pair<String, String>> = emptyList(), steeringInstructions: String? = null): String {
        val resume = tailoredResume
        val description = job.description
        val steering = if (steeringInstructions.isNullOrBlank()) "" else 
            "\n**USER STEERING INSTRUCTIONS (PRIORITIZE THESE):**\n$steeringInstructions\n"

        val qaSection = if (qa.isEmpty()) "" else {
            "\n<CANDIDATE SUPPLEMENTAL Q&A / REFLECTIONS>\n" +
            qa.joinToString("\n") { "Q: ${it.first}\nA: ${it.second}" } +
            "\n(Note: Incorporate 1-2 narrative insights or motivations from the Q&A above to give the cover letter an engaging, authentic personality. Avoid repeating resume bullet points verbatim; instead, focus on the 'why' and personal context behind their experience.)\n"
        }

        return """
            ${BASE_SYSTEM}

            STRICT: YOUR RESPONSE MUST BE RAW JSON ONLY. NO MARKDOWN. NO BACKTICKS. NO CONVERSATION.

            Write a tailored cover letter for the candidate. 
            USE THE TAILORED RESUME BELOW AS YOUR SOURCE OF FACTS TO ENSURE 100% CONSISTENCY.
            $steering
            $qaSection
            STRICT RULES:
            - Output ONLY raw JSON. No markdown. No conversation.
            - Escape double quotes inside strings with \.
            - Use only real facts. Do not invent anything.
            - EXACT JSON schema:
            {
              "name": "Candidate Full Name",
              "contact": "Phone | Email | City, State",
              "companyBlock": "Company info",
              "salutation": "Dear Hiring Manager,",
              "paragraphs": [
                "P1: Opening",
                "P2: Experience mapping",
                "P3: Achievement",
                "P4: Closing"
              ],
              "closing": "Sincerely,",
              "signature": "Candidate Name"
            }

            <TAILORED RESUME>
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
