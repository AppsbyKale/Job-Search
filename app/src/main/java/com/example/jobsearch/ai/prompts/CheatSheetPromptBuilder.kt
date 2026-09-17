package com.example.jobsearch.ai.prompts

import com.example.jobsearch.data.Job
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_JOB_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_RESUME_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.truncateWords

object CheatSheetPromptBuilder {
    fun cheatSheetPrompt(
        job: Job,
        resumeText: String,
        companyInfo: String = "",
        includeOverview: Boolean = true,
        includeChallenges: Boolean = true,
        includeDayToDay: Boolean = true,
        includeHighlights: Boolean = true,
        customQuestions: String = "",
        strengths: String = "",
        weaknesses: String = ""
    ): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        
        val companyBlock = if (companyInfo.isNotBlank()) "\n<COMPANY BACKGROUND & INSIGHTS>\n$companyInfo\n" else ""
        val strengthsBlock = if (strengths.isNotBlank()) "\n<CANDIDATE STRENGTHS TO HIGHLIGHT>\n$strengths\n" else ""
        val weaknessesBlock = if (weaknesses.isNotBlank()) "\n<CANDIDATE WEAKNESSES/GAPS TO ADDRESS>\n$weaknesses\n" else ""
        val customBlock = if (customQuestions.isNotBlank()) "\n<USER CUSTOM INTERVIEW QUESTIONS TO ANSWER>\n$customQuestions\n" else ""

        val focusInstructions = buildList {
            if (includeOverview) add("- Include company culture, overview, and mission insights.")
            if (includeChallenges) add("- Address potential challenges of the role.")
            if (includeDayToDay) add("- Include day-to-day responsibilities and expectations.")
            if (includeHighlights) add("- Highlight key qualifications and selling points.")
        }.joinToString("\n")

        return """
            You are an expert interview coach. Create a comprehensive research document titled "${job.company} Research" for the candidate based on the job, company info, and their resume.
            Focus on high-impact points, company background, relevant skills, Q&A, and notes.

            FOCUS INSTRUCTIONS:
            $focusInstructions

            STRICT RULES:
            - aboutCompany: A short 2-3 sentence executive paragraph summarizing the company's background, culture, and mission.
            - relevantSkills: 5-8 key technical and domain skills bridging the candidate's resume to this job.
            - keyHighlights: 4-5 bullet points of the candidate's strongest selling points for THIS specific job.
            - toughQuestions: Generate questions including any user custom questions above, plus job-specific and standard questions. For each question, provide a "strategy" and an "exampleAnswer" (2-3 sentence first-person response).
            - notes: Leave empty ("") for free-form user notes.
            - Output ONLY raw JSON. No markdown.

            EXACT JSON schema:
            {
              "aboutCompany": "Short company overview paragraph...",
              "relevantSkills": ["Skill 1", "Skill 2"],
              "keyHighlights": ["Point 1", "Point 2"],
              "toughQuestions": [
                { "question": "Q1", "strategy": "Strategy 1", "exampleAnswer": "I would say..." }
              ],
              "notes": ""
            }
            $companyBlock
            $strengthsBlock
            $weaknessesBlock
            $customBlock

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <COMPANY>
            ${job.company}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }

    fun manualQuestionPrompt(job: Job, resumeText: String, question: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            You are an expert interview coach. A candidate has a specific question about preparing for an interview for the job below.
            
            Answer the question in a structured format:
            1. Strategy: A short note on how to approach the answer.
            2. Example Answer: A 2-3 sentence first-person script the candidate can say.

            STRICT RULES:
            - Output ONLY raw JSON. No markdown.
            - Use specific facts from the resume.

            EXACT JSON schema:
            {
              "question": "$question",
              "strategy": "...",
              "exampleAnswer": "..."
            }

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }
}
