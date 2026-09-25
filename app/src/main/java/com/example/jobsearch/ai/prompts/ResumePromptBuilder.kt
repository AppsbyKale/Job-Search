package com.example.jobsearch.ai.prompts

import com.example.jobsearch.data.Job
import com.example.jobsearch.ai.prompts.BasePrompt.BASE_SYSTEM
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_JOB_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.MAX_RESUME_WORDS
import com.example.jobsearch.ai.prompts.BasePrompt.truncateWords

object ResumePromptBuilder {
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

    fun taggingPrompt(title: String, description: String): String {
        return """
            Categorize this job posting into 2-4 short, professional tags (e.g., "Android", "AI", "Project Management", "Remote"). 
            Output ONLY the tags as a comma-separated list. No intro, no fluff.
            
            JOB TITLE: $title
            DESC: ${truncateWords(description, 150)}
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

        val jobInfo = "TARGET JOB TO APPLY FOR (DO NOT list this as past work experience in the resume!):\nTitle: ${job.title}\nCompany: ${job.company}\nDescription: ${truncateWords(job.description, 200)}"
        
        val atsCompliance = """
            ### ATS COMPLIANCE ADD-ON
            The following rules govern how the resume is written and structured so it parses correctly in Applicant Tracking Systems (Workday, Taleo, iCIMS, Greenhouse, Lever, etc.). When these conflict with stylistic instructions elsewhere, these rules win. They NEVER override factual accuracy — do not invent experience, skills, dates, or credentials.
            KEYWORD MATCHING:
            1. Mirror the job description's exact terminology for every skill, tool, and qualification the candidate genuinely has. If the JD says "RESTful APIs" and source says "REST APIs", use the JD's phrasing.
            2. On first use of key terms, include both the acronym and the spelled-out form (e.g., "Search Engine Optimization (SEO)").
            3. Include a dedicated "Skills" section as a simple comma-separated or bulleted list of relevant hard skills (highest-weighted section for keyword matching).
            4. Keep the candidate's real job titles. Tailor bullet wording to the JD, never retitle roles to match it.
            5. Every keyword must reflect genuine experience. Never insert JD keywords the candidate does not have.
        """.trimIndent()

        val head = """
            Rewrite this resume using these facts. **IMPORTANT: Ensure ALL jobs and dates from the base resume are included in the tailored output. Each job must have between 2 and 10 bullet points.** 
            CRITICAL RULE: The target job above is the role the candidate is APPLYING FOR. DO NOT list this target company or role as past employment/experience in the resume's experience array. 
            JSON ONLY.
            $atsCompliance
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
            ${truncateWords(job.company, BasePrompt.MAX_COMPANY_WORDS)}

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

            <TARGET JOB TO APPLY FOR (DO NOT list this as past work experience!)>
            Title: ${job.title}
            Company: ${truncateWords(job.company, BasePrompt.MAX_COMPANY_WORDS)}
            Description: $description
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
            The data below is the candidate's employment history. Rewrite the resume so it is tailored to the job posting and incorporates the follow-up answers. **IMPORTANT: Ensure ALL jobs and dates from the base resume are included in the tailored output. Each job must have between 2 and 10 bullet points.** 
            CRITICAL RULE: The target job/company below is the role the candidate is APPLYING FOR. DO NOT list this target company or role as past employment/experience in the resume's experience array. 
            Never invent facts.

            <RAW EXPERIENCE FACTS>
            $resume

            <TARGET JOB TO APPLY FOR (DO NOT list this as past work experience!)>
            Title: ${job.title}
            Company: ${truncateWords(job.company, BasePrompt.MAX_COMPANY_WORDS)}
            Description: $description

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
}
