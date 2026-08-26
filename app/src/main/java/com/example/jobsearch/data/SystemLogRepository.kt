package com.example.jobsearch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemLogRepository @Inject constructor() {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(message: String) {
        val timestamp = LocalTime.now().format(formatter)
        val line = "[$timestamp] $message"
        _logs.update { 
            (listOf(line) + it).take(50) 
        }
        android.util.Log.d("SystemLog", line)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
