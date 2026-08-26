package com.example.jobsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val company: String = "",
    val url: String = "",
    val description: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val status: String = JobStatus.SAVED.name,
    val resumeText: String = "",
    val coverLetterText: String = "",
    val initialEmailText: String = "",
    val cheatSheetText: String = "",
    val followUpEmailText: String = "",
    val notes: String = ""
) {
    val hasResume: Boolean get() = resumeText.isNotBlank()
    val hasCoverLetter: Boolean get() = coverLetterText.isNotBlank()
    val hasInitialEmail: Boolean get() = initialEmailText.isNotBlank()
    val hasCheatSheet: Boolean get() = cheatSheetText.isNotBlank()
    val hasFollowUpEmail: Boolean get() = followUpEmailText.isNotBlank()
    val hasNotes: Boolean get() = notes.isNotBlank()
}
