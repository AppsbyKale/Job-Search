package com.example.jobsearch.data

enum class JobStatus(val label: String) {
    SYNCED("Synced"),
    SAVED("Saved"),
    APPLIED("Applied"),
    INTERVIEWING("Interviewing"),
    OFFER("Offer"),
    REJECTED("Rejected"),
    ARCHIVED("Archived");

    companion object {
        fun fromName(name: String): JobStatus =
            entries.firstOrNull { it.name == name } ?: SAVED
    }
}
