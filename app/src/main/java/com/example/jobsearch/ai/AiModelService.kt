package com.example.jobsearch.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jobsearch.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive (via a foreground notification) while the on-device
 * AI model is loading or generating. Without this, Android can kill the
 * process - and with it the 4+ GB model - while the app is in the background,
 * which made the model reload and look "stuck" each time.
 */
@AndroidEntryPoint
class AiModelService : Service() {

    @Inject
    lateinit var modelManager: IModelManager

    @Inject
    lateinit var generationRepository: GenerationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var checker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI model", NotificationManager.IMPORTANCE_MIN)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        if (checker?.isActive != true) {
            checker = scope.launch {
                while (isActive) {
                    val busy = modelManager.isBusy() ||
                        generationRepository.state.value.running
                    if (!busy && modelManager.isEngineLoaded()) {
                        delay(IDLE_GRACE_MS)
                        if (!modelManager.isBusy() &&
                            !generationRepository.state.value.running
                        ) {
                            Log.i(TAG, "Model idle - stopping keep-alive service")
                            releaseWakeLock()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            return@launch
                        }
                    }
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        checker?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JobSearch:AiWakeLock")
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minute timeout
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
        wakeLock = null
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Job Search AI")
            .setContentText("Preparing the AI model...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        private const val TAG = "AiModelService"
        private const val CHANNEL_ID = "ai_model"
        private const val NOTIFICATION_ID = 1
        private const val CHECK_INTERVAL_MS = 2_000L
        private const val IDLE_GRACE_MS = 300_000L // 5 minutes

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AiModelService::class.java))
        }
    }
}
