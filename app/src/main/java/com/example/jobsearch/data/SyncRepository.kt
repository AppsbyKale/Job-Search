package com.example.jobsearch.data

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.util.Log
import com.example.jobsearch.R
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.PromptBuilder
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SharedJob(
    val title: String,
    val company: String,
    val description: String,
    val url: String,
    val notes: String? = null
)

@Serializable
data class SyncResponse(
    val status: String,
    val id: Long? = null,
    val message: String? = null,
    val type: String? = null
)

@Serializable
data class PairRequest(
    val pin: String
)

@Serializable
data class PairResponse(
    val status: String,
    val token: String? = null,
    val message: String? = null,
    val expiresInDays: Int? = null
)

@Singleton
class SyncRepository @Inject constructor(
    private val context: Context,
    private val jobRepository: JobRepository,
    private val trainingRepository: TrainingRepository,
    private val modelManager: IModelManager,
    private val parser: JobParser,
    private val settingsRepository: SettingsRepository,
    private val systemLog: SystemLogRepository
) {
    private var server: ApplicationEngine? = null

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
        CoroutineScope(Dispatchers.IO).launch {
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

    fun startServer(port: Int) {
        if (server != null) return

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
                        if (req.pin == _currentPin.value || _currentPin.value.isBlank()) {
                            val newToken = UUID.randomUUID().toString()
                            val expiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L) // 30 days
                            activeToken = newToken
                            tokenExpiryTime = expiry

                            CoroutineScope(Dispatchers.IO).launch {
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
                        val sharedJob = call.receive<SharedJob>()
                        Log.i("SyncServer", "Received job request: ${sharedJob.title}")

                        // Check if job already exists in app by URL or Title+Company
                        val existingJob = jobRepository.findExistingJob(sharedJob.url, sharedJob.title, sharedJob.company)
                        if (existingJob != null) {
                            val cleanedDesc = parser.trimFluff(sharedJob.description)
                            val updated = existingJob.copy(
                                title = sharedJob.title.ifBlank { existingJob.title },
                                company = sharedJob.company.ifBlank { existingJob.company },
                                description = if (cleanedDesc.isNotBlank()) cleanedDesc else existingJob.description,
                                notes = sharedJob.notes ?: existingJob.notes,
                                status = JobStatus.SYNCED.name,
                                dateAdded = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO + NonCancellable) {
                                jobRepository.updateJob(updated)
                            }
                            Log.i("SyncServer", "Restored/Updated job in Synced list: '${sharedJob.title}' (ID: ${existingJob.id})")
                            systemLog.log("Sync: Restored job '${sharedJob.title}' to Synced list.")
                            _recentSyncs.value = (listOf(sharedJob.title.ifBlank { sharedJob.company }) + _recentSyncs.value).take(5)
                            try {
                                showJobReceivedNotification(sharedJob.title, sharedJob.company)
                            } catch (e: Exception) {
                                Log.e("SyncServer", "Failed to show notification, but job was updated", e)
                            }
                            call.respond(HttpStatusCode.OK, SyncResponse(
                                status = "success",
                                id = existingJob.id,
                                message = "Job restored to Synced list"
                            ))
                            return@post
                        }

                        systemLog.log("Sync: Received job '${sharedJob.title}' from desktop.")
                        
                        val cleanedDesc = parser.trimFluff(sharedJob.description)

                        val job = Job(
                            title = sharedJob.title,
                            company = sharedJob.company,
                            url = sharedJob.url,
                            description = cleanedDesc,
                            dateAdded = System.currentTimeMillis(),
                            status = JobStatus.SYNCED.name,
                            notes = sharedJob.notes ?: "",
                            tags = ""
                        )
                        Log.d("SyncServer", "Saving job to database...")
                        val id = withContext(Dispatchers.IO + NonCancellable) {
                            jobRepository.addJob(job)
                        }
                        Log.i("SyncServer", "Successfully added job with ID: $id")
                        systemLog.log("Sync: Job saved (ID: $id).")
                        
                        // Respond to client IMMEDIATELY to prevent browser/addon timeout
                        call.respond(HttpStatusCode.OK, SyncResponse(status = "success", id = id))

                        // Run AI auto-sweep and tagging asynchronously in background
                        if (modelManager.isModelDownloaded()) {
                            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                                try {
                                    systemLog.log("Sync: Auto-sweeping and tagging job in background...")
                                    
                                    val cleanPrompt = PromptBuilder.smartCleanPrompt(cleanedDesc)
                                    val cleaned = modelManager.generate(cleanPrompt, source = "Sync Auto-Sweep").trim()
                                    val finalDesc = if (cleaned.isNotBlank()) {
                                        trainingRepository.logExample("task", "auto_sweep_sync", cleanPrompt, cleaned)
                                        cleaned
                                    } else cleanedDesc

                                    val tagPrompt = PromptBuilder.taggingPrompt(sharedJob.title, sharedJob.description)
                                    val tags = modelManager.generate(tagPrompt, source = "Sync Auto-Tagging").trim()
                                    if (tags.isNotBlank()) {
                                        trainingRepository.logExample("task", "auto_tagging_sync", tagPrompt, tags)
                                    }

                                    jobRepository.getJob(id)?.let { savedJob ->
                                        jobRepository.updateJob(savedJob.copy(description = finalDesc, tags = tags))
                                    }
                                } catch (e: Exception) {
                                    Log.e("SyncServer", "Background AI Sweep/Tag failed", e)
                                }
                            }
                        }
                        
                        // Track last 5 jobs
                        _recentSyncs.value = (listOf(sharedJob.title.ifBlank { sharedJob.company }) + _recentSyncs.value).take(5)

                        try {
                            showJobReceivedNotification(sharedJob.title, sharedJob.company)
                        } catch (e: Exception) {
                            Log.e("SyncServer", "Failed to show notification, but job was added", e)
                        }
                    } catch (e: Exception) {
                        Log.e("SyncServer", "Critical error in /add-job", e)
                        try {
                            call.respond(HttpStatusCode.InternalServerError, SyncResponse(
                                status = "error", 
                                message = e.message ?: "Unknown error",
                                type = e.javaClass.simpleName
                            ))
                        } catch (respEx: Exception) {
                            // Response already sent or connection closed
                        }
                    }
                }
                get("/status") {
                    Log.i("SyncServer", "Status check received")
                    call.respond(mapOf("status" to "ok", "app" to "JobSearch"))
                }
            }
        }.start(wait = false)
        _isServerRunning.value = true
        Log.i("SyncServer", "Server started on port $port")
    }

    fun stopServer() {
        server?.stop(1000L, 2000L, TimeUnit.MILLISECONDS)
        server = null
        _isServerRunning.value = false
        Log.i("SyncServer", "Server stopped")
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
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
