package com.example.jobsearch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert
    suspend fun insert(job: Job): Long

    @Update
    suspend fun update(job: Job)

    @Query("SELECT * FROM jobs ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: Long): Flow<Job?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: Long): Job?

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY dateAdded DESC")
    fun observeByStatus(status: String): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE LOWER(url) = LOWER(:url) LIMIT 1")
    suspend fun findByUrl(url: String): Job?

    @Query("SELECT * FROM jobs WHERE LOWER(title) = LOWER(:title) AND LOWER(company) = LOWER(:company) LIMIT 1")
    suspend fun findByTitleAndCompany(title: String, company: String): Job?

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM jobs")
    suspend fun deleteAll()
}
