package com.example.jobsearch.data

import kotlinx.coroutines.flow.Flow

class InterviewRepository(private val dao: InterviewDao) {

    fun observeQuestions(jobId: Long): Flow<List<InterviewQuestion>> = dao.observeQuestions(jobId)
    fun observeAnswers(jobId: Long): Flow<List<InterviewAnswer>> = dao.observeAnswers(jobId)
    fun observeReport(jobId: Long): Flow<InterviewReport?> = dao.observeReport(jobId)

    suspend fun getQuestions(jobId: Long): List<InterviewQuestion> = dao.getQuestions(jobId)
    suspend fun getAnswer(questionId: Long): InterviewAnswer? = dao.getAnswer(questionId)

    suspend fun replaceQuestions(jobId: Long, questions: List<InterviewQuestion>): List<Long> {
        dao.deleteQuestions(jobId)
        return dao.insertQuestions(questions)
    }

    suspend fun upsertAnswer(answer: InterviewAnswer) = dao.upsertAnswer(answer)

    suspend fun upsertReport(report: InterviewReport) = dao.upsertReport(report)

    suspend fun resetForJob(jobId: Long) {
        dao.deleteQuestions(jobId)
        dao.deleteAnswers(jobId)
        dao.deleteReport(jobId)
    }

    suspend fun deleteForJob(jobId: Long) {
        dao.deleteQuestions(jobId)
        dao.deleteAnswers(jobId)
        dao.deleteReport(jobId)
    }
}
