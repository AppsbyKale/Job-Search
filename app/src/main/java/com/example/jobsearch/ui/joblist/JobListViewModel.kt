package com.example.jobsearch.ui.joblist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.JobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the job list screen.
 * Handles filtering and basic job management (delete, status update).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JobListViewModel @Inject constructor(
    private val repository: JobRepository,
    private val interviewRepository: InterviewRepository
) : ViewModel() {

    data class JobUiModel(
        val job: Job,
        val statusLabel: String
    )

    private val _filter = MutableStateFlow<JobStatus?>(null)
    val filter: StateFlow<JobStatus?> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val syncedJobs: StateFlow<List<Job>> = repository.observeByStatus(JobStatus.SYNCED.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val jobs: StateFlow<List<JobUiModel>> = combine(
        _filter.flatMapLatest { status ->
            if (status == null) repository.observeJobs()
            else repository.observeByStatus(status.name)
        },
        _searchQuery
    ) { list, query ->
        list.filter { job ->
            job.status != JobStatus.SYNCED.name &&
            (query.isBlank() || 
            job.title.contains(query, ignoreCase = true) || 
            job.company.contains(query, ignoreCase = true))
        }.map { job ->
            JobUiModel(
                job = job,
                statusLabel = JobStatus.fromName(job.status).label
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(status: JobStatus?) {
        _filter.value = status
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun updateStatus(id: Long, status: JobStatus) {
        viewModelScope.launch {
            repository.getJob(id)?.let { job ->
                repository.updateJob(job.copy(status = status.name))
            }
        }
    }

    fun deleteJob(id: Long) {
        viewModelScope.launch {
            interviewRepository.deleteForJob(id)
            repository.deleteJob(id)
        }
    }
}
