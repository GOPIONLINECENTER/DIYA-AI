package com.example.core.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Encrypted HTTPS/WSS client for connecting the Android mobile app to the Cloud Run relay.
 * Never claims the Windows PC is connected unless the authenticated relay delivers a verified PC_STATUS frame.
 */
class SecureWssRelayClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Unconfigured)
    val connectionState: StateFlow<RelayConnectionState> = _connectionState.asStateFlow()

    private var activeSocket: WebSocket? = null
    private var currentRelayHost: String = ""

    suspend fun connect(httpsBaseUrl: String, mobileDeviceId: String, mobileDeviceName: String) {
        val cleanUrl = httpsBaseUrl.trim().trimEnd('/')
        if (cleanUrl.isEmpty()) {
            disconnect()
            _connectionState.value = RelayConnectionState.Unconfigured
            return
        }

        val parsed = try {
            URI(cleanUrl)
        } catch (_: Exception) {
            _connectionState.value = RelayConnectionState.Error("Invalid relay URL format.")
            return
        }

        if (parsed.scheme != "https" || parsed.host.isNullOrBlank()) {
            _connectionState.value = RelayConnectionState.Error("Encrypted HTTPS/WSS relay URL required (https://...).")
            return
        }

        val user = auth.currentUser
        if (user == null) {
            _connectionState.value = RelayConnectionState.Error("Sign in with Google before connecting to relay.")
            return
        }

        val idToken = try {
            user.getIdToken(false).await()?.token
        } catch (e: Exception) {
            _connectionState.value = RelayConnectionState.Error(e.localizedMessage ?: "Failed to obtain auth token.")
            return
        }

        if (idToken.isNullOrBlank()) {
            _connectionState.value = RelayConnectionState.Error("Missing Firebase ID token.")
            return
        }

        disconnect()
        currentRelayHost = parsed.host
        _connectionState.value = RelayConnectionState.Connecting

        val wssUrl = cleanUrl.replaceFirst("https://", "wss://") +
            "/ws/mobile?deviceId=$mobileDeviceId"

        val request = Request.Builder()
            .url(wssUrl)
            .addHeader("Authorization", "Bearer $idToken")
            .addHeader("X-Device-Name", mobileDeviceName.take(80))
            .build()

        activeSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = RelayConnectionState.AuthenticatedMobileOnly(currentRelayHost)
                val hello = JSONObject().apply {
                    put("type", "MOBILE_HELLO")
                    put("deviceId", mobileDeviceId)
                    put("deviceName", mobileDeviceName)
                }
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingFrame(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("SecureWssRelay", "WSS connection closed/failed: ${t.message}")
                _connectionState.value = RelayConnectionState.Error(
                    t.localizedMessage ?: "WSS relay unreachable (host: $currentRelayHost)"
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = RelayConnectionState.Disconnected
            }
        })
    }

    fun sendApprovedCommand(command: CommandRequestRecord): Boolean {
        val socket = activeSocket ?: return false
        if (!command.confirmed && command.confirmationRequired) return false
        val frame = JSONObject().apply {
            put("type", "COMMAND_DISPATCH")
            put("commandId", command.id)
            put("actionType", command.actionType)
            put("targetScope", command.targetScope)
            put("confirmed", command.confirmed)
        }
        return socket.send(frame.toString())
    }

    fun disconnect() {
        activeSocket?.close(1000, "Client disconnected")
        activeSocket = null
        if (_connectionState.value !is RelayConnectionState.Unconfigured) {
            _connectionState.value = RelayConnectionState.Disconnected
        }
    }

    private fun handleIncomingFrame(rawText: String) {
        try {
            val json = JSONObject(rawText)
            when (json.optString("type")) {
                "PC_STATUS" -> {
                    val pcOnline = json.optBoolean("pcOnline", false)
                    val pcName = json.optString("deviceName", "Windows Desktop")
                    _connectionState.value = if (pcOnline) {
                        RelayConnectionState.PairedAgentOnline(currentRelayHost, pcName)
                    } else {
                        RelayConnectionState.AuthenticatedMobileOnly(currentRelayHost)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SecureWssRelay", "Malformed relay frame ignored: ${e.message}")
        }
    }
}
