package com.example.jobsearch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingExampleDao {
    @Query("SELECT * FROM training_examples ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TrainingExample>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(example: TrainingExample): Long

    @Update
    suspend fun update(example: TrainingExample)

    @Query("DELETE FROM training_examples WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM training_examples WHERE id = :id")
    suspend fun getById(id: Long): TrainingExample?

    @Query("SELECT * FROM training_examples WHERE isGoodExample = 1")
    suspend fun getGoodExamples(): List<TrainingExample>
}
