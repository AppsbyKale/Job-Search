package com.example.jobsearch.ai

import com.example.jobsearch.data.Job

object PromptBuilder {

    private const val MAX_RESUME_WORDS = 800
    private const val MAX_JOB_WORDS = 300
    private const val MAX_COMPANY_WORDS = 30

    private const val BASE_SYSTEM =
        "You are an expert professional resume writer. " +
        "Base everything on the candidate's real resume. Never invent facts."

    fun distillationPrompt(job: Job, resumeText: String, qa: List<Pair<String, String>> = emptyList()): String {
        val qaText = if (qa.isEmpty()) "" else "\nCandidate Q&A:\n" +
                qa.joinToString("\n") { "Q: ${it.first}\nA: ${it.second}" }
        
        return """
            You are a job analysis assistant. Your task is to extract a matching strategy between the JOB and the RESUME, including any supplemental Q&A facts.
            
            STRICT RULES:
            1. Output ONLY a raw JSON object.
            2. Do not include markdown, backticks, or any conversational text.
            3. Ensure the JSON is valid.

            JSON Schema:
            {
              "strategy": "A 2-sentence tailoring strategy",
              "keywords": ["List of 5 key missing skills from job description"],
              "proofPoints": ["List of 3 strong achievements from the resume or Q&A that match the job"]
            }

            JOB: ${job.title}
            DESC: ${job.description}
            
            RESUME: ${resumeText}
            $qaText
        """.trimIndent()
    }

    fun smartCleanPrompt(description: String): String {
        return """
            Extract 5-10 core requirements from this job description as a simple bulleted list. 
            No intro, no fluff.
            
            DESC: $description
        """.trimIndent()
    }

    fun resumePrompt(
        job: Job,
        resumeText: String,
        distilledFacts: String = "",
        qa: List<Pair<String, String>> = emptyList(),
        steeringInstructions: String? = null
    ): String {
        val qaText = if (qa.isEmpty()) "" else "\nCandidate Answers:\n" +
                qa.joinToString("\n") { "Q: ${it.first}\nA: ${it.second}" }

        val steering = if (steeringInstructions.isNullOrBlank()) "" else 
            "\n**USER STEERING INSTRUCTIONS (PRIORITIZE THESE):**\n$steeringInstructions\n"

        val jobInfo = "Job: ${job.title} at ${job.company}\nDescription: ${truncateWords(job.description, 200)}"
        
        val head = """
            Rewrite this resume using these facts. **IMPORTANT: Ensure ALL jobs and dates from the base resume are included in the tailored output. Each job must have between 2 and 10 bullet points.** JSON ONLY.
            $steering
            Facts:
            $distilledFacts
            $qaText
            
            $jobInfo
        """.trimIndent()

        val schema = """
            Schema:
            {
              "name": "Full Name",
              "contact": "Contact",
              "summary": "Summary",
              "skills": [ { "name": "Cat", "skills": ["S1"] } ],
              "experience": [ { "title": "T", "company": "C", "location": "L", "dates": "D", "bullets": ["B1"] } ],
              "education": [ { "degree": "D", "school": "S", "dates": "D" } ],
              "projects": [ { "name": "P", "bullets": ["B1"] } ]
            }
        """.trimIndent()

        val resume = resumeText

        return """
            $head
            
            Raw Resume:
            $resume

            $schema
        """.trimIndent()
    }

    fun coverLetterPrompt(job: Job, tailoredResume: String): String {
        val resume = tailoredResume
        val description = job.description
        return """
            ${BASE_SYSTEM}

            STRICT: YOUR RESPONSE MUST BE RAW JSON ONLY. NO MARKDOWN. NO BACKTICKS. NO CONVERSATION.

            Write a tailored cover letter for the candidate. 
            USE THE TAILORED RESUME BELOW AS YOUR SOURCE OF FACTS TO ENSURE 100% CONSISTENCY.

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

    private fun truncateWords(text: String, maxWords: Int): String {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        val words = cleaned.split(" ")
        return if (words.size <= maxWords) cleaned else words.take(maxWords).joinToString(" ")
    }

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
            ${truncateWords(job.company, MAX_COMPANY_WORDS)}

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

    fun matchPercentPrompt(job: Job, resumeText: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            You are an expert recruiter evaluating how well a candidate's resume matches a job description.

            Score the match from 0 to 100 based on:
            - Required skills and technologies that appear in the resume
            - Level and years of relevant experience
            - Domain / industry fit
            - How much of the job's actual responsibilities the candidate has performed

            STRICT RULES:
            - Be honest and strict. Do not inflate. Missing core requirements must reduce the score substantially.
            - A generally good resume does not get a high score for an unrelated job.
            - DO NOT hallucinate technical skills (like Kotlin or Java) if they are not explicitly mentioned in the job description or candidate resume. Be realistic. If it's a customer service job, focus on soft skills, communication, and specific tools mentioned (e.g. Quicken, Excel).
            - YOUR RESPONSE MUST BE RAW JSON ONLY. NO MARKDOWN.
            - EXACT JSON schema:
            {
              "score": 85,
              "foundKeywords": ["Kotlin", "Compose"],
              "missingKeywords": ["GraphQL", "TDD"]
            }

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

    fun improveQuestionsPrompt(job: Job, resumeText: String, currentPercent: Int, count: Int = 4): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            You are an expert career coach. The candidate scored $currentPercent% match for the job below.
            Their resume is missing things the job wants. Your job is to draw out real experience they have but did not write down.

            STRICT RULES:
            - Ask $count pointed questions that surface concrete experience, skills, or achievements the candidate could genuinely answer from their own background.
            - Focus on the skills and requirements the job lists that the resume does NOT show.
            - Ask specific questions like "Have you worked with X? If so, what did you do and what was the result?" instead of generic ones.
            - Do not ask about things the resume already covers well.
            - Output ONLY a numbered list, one question per line, like:
            1. <question>
            - No intro text, no explanations, no markdown.

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

    fun improvedResumePrompt(job: Job, resumeText: String, qa: List<Pair<String, String>>): String {
        val resume = resumeText
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        val qaText = qa.mapIndexed { index, (question, answer) ->
            "Q${index + 1}: $question\nA${index + 1}: ${answer.take(800)}"
        }.joinToString("\n\n")
        return """
            $BASE_SYSTEM
            The data below is the candidate's employment history. Rewrite the resume so it is tailored to the job posting and incorporates the follow-up answers. **IMPORTANT: Ensure ALL jobs and dates from the base resume are included in the tailored output. Each job must have between 2 and 10 bullet points.** Never invent facts.

            <RAW EXPERIENCE FACTS>
            $resume

            <JOB TITLE>
            ${job.title}

            <COMPANY>
            ${truncateWords(job.company, MAX_COMPANY_WORDS)}

            <JOB DESCRIPTION>
            $description

            <CANDIDATE ANSWERS>
            $qaText

            STRICT JSON ONLY:
            {
              "name": "Full Name",
              "contact": "Phone | Email | City, State",
              "summary": "3-5 sentences paragraph tailored to the role...",
              "skills": [ { "name": "Category", "skills": ["Skill 1", "Skill 2"] } ],
              "experience": [
                {
                  "title": "Job Title",
                  "company": "Company Name",
                  "location": "City, State",
                  "dates": "Start - End",
                  "bullets": [
                    "Strong action-oriented bullet with metrics",
                    "Detailed technical achievement",
                    "Proof of impact"
                  ]
                }
              ],
              "education": [ { "degree": "Degree", "school": "School", "dates": "Dates" } ],
              "projects": [ { "name": "Project Name", "bullets": ["Detailed project description and technologies"] } ]
            }
        """.trimIndent()
    }

    fun cheatSheetPrompt(job: Job, resumeText: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            You are an expert interview coach. Create a concise "Interview Cheat Sheet" for the candidate based on the job and their resume.
            Focus on high-impact points and preparing for difficult questions.

            STRICT RULES:
            - keyHighlights: 4-5 bullet points of the candidate's strongest selling points for THIS specific job.
            - toughQuestions: Generate 6 questions total:
                1. 3 Standard Questions (e.g., "Tell me about yourself", "Why should we hire you?", "Strengths/Weaknesses").
                2. 3 Job-Specific Questions (tailored to this role and the candidate's specific background).
            - For each question, provide a "strategy" and a "exampleAnswer" (a 2-3 sentence first-person response).
            - Output ONLY raw JSON. No markdown.

            EXACT JSON schema:
            {
              "keyHighlights": ["Point 1", "Point 2", "Point 3", "Point 4"],
              "toughQuestions": [
                { "question": "Q1", "strategy": "Strategy 1", "exampleAnswer": "I would say..." },
                { "question": "Q2", "strategy": "Strategy 2", "exampleAnswer": "In my previous role..." },
                { "question": "Q3", "strategy": "Strategy 3", "exampleAnswer": "I approach this by..." },
                { "question": "Q4", "strategy": "Strategy 4", "exampleAnswer": "..." },
                { "question": "Q5", "strategy": "Strategy 5", "exampleAnswer": "..." },
                { "question": "Q6", "strategy": "Strategy 6", "exampleAnswer": "..." }
              ]
            }

            <CANDIDATE RESUME>
            $resume

            <JOB TITLE>
            ${job.title}

            <JOB DESCRIPTION>
            $description
        """.trimIndent()
    }

    fun followUpEmailPrompt(job: Job, resumeText: String): String {
        val resume = truncateWords(resumeText, MAX_RESUME_WORDS)
        val description = truncateWords(job.description, MAX_JOB_WORDS)
        return """
            $BASE_SYSTEM
            Write a professional follow-up email for the candidate to send after an interview or application for the role below.
            Use the candidate's resume for specific details.

            STRICT RULES:
            - Keep it professional, concise, and enthusiastic.
            - Output ONLY the email body text.
            - No subject line, no placeholders like "[Name]", use the real names from the resume/job if available.

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

    fun parseQuestions(text: String): List<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val match = Regex("^\\d+[.)]\\s*").find(line) ?: return@mapNotNull null
                line.substring(match.range.last + 1).trim()
            }
            .filter { it.length > 3 }
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
