package com.example.jobsearch.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jobsearch.R
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.PromptBuilder
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobRepository
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.data.PairRequest
import com.example.jobsearch.data.PairResponse
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.data.SharedJob
import com.example.jobsearch.data.SyncResponse
import com.example.jobsearch.data.SystemLogRepository
import com.example.jobsearch.data.TrainingRepository
import com.example.jobsearch.parsing.JobParser
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncServer @Inject constructor(
    private val context: Context,
    private val jobRepository: JobRepository,
    private val trainingRepository: TrainingRepository,
    private val modelManager: IModelManager,
    private val parser: JobParser,
    private val settingsRepository: SettingsRepository,
    private val systemLog: SystemLogRepository
) {
    private var server: ApplicationEngine? = null
    private val bgScope = CoroutineScope(Dispatchers.IO)

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _recentSyncs = MutableStateFlow<List<String>>(emptyList())
    val recentSyncs: StateFlow<List<String>> = _recentSyncs.asStateFlow()

    private val _currentPin = MutableStateFlow("123456")
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    @Volatile
    private var activeToken: String? = null

    @Volatile
    private var tokenExpiryTime: Long = 0L

    private fun generatePin(): String {
        return (100000..999999).random().toString()
    }

    fun refreshPin(): String {
        val newPin = generatePin()
        _currentPin.value = newPin
        activeToken = null
        tokenExpiryTime = 0L
        bgScope.launch {
            settingsRepository.saveSyncPairing(newPin, "", 0L)
        }
        return newPin
    }

    private fun isValidToken(authHeader: String?): Boolean {
        if (authHeader.isNullOrBlank()) return false
        val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            authHeader.trim()
        }
        val currentActive = activeToken ?: return false
        if (System.currentTimeMillis() > tokenExpiryTime) return false
        return token == currentActive
    }

    private fun restoreSavedCredentials() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                var pin = settingsRepository.syncPin.first()
                if (pin.isBlank()) {
                    pin = generatePin()
                    settingsRepository.saveSyncPin(pin)
                }
                _currentPin.value = pin

                val token = settingsRepository.syncToken.first()
                val expiry = settingsRepository.syncTokenExpiry.first()
                if (token.isNotBlank() && System.currentTimeMillis() <= expiry) {
                    activeToken = token
                    tokenExpiryTime = expiry
                    Log.i("SyncServer", "Restored persistent 30-day pairing token.")
                }
            }
        }.onFailure { e ->
            Log.e("SyncServer", "Failed to restore sync credentials", e)
        }
    }

    fun start(port: Int) {
        if (server != null) return

        restoreSavedCredentials()

        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
            }
            routing {
                post("/pair") {
                    try {
                        val req = call.receive<PairRequest>()
                        if (req.pin == _currentPin.value) {
                            val newToken = UUID.randomUUID().toString()
                            val expiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) // 30 days
                            activeToken = newToken
                            tokenExpiryTime = expiry

                            bgScope.launch {
                                settingsRepository.saveSyncPairing(_currentPin.value, newToken, expiry)
                            }
                            
                            Log.i("SyncServer", "Client paired successfully with PIN.")
                            systemLog.log("Sync: Desktop client paired successfully.")
                            call.respond(HttpStatusCode.OK, PairResponse(
                                status = "ok",
                                token = newToken,
                                expiresInDays = 30
                            ))
                        } else {
                            Log.w("SyncServer", "Pairing failed: Invalid PIN '${req.pin}'")
                            call.respond(HttpStatusCode.Unauthorized, PairResponse(
                                status = "error",
                                message = "Invalid pairing PIN. Check the PIN displayed in JobSearch Settings."
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("SyncServer", "Error in /pair", e)
                        call.respond(HttpStatusCode.BadRequest, PairResponse(
                            status = "error",
                            message = e.message ?: "Invalid pairing payload"
                        ))
                    }
                }

                post("/add-job") {
                    try {
                        val authHeader = call.request.header(HttpHeaders.Authorization)
                        if (!isValidToken(authHeader)) {
                            Log.w("SyncServer", "Unauthorized request to /add-job")
                            call.respond(HttpStatusCode.Unauthorized, SyncResponse(
                                status = "error",
                                message = "Unauthorized: Invalid or expired pairing token. Please pair using the PIN in Settings."
                            ))
                            return@post
                        }

                        val sharedJob = call.receive<SharedJob>()
                        Log.i("SyncServer", "Received job request: ${sharedJob.title}")

                        // Filter duplicates: check if job already exists in app by URL or Title+Company
                        val existingJob = jobRepository.findExistingJob(sharedJob.url, sharedJob.title, sharedJob.company)
                        if (existingJob != null) {
                            if (existingJob.status == JobStatus.SYNCED.name) {
                                // If job is pending in Synced Jobs list, bump date and update description
                                val updated = existingJob.copy(
                                    title = sharedJob.title.ifBlank { existingJob.title },
                                    company = sharedJob.company.ifBlank { existingJob.company },
                                    description = parser.trimFluff(sharedJob.description),
                                    dateAdded = System.currentTimeMillis()
                                )
                                withContext(Dispatchers.IO + NonCancellable) {
                                    jobRepository.updateJob(updated)
                                }
                                Log.i("SyncServer", "Re-synced existing pending job: '${sharedJob.title}' (ID: ${existingJob.id})")
                                systemLog.log("Sync: Re-synced existing job '${sharedJob.title}'.")
                                call.respond(HttpStatusCode.OK, SyncResponse(
                                    status = "success",
                                    id = existingJob.id,
                                    message = "Job updated in Synced list"
                                ))
                                return@post
                            } else {
                                Log.i("SyncServer", "Duplicate job ignored: '${sharedJob.title}' (ID: ${existingJob.id})")
                                systemLog.log("Sync: Ignored duplicate job '${sharedJob.title}' at '${sharedJob.company}'.")
                                call.respond(HttpStatusCode.OK, SyncResponse(
                                    status = "exists",
                                    id = existingJob.id,
                                    message = "Job already exists in app"
                                ))
                                return@post
                            }
                        }

                        systemLog.log("Sync: Received job '${sharedJob.title}' from desktop.")
                        val rawCleanedDesc = parser.trimFluff(sharedJob.description)

                        val newJob = Job(
                            title = sharedJob.title,
                            company = sharedJob.company,
                            url = sharedJob.url,
                            description = rawCleanedDesc,
                            dateAdded = System.currentTimeMillis(),
                            status = JobStatus.SYNCED.name,
                            notes = sharedJob.notes ?: "",
                            tags = ""
                        )
                        
                        Log.d("SyncServer", "Saving job to database...")
                        val id = withContext(Dispatchers.IO + NonCancellable) {
                            jobRepository.addJob(newJob)
                        }
                        
                        Log.i("SyncServer", "Successfully added job with ID: $id")
                        systemLog.log("Sync: Job saved (ID: $id).")
                        
                        // Track last 5 jobs
                        _recentSyncs.value = (listOf(sharedJob.title.ifBlank { sharedJob.company }) + _recentSyncs.value).take(5)

                        try {
                            showJobReceivedNotification(sharedJob.title, sharedJob.company)
                        } catch (e: Exception) {
                            Log.e("SyncServer", "Failed to show notification, but job was added", e)
                        }

                        // Respond IMMEDIATELY to HTTP client (in ~50ms) to prevent extension timeout
                        call.respond(HttpStatusCode.OK, SyncResponse(status = "success", id = id))

                        // Trigger background AI auto-sweep & auto-tagging asynchronously
                        if (modelManager.isModelDownloaded()) {
                            bgScope.launch {
                                try {
                                    systemLog.log("Sync: Background auto-sweeping job...")
                                    var cleanedDesc = rawCleanedDesc
                                    var jobTags = ""

                                    val cleanPrompt = PromptBuilder.smartCleanPrompt(cleanedDesc)
                                    val cleaned = modelManager.generate(cleanPrompt, source = "Sync Auto-Sweep").trim()
                                    if (cleaned.isNotBlank()) {
                                        trainingRepository.logExample("task", "auto_sweep_sync", cleanPrompt, cleaned)
                                        cleanedDesc = cleaned
                                    }

                                    val tagPrompt = PromptBuilder.taggingPrompt(sharedJob.title, rawCleanedDesc)
                                    val tags = modelManager.generate(tagPrompt, source = "Sync Auto-Tagging").trim()
                                    if (tags.isNotBlank()) {
                                        trainingRepository.logExample("task", "auto_tagging_sync", tagPrompt, tags)
                                        jobTags = tags
                                    }

                                    jobRepository.getJob(id)?.let { savedJob ->
                                        jobRepository.updateJob(
                                            savedJob.copy(
                                                description = cleanedDesc,
                                                tags = jobTags
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("SyncServer", "Background AI Sweep/Tag failed", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SyncServer", "Critical error in /add-job", e)
                        call.respond(HttpStatusCode.InternalServerError, SyncResponse(
                            status = "error", 
                            message = e.message ?: "Unknown error",
                            type = e.javaClass.simpleName
                        ))
                    }
                }
                get("/status") {
                    Log.i("SyncServer", "Status check received")
                    call.respond(mapOf(
                        "status" to "ok",
                        "app" to "JobSearch",
                        "paired" to (activeToken != null && System.currentTimeMillis() <= tokenExpiryTime)
                    ))
                }
            }
        }.start(wait = false)
        _isServerRunning.value = true
        Log.i("SyncServer", "Server started on port $port with PIN: ${_currentPin.value}")
    }

    fun stop() {
        server?.stop(1000L, 2000L, TimeUnit.MILLISECONDS)
        server = null
        _isServerRunning.value = false
        Log.i("SyncServer", "Server stopped")
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            
            val wlanIp = interfaces
                .filter { it.name.contains("wlan", ignoreCase = true) || it.name.contains("eth", ignoreCase = true) }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress

            if (!wlanIp.isNullOrBlank()) return wlanIp

            return interfaces
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            Log.e("SyncServer", "Failed to get IP address", e)
        }
        return null
    }

    private fun showJobReceivedNotification(title: String, company: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channel = NotificationChannel(
            SYNC_CHANNEL_ID,
            "Job Sync",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for jobs received from desktop"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Job Received")
            .setContentText("$title at $company")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val SYNC_CHANNEL_ID = "job_sync"
    }
}
