package com.example.jobsearch.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingRepository @Inject constructor(
    private val dao: TrainingExampleDao,
    private val settings: SettingsRepository,
) {
    fun observeExamples(): Flow<List<TrainingExample>> = dao.observeAll()

    suspend fun logExample(
        appName: String,
        feature: String,
        inputPrompt: String,
        modelOutput: String,
        isGood: Boolean = false,
        extraMetadata: String? = null
    ): Long {
        if (!settings.trainingLoggingEnabled.first()) return -1L
        
        val example = TrainingExample(
            appName = appName,
            feature = feature,
            inputPrompt = inputPrompt,
            modelOutput = modelOutput,
            isGoodExample = isGood,
            extraMetadata = extraMetadata
        )
        return dao.insert(example)
    }

    suspend fun markCorrected(id: Long, correctedOutput: String) {
        dao.getById(id)?.let { existing ->
            dao.update(existing.copy(correctedOutput = correctedOutput, isGoodExample = true))
        }
    }

    suspend fun markAsGood(id: Long, isGood: Boolean) {
        dao.getById(id)?.let { existing ->
            dao.update(existing.copy(isGoodExample = isGood))
        }
    }

    suspend fun deleteExample(id: Long) {
        dao.deleteById(id)
    }

    suspend fun exportAsJson(): String {
        val examples = dao.getGoodExamples()
        val list = examples.map { 
            "{\"instruction\": \"${it.inputPrompt.escape()}\", \"output\": \"${(it.correctedOutput ?: it.modelOutput).escape()}\"}"
        }
        return list.joinToString("\n")
    }

    private fun String.escape() = this.replace("\"", "\\\"").replace("\n", "\\n")
}
