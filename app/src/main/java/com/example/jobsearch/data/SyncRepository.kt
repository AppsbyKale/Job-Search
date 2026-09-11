package com.example.jobsearch.data

import com.example.jobsearch.network.SyncServer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
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
    private val syncServer: SyncServer
) {
    val isServerRunning: StateFlow<Boolean> get() = syncServer.isServerRunning
    val recentSyncs: StateFlow<List<String>> get() = syncServer.recentSyncs
    val currentPin: StateFlow<String> get() = syncServer.currentPin

    fun refreshPin(): String = syncServer.refreshPin()

    fun startServer(port: Int) {
        syncServer.start(port)
    }

    fun stopServer() {
        syncServer.stop()
    }

    fun getLocalIpAddress(): String? {
        return syncServer.getLocalIpAddress()
    }
}
