package com.example.jobsearch.data

import kotlinx.coroutines.flow.Flow

class JobRepository(private val dao: JobDao) {
    fun observeJobs(): Flow<List<Job>> = dao.observeAll()
    fun observeJob(id: Long): Flow<Job?> = dao.observeById(id)
    fun observeByStatus(status: String): Flow<List<Job>> = dao.observeByStatus(status)
    suspend fun getJob(id: Long): Job? = dao.getById(id)
    suspend fun addJob(job: Job): Long = dao.insert(job)
    suspend fun updateJob(job: Job) = dao.update(job)
    suspend fun deleteJob(id: Long) = dao.deleteById(id)

    suspend fun findExistingJob(url: String, title: String, company: String): Job? {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isNotBlank()) {
            val byUrl = dao.findByUrl(trimmedUrl)
            if (byUrl != null) return byUrl
        }
        val trimmedTitle = title.trim()
        val trimmedCompany = company.trim()
        if (trimmedTitle.isNotBlank() && trimmedCompany.isNotBlank()) {
            val byTitleCompany = dao.findByTitleAndCompany(trimmedTitle, trimmedCompany)
            if (byTitleCompany != null) return byTitleCompany
        }
        return null
    }
}
