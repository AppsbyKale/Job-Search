package com.example.jobsearch.ai

import com.example.jobsearch.ai.prompts.CheatSheetPromptBuilder
import com.example.jobsearch.ai.prompts.CoverLetterPromptBuilder
import com.example.jobsearch.ai.prompts.EmailPromptBuilder
import com.example.jobsearch.ai.prompts.InterviewPromptBuilder
import com.example.jobsearch.ai.prompts.ResumePromptBuilder
import com.example.jobsearch.data.Job

object PromptBuilder {
    fun distillationPrompt(job: Job, resumeText: String, qa: List<Pair<String, String>> = emptyList()) =
        ResumePromptBuilder.distillationPrompt(job, resumeText, qa)

    fun smartCleanPrompt(description: String) =
        ResumePromptBuilder.smartCleanPrompt(description)

    fun taggingPrompt(title: String, description: String) =
        ResumePromptBuilder.taggingPrompt(title, description)

    fun resumePrompt(job: Job, resumeText: String, distilledFacts: String = "", qa: List<Pair<String, String>> = emptyList(), steeringInstructions: String? = null) =
        ResumePromptBuilder.resumePrompt(job, resumeText, distilledFacts, qa, steeringInstructions)

    fun coverLetterPrompt(job: Job, tailoredResume: String, qa: List<Pair<String, String>> = emptyList(), steeringInstructions: String? = null) =
        CoverLetterPromptBuilder.coverLetterPrompt(job, tailoredResume, qa, steeringInstructions)

    fun interviewQuestionsPrompt(job: Job, resumeText: String, count: Int) =
        InterviewPromptBuilder.interviewQuestionsPrompt(job, resumeText, count)

    fun interviewFeedbackPrompt(job: Job, resumeText: String, question: String, answer: String) =
        InterviewPromptBuilder.interviewFeedbackPrompt(job, resumeText, question, answer)

    fun interviewOverallPrompt(job: Job, resumeText: String, items: List<Pair<String, String>>) =
        InterviewPromptBuilder.interviewOverallPrompt(job, resumeText, items)

    fun matchPercentPrompt(job: Job, resumeText: String) =
        ResumePromptBuilder.matchPercentPrompt(job, resumeText)

    fun improveQuestionsPrompt(job: Job, resumeText: String, currentPercent: Int, count: Int = 4) =
        ResumePromptBuilder.improveQuestionsPrompt(job, resumeText, currentPercent, count)

    fun improvedResumePrompt(job: Job, resumeText: String, qa: List<Pair<String, String>>) =
        ResumePromptBuilder.improvedResumePrompt(job, resumeText, qa)

    fun cheatSheetPrompt(job: Job, resumeText: String, companyInfo: String = "", includeOverview: Boolean = true, includeChallenges: Boolean = true, includeDayToDay: Boolean = true, includeHighlights: Boolean = true, customQuestions: String = "", strengths: String = "", weaknesses: String = "") =
        CheatSheetPromptBuilder.cheatSheetPrompt(job, resumeText, companyInfo, includeOverview, includeChallenges, includeDayToDay, includeHighlights, customQuestions, strengths, weaknesses)

    fun initialEmailPrompt(job: Job, resumeText: String) =
        EmailPromptBuilder.initialEmailPrompt(job, resumeText)

    fun followUpEmailPrompt(job: Job, resumeText: String) =
        EmailPromptBuilder.followUpEmailPrompt(job, resumeText)

    fun manualQuestionPrompt(job: Job, resumeText: String, question: String) =
        CheatSheetPromptBuilder.manualQuestionPrompt(job, resumeText, question)

    fun parseQuestions(text: String) =
        InterviewPromptBuilder.parseQuestions(text)

    fun parseFeedback(text: String) =
        InterviewPromptBuilder.parseFeedback(text)

    fun splitModelAnswer(feedback: String) =
        InterviewPromptBuilder.splitModelAnswer(feedback)
}
