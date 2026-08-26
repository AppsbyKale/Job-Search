package com.example.jobsearch.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val defaultFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun formatDate(timestamp: Long): String {
        return defaultFormatter.format(Date(timestamp))
    }
}
