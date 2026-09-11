package com.example.jobsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interview_questions")
data class InterviewQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val question: String,
    val position: Int
)
