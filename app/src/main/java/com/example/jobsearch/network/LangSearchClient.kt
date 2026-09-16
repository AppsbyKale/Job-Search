package com.example.jobsearch.network

import android.util.Log
import com.example.jobsearch.data.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LangSearchClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun searchCompany(company: String, location: String = ""): String {
        val apiKey = settingsRepository.langSearchApiKey.first()
        if (apiKey.isBlank() || company.isBlank()) return ""

        val query = if (location.isNotBlank()) {
            "$company $location company overview mission culture industry values"
        } else {
            "$company company overview mission culture industry values"
        }

        return try {
            val response = client.get("https://api.langsearch.com/v1/web-search") {
                header("Authorization", "Bearer $apiKey")
                parameter("q", query)
                parameter("count", 3)
            }
            if (response.status == HttpStatusCode.OK) {
                response.bodyAsText()
            } else {
                Log.w("LangSearchClient", "LangSearch returned status: ${response.status}")
                ""
            }
        } catch (e: Exception) {
            Log.e("LangSearchClient", "LangSearch request failed", e)
            ""
        }
    }
}
