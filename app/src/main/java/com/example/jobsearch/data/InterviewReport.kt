package com.example.jobsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interview_reports")
data class InterviewReport(
    @PrimaryKey val jobId: Long,
    val overall: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
