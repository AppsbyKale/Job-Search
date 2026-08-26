package com.example.jobsearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Job::class,
        InterviewQuestion::class,
        InterviewAnswer::class,
        InterviewReport::class,
        TrainingExample::class
    ],
    version = 7,
    exportSchema = false
)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun interviewDao(): InterviewDao
    abstract fun trainingExampleDao(): TrainingExampleDao

    companion object {
        @Volatile
        private var instance: JobDatabase? = null

        fun get(context: Context): JobDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JobDatabase::class.java,
                    "jobsearch.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jobs` ADD COLUMN `initialEmailText` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `training_examples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `appName` TEXT NOT NULL, `feature` TEXT NOT NULL, `inputPrompt` TEXT NOT NULL, `modelOutput` TEXT NOT NULL, `correctedOutput` TEXT, `isGoodExample` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `extraMetadata` TEXT)"
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interview_questions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jobId` INTEGER NOT NULL, `question` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interview_answers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jobId` INTEGER NOT NULL, `questionId` INTEGER NOT NULL, `text` TEXT NOT NULL, `audioPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `score` INTEGER, `feedback` TEXT NOT NULL, `modelAnswer` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interview_reports` (`jobId` INTEGER PRIMARY KEY NOT NULL, `overall` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
