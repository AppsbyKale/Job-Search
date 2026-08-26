package com.example.jobsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interview_answers")
data class InterviewAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val questionId: Long,
    val text: String = "",
    val audioPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val score: Int? = null,
    val feedback: String = "",
    val modelAnswer: String = ""
) {
    val hasAnswer: Boolean get() = text.isNotBlank()
}
