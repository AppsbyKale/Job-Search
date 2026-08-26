package com.example.jobsearch.di

import android.content.Context
import com.example.jobsearch.data.InterviewDao
import com.example.jobsearch.data.JobDao
import com.example.jobsearch.data.JobDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JobDatabase {
        return JobDatabase.get(context)
    }

    @Provides
    fun provideJobDao(database: JobDatabase): JobDao {
        return database.jobDao()
    }

    @Provides
    fun provideInterviewDao(database: JobDatabase): InterviewDao {
        return database.interviewDao()
    }

    @Provides
    fun provideTrainingExampleDao(database: JobDatabase): com.example.jobsearch.data.TrainingExampleDao {
        return database.trainingExampleDao()
    }
}
