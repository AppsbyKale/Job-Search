package com.example.jobsearch.ai

import com.example.jobsearch.data.SettingsRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.generationConfig
import kotlin.time.Duration.Companion.seconds

/**
 * Manages cloud-based AI generation using the Google Gemini API.
 */
class CloudModelManager(private val settings: SettingsRepository) {

    /**
     * Generates content using the Gemini model based on the provided prompt.
     * Requires a valid Gemini API key in settings.
     */
    suspend fun generate(prompt: String): String {
        val apiKey = settings.getGeminiApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is missing. Please add it in Settings.")
        }

        val config = generationConfig {
            temperature = 0.4f
            topK = 32
            topP = 0.95f
            maxOutputTokens = 4096
            responseMimeType = "application/json"
        }

        val model = GenerativeModel(
            modelName = "gemini-3.5-flash-lite",
            apiKey = apiKey,
            generationConfig = config,
            requestOptions = RequestOptions(timeout = 120.seconds),
        )

        val response = model.generateContent(prompt)
        return response.text ?: ""
    }
}
