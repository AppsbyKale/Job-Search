package com.example.jobsearch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InterviewDao {
    @Query("SELECT * FROM interview_questions WHERE jobId = :jobId ORDER BY position ASC")
    fun observeQuestions(jobId: Long): Flow<List<InterviewQuestion>>

    @Query("SELECT * FROM interview_questions WHERE jobId = :jobId ORDER BY position ASC")
    suspend fun getQuestions(jobId: Long): List<InterviewQuestion>

    @Query("SELECT * FROM interview_answers WHERE jobId = :jobId")
    fun observeAnswers(jobId: Long): Flow<List<InterviewAnswer>>

    @Query("SELECT * FROM interview_answers WHERE questionId = :questionId")
    suspend fun getAnswer(questionId: Long): InterviewAnswer?

    @Query("SELECT * FROM interview_reports WHERE jobId = :jobId")
    fun observeReport(jobId: Long): Flow<InterviewReport?>

    @Insert
    suspend fun insertQuestions(questions: List<InterviewQuestion>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswer(answer: InterviewAnswer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: InterviewReport)

    @Query("DELETE FROM interview_questions WHERE jobId = :jobId")
    suspend fun deleteQuestions(jobId: Long)

    @Query("DELETE FROM interview_answers WHERE jobId = :jobId")
    suspend fun deleteAnswers(jobId: Long)

    @Query("DELETE FROM interview_reports WHERE jobId = :jobId")
    suspend fun deleteReport(jobId: Long)
}
