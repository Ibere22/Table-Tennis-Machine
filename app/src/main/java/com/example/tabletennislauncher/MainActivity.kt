package com.example.tabletennislauncher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tabletennislauncher.ui.theme.TableTennisLauncherTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WiFiManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize WiFi Manager
        wifiManager = WiFiManager(this)

        // Request permissions on app start
        requestPermissions()

        setContent {
            TableTennisLauncherTheme {
                TableTennisLauncherApp(wifiManager)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableTennisLauncherApp(wifiManager: WiFiManager) {
    var selectedMode by remember { mutableStateOf("") }
    var sessionActive by remember { mutableStateOf(false) }
    var ballsRemaining by remember { mutableStateOf(0) }
    var currentShot by remember { mutableStateOf(0) }
    var sessionTime by remember { mutableStateOf(0) }
    var speed by remember { mutableStateOf(50) }
    var direction by remember { mutableStateOf(90) }
    var verticalAngle by remember { mutableStateOf(90) }
    var ballCount by remember { mutableStateOf("20") }
    var throwInterval by remember { mutableStateOf("3") } // Interval in seconds
    var status by remember { mutableStateOf("Connect phone to ESP32 AP, then use buttons") }
    
    // Connection test function
    LaunchedEffect(Unit) {
        delay(1000) // Wait 1 second after app starts
        wifiManager.connectToESP32(
            onSuccess = {
                status = "Connected to ESP32! Ready to start training."
            },
            onError = { error ->
                status = "Connection failed: $error"
            }
        )
    }
    
    // Function to update session status from ESP32
    fun updateSessionStatusFromESP32() {
        wifiManager.getSessionStatus(
            onSuccess = { statusJson ->
                try {
                    val json = org.json.JSONObject(statusJson)
                    sessionActive = json.optBoolean("sessionActive", false)
                    ballsRemaining = json.optInt("ballsRemaining", 0)
                    currentShot = json.optInt("currentShot", 0)
                    sessionTime = json.optInt("sessionTime", 0)
                    
                    status = "Ball thrown! Shot $currentShot, $ballsRemaining remaining"
                } catch (e: Exception) {
                    status = "Error parsing status: ${e.message}"
                }
            },
            onError = { error ->
                status = "Error getting status: $error"
            }
        )
    }
    
    // Periodic status updates during active session
    LaunchedEffect(sessionActive) {
        if (sessionActive) {
            while (sessionActive) {
                delay(2000) // Update every 2 seconds
                updateSessionStatusFromESP32()
            }
        }
    }
    
    // Automatic ball throwing during active session
    LaunchedEffect(sessionActive) {
        if (sessionActive) {
            val intervalSeconds = throwInterval.toDoubleOrNull() ?: 3.0
            val intervalMillis = (intervalSeconds * 1000).toLong()
            
            while (sessionActive && ballsRemaining > 0) {
                delay(intervalMillis) // Wait for user-specified interval
                
                if (sessionActive && ballsRemaining > 0) {
                    // Throw ball automatically
                    wifiManager.throwBall(
                        onSuccess = {
                            updateSessionStatusFromESP32()
                        },
                        onError = { error ->
                            status = "Error throwing ball: $error"
                        }
                    )
                }
            }
        }
    }

    // Session summary variables
    var showSessionSummary by remember { mutableStateOf(false) }
    var sessionSummary by remember { mutableStateOf<SessionSummary?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Table Tennis Launcher",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Text
            Text(
                text = status,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )

            // Mode Selection Card
            ModeSelectionCard(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )

            // Speed Control Card (show for modes that need user speed control)
            if (selectedMode.isNotEmpty() && selectedMode != "HARDCORE" && selectedMode != "RANDOM") {
                SpeedControlCard(
                    speed = speed,
                    onSpeedChange = { speed = it }
                )
            }

            // Ball Count Card (show for all modes)
            BallCountCard(
                ballCount = ballCount,
                onBallCountChange = { ballCount = it }
            )
            
            // Throw Interval Card (show for all modes)
            ThrowIntervalCard(
                throwInterval = throwInterval,
                onThrowIntervalChange = { throwInterval = it }
            )

            // Manual Parameters Card (only show for Manual mode)
            if (selectedMode == "MANUAL") {
                ManualParametersCard(
                    direction = direction,
                    verticalAngle = verticalAngle,
                    onDirectionChange = { direction = it },
                    onVerticalAngleChange = { verticalAngle = it }
                )
            }

            // Session Control Card
            SessionControlCard(
                selectedMode = selectedMode,
                sessionActive = sessionActive,
                onStartSession = {
                    status = "Starting session..."
                    wifiManager.startSession(
                        mode = selectedMode,
                        speed = speed,
                        direction = direction,
                        verticalAngle = verticalAngle,
                        ballCount = ballCount.toIntOrNull() ?: 20,
                        onSuccess = {
                            sessionActive = true
                            showSessionSummary = false
                            sessionSummary = null
                            status = "Session started: $selectedMode mode"
                            
                            // Get initial status from ESP32
                            updateSessionStatusFromESP32()
                        },
                        onError = { error ->
                            status = "Error: $error"
                        }
                    )
                },
                onStopSession = {
                    status = "Stopping session..."
                    wifiManager.stopSession(
                        onSuccess = {
                            sessionActive = false
                            status = "Session stopped"
                            
                            // Get final status from ESP32 before showing summary
                            updateSessionStatusFromESP32()
                            
                            // Get session statistics with shot data
                            wifiManager.getStatistics(
                                onSuccess = { stats ->
                                    try {
                                        val json = org.json.JSONObject(stats)
                                        val shotsArray = json.optJSONArray("shots")
                                        val shotsList = mutableListOf<ShotData>()
                                        
                                        if (shotsArray != null) {
                                            for (i in 0 until shotsArray.length()) {
                                                val shotJson = shotsArray.getJSONObject(i)
                                                shotsList.add(ShotData(
                                                    shotNumber = shotJson.optInt("shotNumber"),
                                                    horizontalAngle = shotJson.optInt("horizontalAngle"),
                                                    verticalAngle = shotJson.optInt("verticalAngle"),
                                                    speed = shotJson.optInt("speed"),
                                                    timestamp = shotJson.optLong("timestamp")
                                                ))
                                            }
                                        }
                                        
                                        sessionSummary = SessionSummary(
                                            mode = selectedMode,
                                            totalBalls = json.optInt("totalBalls", ballCount.toIntOrNull() ?: 20),
                                            sessionDuration = json.optInt("sessionDuration", sessionTime),
                                            averageSpeed = json.optInt("averageSpeed", speed),
                                            ballsPerMinute = if (sessionTime > 0) {
                                                (ballCount.toIntOrNull() ?: 20) * 60 / sessionTime
                                            } else 0,
                                            shots = shotsList
                                        )
                                        showSessionSummary = true
                                    } catch (e: Exception) {
                                        // Fallback to local data if parsing fails
                                        sessionSummary = SessionSummary(
                                            mode = selectedMode,
                                            totalBalls = ballCount.toIntOrNull() ?: 20,
                                            sessionDuration = sessionTime,
                                            averageSpeed = speed,
                                            ballsPerMinute = if (sessionTime > 0) {
                                                (ballCount.toIntOrNull() ?: 20) * 60 / sessionTime
                                            } else 0
                                        )
                                        showSessionSummary = true
                                    }
                                },
                                onError = {
                                    // Fallback to local data if request fails
                                    sessionSummary = SessionSummary(
                                        mode = selectedMode,
                                        totalBalls = ballCount.toIntOrNull() ?: 20,
                                        sessionDuration = sessionTime,
                                        averageSpeed = speed,
                                        ballsPerMinute = if (sessionTime > 0) {
                                            (ballCount.toIntOrNull() ?: 20) * 60 / sessionTime
                                        } else 0
                                    )
                                    showSessionSummary = true
                                }
                            )
                        },
                        onError = { error ->
                            status = "Error stopping session: $error"
                        }
                    )
                }
            )

            // Session Info Card
            SessionInfoCard(
                sessionActive = sessionActive,
                ballsRemaining = ballsRemaining,
                currentShot = currentShot,
                sessionTime = sessionTime
            )

            // Session Summary Card (show after session ends)
            if (showSessionSummary && sessionSummary != null) {
                SessionSummaryCard(
                    sessionSummary = sessionSummary!!,
                    onDismiss = {
                        showSessionSummary = false
                        sessionSummary = null
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectionCard(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Training Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Mode selection buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeButton("MANUAL", selectedMode, onModeSelected, "Full control over all parameters")
                ModeButton("FOREHAND", selectedMode, onModeSelected, "Servo at 70° - choose speed and ball count")
                ModeButton("BACKHAND", selectedMode, onModeSelected, "Servo at 110° - choose speed and ball count")
                ModeButton("FOREHAND_BACKHAND", selectedMode, onModeSelected, "Alternates between 70° and 110°")
            }
        }
    }
}

@Composable
fun ModeButton(
    mode: String,
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    description: String
) {
    Button(
        onClick = { onModeSelected(mode) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selectedMode == mode) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = mode.replace("_", " "),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedMode == mode) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = if (selectedMode == mode) 
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedControlCard(
    speed: Int,
    onSpeedChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Speed Control",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Speed Slider
            Column {
                Text("Speed: ${speed}%")
                Slider(
                    value = speed.toFloat() / 100f,
                    onValueChange = { onSpeedChange((it * 100).toInt()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallCountCard(
    ballCount: String,
    onBallCountChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Ball Count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Ball Count Input
            Column {
                Text("Number of Balls:")
                OutlinedTextField(
                    value = ballCount,
                    onValueChange = { onBallCountChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter number of balls") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThrowIntervalCard(
    throwInterval: String,
    onThrowIntervalChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Throw Interval",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Throw Interval Input
            Column {
                Text("Interval between throws (seconds):")
                OutlinedTextField(
                    value = throwInterval,
                    onValueChange = { onThrowIntervalChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter interval in seconds (e.g., 3 or 0.5)") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualParametersCard(
    direction: Int,
    verticalAngle: Int,
    onDirectionChange: (Int) -> Unit,
    onVerticalAngleChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Manual Parameters",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Direction Slider (72° to 180°)
            Column {
                Text("Direction: ${direction}°")
                Slider(
                    value = (direction - 72).toFloat() / (180 - 72).toFloat(),
                    onValueChange = { onDirectionChange((it * (180 - 72)).toInt() + 72) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Vertical Angle Slider (25° to 140°)
            Column {
                Text("Vertical Angle: ${verticalAngle}°")
                Slider(
                    value = (verticalAngle - 25).toFloat() / (140 - 25).toFloat(),
                    onValueChange = { onVerticalAngleChange((it * (140 - 25)).toInt() + 25) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionControlCard(
    selectedMode: String,
    sessionActive: Boolean,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Session Control",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.weight(1f),
                    enabled = selectedMode.isNotEmpty() && !sessionActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Start Session")
                }

                Button(
                    onClick = onStopSession,
                    modifier = Modifier.weight(1f),
                    enabled = sessionActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Session")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionInfoCard(
    sessionActive: Boolean,
    ballsRemaining: Int,
    currentShot: Int,
    sessionTime: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Session Information",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (sessionActive) {
                Text("Status: Active")
                Text("Balls Remaining: $ballsRemaining")
                Text("Current Shot: $currentShot")
                Text("Session Time: ${sessionTime}s")
            } else {
                Text("Status: No active session")
            }
        }
    }
}

// Data class for session summary
data class SessionSummary(
    val mode: String,
    val totalBalls: Int,
    val sessionDuration: Int,
    val averageSpeed: Int,
    val ballsPerMinute: Int,
    val shots: List<ShotData> = emptyList()
)

// Data class for individual shot data
data class ShotData(
    val shotNumber: Int,
    val horizontalAngle: Int,
    val verticalAngle: Int,
    val speed: Int,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryCard(
    sessionSummary: SessionSummary,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session Summary",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Text("✕", fontSize = 18.sp)
                }
            }

            // Session statistics
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatRow("Mode", sessionSummary.mode)
                StatRow("Total Balls", "${sessionSummary.totalBalls}")
                StatRow("Session Duration", "${sessionSummary.sessionDuration}s")
                StatRow("Average Speed", "${sessionSummary.averageSpeed}%")
                StatRow("Balls per Minute", "${sessionSummary.ballsPerMinute}")
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}