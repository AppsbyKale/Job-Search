package com.example.jobsearch.data

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.net.NetworkCapabilities
import android.util.Log
import com.example.jobsearch.R
import com.example.jobsearch.ai.IModelManager
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
import java.net.Inet4Address
import java.net.NetworkInterface
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

@Singleton
class SyncRepository @Inject constructor(
    private val context: Context,
    private val jobRepository: JobRepository,
    private val trainingRepository: TrainingRepository,
    private val modelManager: IModelManager,
    private val parser: JobParser,
    private val systemLog: SystemLogRepository
) {
    private var server: ApplicationEngine? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _recentSyncs = MutableStateFlow<List<String>>(emptyList())
    val recentSyncs: StateFlow<List<String>> = _recentSyncs.asStateFlow()

    fun startServer(port: Int) {
        if (server != null) return

        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
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
                post("/add-job") {
                    try {
                        val sharedJob = call.receive<SharedJob>()
                        Log.i("SyncServer", "Received job request: ${sharedJob.title}")
                        systemLog.log("Sync: Received job '${sharedJob.title}' from desktop.")
                        
                        val job = Job(
                            title = sharedJob.title,
                            company = sharedJob.company,
                            url = sharedJob.url,
                            description = try { 
                                val raw = parser.trimFluff(sharedJob.description)
                                if (modelManager.isModelDownloaded()) {
                                    systemLog.log("Sync: Auto-sweeping job description...")
                                    val prompt = com.example.jobsearch.ai.PromptBuilder.smartCleanPrompt(raw)
                                    val cleaned = modelManager.generate(prompt, source = "Sync Auto-Sweep").trim()
                                    if (cleaned.isNotBlank()) {
                                        trainingRepository.logExample("task", "auto_sweep_sync", prompt, cleaned)
                                        cleaned
                                    } else raw
                                } else raw
                            } catch(e: Exception) { 
                                Log.e("SyncServer", "Auto-sweep failed, using raw description", e)
                                sharedJob.description 
                            },
                            dateAdded = System.currentTimeMillis(),
                            status = JobStatus.SYNCED.name,
                            notes = sharedJob.notes ?: ""
                        )
                        Log.d("SyncServer", "Saving job to database...")
                        val id = try {
                            withContext(Dispatchers.IO + NonCancellable) {
                                jobRepository.addJob(job)
                            }
                        } catch (e: Exception) {
                            Log.e("SyncServer", "Failed to save job: ${e.message}")
                            throw e
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
                        call.respond(HttpStatusCode.OK, SyncResponse(status = "success", id = id))
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
