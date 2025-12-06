package com.example.tabletennislauncher

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class WiFiManager(private val context: Context) {

    companion object {
        private const val TAG = "WiFiManager"
        private const val ESP32_IP = "192.168.4.1" // ESP32 Access Point IP
        private const val ESP32_PORT = 80
        private const val ESP32_BASE_URL = "http://$ESP32_IP:$ESP32_PORT"
        private const val WEBSOCKET_URL = "ws://$ESP32_IP:81"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var onDataReceived: ((String) -> Unit)? = null

    // WiFi Manager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun connectToESP32(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var retryCount = 0
            val maxRetries = 3
            
            while (retryCount < maxRetries) {
                try {
                    Log.d(TAG, "Attempting to connect to ESP32 (attempt ${retryCount + 1}/$maxRetries)")
                    
                    // Check if we can reach ESP32
                    val response = client.newCall(
                        Request.Builder()
                            .url("$ESP32_BASE_URL/status")
                            .build()
                    ).execute()

                    if (response.isSuccessful) {
                        isConnected = true
                        setupWebSocket()
                        Log.d(TAG, "Successfully connected to ESP32")
                        withContext(Dispatchers.Main) {
                            onSuccess()
                        }
                        return@launch
                    } else {
                        Log.w(TAG, "ESP32 responded with status: ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error (attempt ${retryCount + 1}): ${e.message}")
                }
                
                retryCount++
                if (retryCount < maxRetries) {
                    delay(2000) // Wait 2 seconds before retry
                }
            }
            
            withContext(Dispatchers.Main) {
                onError("Failed to connect to ESP32 after $maxRetries attempts. Make sure ESP32 is running and phone is connected to 'TableTennisLauncher' WiFi.")
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected

    private fun setupWebSocket() {
        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                onDataReceived?.invoke(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
            }
        })
    }

    fun setOnDataReceived(callback: (String) -> Unit) {
        onDataReceived = callback
    }

    // Send commands to ESP32
    fun startSession(
        mode: String,
        speed: Int = 50,
        direction: Int = 180,
        verticalAngle: Int = 90,
        ballCount: Int = 20,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("mode", mode)
                    put("speed", speed)
                    put("direction", direction)
                    put("verticalAngle", verticalAngle)
                    put("ballCount", ballCount)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$ESP32_BASE_URL/start-session")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to start session")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start session error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    fun stopSession(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$ESP32_BASE_URL/stop-session")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to stop session")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stop session error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    fun throwBall(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$ESP32_BASE_URL/throw-ball")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to throw ball")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Throw ball error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    fun getSessionStatus(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$ESP32_BASE_URL/status")
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val status = response.body?.string() ?: ""
                    withContext(Dispatchers.Main) {
                        onSuccess(status)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to get status")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Get status error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    fun getStatistics(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$ESP32_BASE_URL/statistics")
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val stats = response.body?.string() ?: ""
                    withContext(Dispatchers.Main) {
                        onSuccess(stats)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to get statistics")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Get statistics error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            }
        }
    }
} 