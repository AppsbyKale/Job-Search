package com.example.jobsearch.di

import android.content.Context
import com.example.jobsearch.ai.CloudModelManager
import com.example.jobsearch.ai.GenerationRepository
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.InterviewGenerator
import com.example.jobsearch.ai.ModelManager
import com.example.jobsearch.data.BackupRepository
import com.example.jobsearch.data.InterviewDao
import com.example.jobsearch.data.InterviewRepository
import com.example.jobsearch.data.JobDao
import com.example.jobsearch.data.JobDatabase
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.TrainingExampleDao
import com.example.jobsearch.data.TrainingRepository
import com.example.jobsearch.document.DocumentExporter
import com.example.jobsearch.parsing.HtmlRenderer
import com.example.jobsearch.parsing.JobParser
import com.example.jobsearch.resume.ResumeImporter
import com.example.jobsearch.speech.AudioRecorder
import com.example.jobsearch.speech.SpeechToText
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideJobRepository(jobDao: JobDao): JobRepository {
        return JobRepository(jobDao)
    }

    @Provides
    @Singleton
    fun provideInterviewRepository(interviewDao: InterviewDao): InterviewRepository {
        return InterviewRepository(interviewDao)
    }

    @Provides
    @Singleton
    fun provideTrainingRepository(dao: TrainingExampleDao, settings: SettingsRepository): TrainingRepository {
        return TrainingRepository(dao, settings)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideJobParser(@ApplicationContext context: Context): JobParser {
        return JobParser(HtmlRenderer(context))
    }

    @Provides
    @Singleton
    fun provideResumeImporter(): ResumeImporter {
        return ResumeImporter()
    }

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        systemLog: com.example.jobsearch.data.SystemLogRepository
    ): IModelManager {
        return ModelManager(context, settingsRepository, systemLog)
    }

    @Provides
    @Singleton
    fun provideCloudModelManager(settingsRepository: SettingsRepository): CloudModelManager {
        return CloudModelManager(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideDocumentExporter(@ApplicationContext context: Context): DocumentExporter {
        return DocumentExporter(context)
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        database: JobDatabase,
        settingsRepository: SettingsRepository,
        systemLog: com.example.jobsearch.data.SystemLogRepository
    ): BackupRepository {
        return BackupRepository(context, database, settingsRepository, systemLog)
    }

    @Provides
    @Singleton
    fun provideGenerationRepository(
        @ApplicationScope scope: CoroutineScope,
        modelManager: IModelManager,
        cloudModelManager: CloudModelManager,
        jobRepository: JobRepository,
        interviewRepository: InterviewRepository,
        settingsRepository: SettingsRepository,
        trainingRepository: TrainingRepository,
        systemLog: com.example.jobsearch.data.SystemLogRepository
    ): GenerationRepository {
        return GenerationRepository(
            scope = scope,
            modelManager = modelManager,
            cloudModelManager = cloudModelManager,
            repository = jobRepository,
            interviewRepository = interviewRepository,
            settings = settingsRepository,
            trainingRepository = trainingRepository,
            systemLog = systemLog
        )
    }

    @Provides
    @Singleton
    fun provideInterviewGenerator(
        @ApplicationScope scope: CoroutineScope,
        modelManager: IModelManager,
        settingsRepository: SettingsRepository,
        jobRepository: JobRepository,
        interviewRepository: InterviewRepository,
        trainingRepository: TrainingRepository
    ): InterviewGenerator {
        return InterviewGenerator(
            scope = scope,
            modelManager = modelManager,
            settings = settingsRepository,
            jobRepository = jobRepository,
            interviewRepository = interviewRepository,
            trainingRepository = trainingRepository
        )
    }

    @Provides
    @Singleton
    fun provideSpeechToText(@ApplicationContext context: Context): SpeechToText {
        return SpeechToText(context)
    }

    @Provides
    @Singleton
    fun provideAudioRecorder(@ApplicationContext context: Context): AudioRecorder {
        return AudioRecorder(context)
    }

    @Provides
    @Singleton
    fun provideInterviewAudioDir(@ApplicationContext context: Context): File {
        return File(context.filesDir, "interview_audio")
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        @ApplicationContext context: Context,
        jobRepository: JobRepository,
        trainingRepository: TrainingRepository,
        modelManager: IModelManager,
        jobParser: JobParser,
        systemLog: com.example.jobsearch.data.SystemLogRepository
    ): com.example.jobsearch.data.SyncRepository {
        return com.example.jobsearch.data.SyncRepository(
            context,
            jobRepository,
            trainingRepository,
            modelManager,
            jobParser,
            systemLog
        )
    }
}
