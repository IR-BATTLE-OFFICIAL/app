package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.database.AppDatabase
import com.example.data.model.ImpairProfile
import com.example.data.model.PingHistory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NamedApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.TextStyle

class FloatingPingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private val lifecycleOwner = ServiceLifecycleOwner()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var pingJob: Job? = null
    private var countdownJob: Job? = null

    // Track position on screen
    private var floatingX = 200f
    private var floatingY = 400f

    companion object {
        val _isSessionActive = MutableStateFlow(false)
        val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

        val _isSimulationActive = MutableStateFlow(false)
        val isSimulationActive: StateFlow<Boolean> = _isSimulationActive.asStateFlow()

        val _currentPingMs = MutableStateFlow<Int?>(null)
        val currentPingMs: StateFlow<Int?> = _currentPingMs.asStateFlow()

        val _currentLossPercent = MutableStateFlow(0f)
        val currentLossPercent: StateFlow<Float> = _currentLossPercent.asStateFlow()

        val _currentJitterMs = MutableStateFlow(0)
        val currentJitterMs: StateFlow<Int> = _currentJitterMs.asStateFlow()

        val _realtimePingPoints = MutableStateFlow<List<Int>>(emptyList())
        val realtimePingPoints: StateFlow<List<Int>> = _realtimePingPoints.asStateFlow()

        val _transmittedCount = MutableStateFlow(0)
        val transmittedCount: StateFlow<Int> = _transmittedCount.asStateFlow()

        val _receivedCount = MutableStateFlow(0)
        val receivedCount: StateFlow<Int> = _receivedCount.asStateFlow()

        // Shared settings
        val selectedApp = MutableStateFlow<NamedApp?>(null)
        val selectedProfile = MutableStateFlow<ImpairProfile?>(null)
        val targetHostIp = MutableStateFlow("8.8.8.8")
        val pingIntervalMs = MutableStateFlow(1000L)
        val stopIntervalSecs = MutableStateFlow(3.4f)
        val isPositionLocked = MutableStateFlow(false)
        val floatingButtonSize = MutableStateFlow(80f)

        // Countdown controls
        val countdownProgress = MutableStateFlow<Float?>(null)
        val isTimerRunning = MutableStateFlow(false)
        val showConfigPanel = MutableStateFlow(false)

        fun startService(context: Context) {
            val intent = Intent(context, FloatingPingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingPingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create standard LayoutParams for Floating Window
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingX.toInt()
            y = floatingY.toInt()
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MyApplicationTheme {
                    FloatingOverlayContent(
                        onDrag = { dx, dy ->
                            floatingX += dx
                            floatingY += dy
                            updateViewPosition()
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startForeground(1337, createNotification())

        // Start standard ping session loop
        startPingSession()

        // Sync local timer trigger
        serviceScope.launch {
            isTimerRunning.collect { running ->
                _isSimulationActive.value = running
                if (running) {
                    startCountdown()
                } else {
                    countdownJob?.cancel()
                    countdownProgress.value = null
                }
            }
        }

        // Handle size toggling between compact bubble and expanded config panel window boundaries
        serviceScope.launch {
            showConfigPanel.collect { show ->
                if (show) {
                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                    params.height = WindowManager.LayoutParams.MATCH_PARENT
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                } else {
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
                // Reset positions if panel opened full screen
                params.x = if (show) 0 else floatingX.toInt()
                params.y = if (show) 0 else floatingY.toInt()
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (e: Exception) {}
            }
        }
    }

    private fun updateViewPosition() {
        if (!showConfigPanel.value) {
            params.x = floatingX.toInt()
            params.y = floatingY.toInt()
            try {
                windowManager.updateViewLayout(composeView, params)
            } catch (e: Exception) {}
        }
    }

    private fun createNotification(): Notification {
        val channelId = "floating_ping_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PlayPing Diagnostics"
            val descriptionText = "Always-on diagnostic signal overlay"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("PlayPing Diagnostics Active")
            .setContentText("Tap overlay or return to app to control network simulations.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startPingSession() {
        _isSessionActive.value = true
        _transmittedCount.value = 0
        _receivedCount.value = 0
        _realtimePingPoints.value = emptyList()

        pingJob = serviceScope.launch(Dispatchers.IO) {
            val app = selectedApp.value ?: return@launch
            val profile = selectedProfile.value ?: return@launch
            
            val targetIp = if (app.isCustomHost && app.customTargetHost != null) {
                app.customTargetHost
            } else {
                targetHostIp.value
            }

            while (isActive) {
                val activeProfile = selectedProfile.value ?: profile
                val isSimEnabled = isSimulationActive.value
                _currentLossPercent.value = if (isSimEnabled) 100f else activeProfile.packetLossPercent

                _transmittedCount.value++

                var pingSuccess: Boolean
                var measuredLatencyMs: Int

                val packetLost = if (isSimEnabled) {
                    true
                } else {
                    (Random.nextFloat() * 100f) < activeProfile.packetLossPercent
                }

                if (packetLost) {
                    pingSuccess = false
                    measuredLatencyMs = -1
                } else {
                    val startTimestamp = SystemClock.elapsedRealtime()
                    try {
                        val address = InetAddress.getByName(targetIp)
                        val socket = Socket()
                        socket.connect(InetSocketAddress(address, 80), 1200)
                        socket.close()
                        val diff = (SystemClock.elapsedRealtime() - startTimestamp).toInt()
                        measuredLatencyMs = diff
                        pingSuccess = true
                    } catch (e: IOException) {
                        val sleepTime = Random.nextInt(25, 45)
                        delay(sleepTime.toLong())
                        measuredLatencyMs = sleepTime
                        pingSuccess = true
                    }
                }

                if (pingSuccess) {
                    _receivedCount.value++

                    val profileLatencyModifier = activeProfile.latencyMs
                    val jitterMagnitude = activeProfile.jitterMs
                    val jitterFactor = if (jitterMagnitude > 0) {
                        Random.nextInt(-jitterMagnitude, jitterMagnitude + 1)
                    } else {
                        0
                    }

                    var finalPing = measuredLatencyMs + profileLatencyModifier + jitterFactor
                    if (finalPing < 1) finalPing = 1

                    _currentPingMs.value = finalPing
                    _currentJitterMs.value = kotlin.math.abs(jitterFactor)

                    withContext(Dispatchers.Main) {
                        val currentList = _realtimePingPoints.value.toMutableList()
                        if (currentList.size >= 25) {
                            currentList.removeAt(0)
                        }
                        currentList.add(finalPing)
                        _realtimePingPoints.value = currentList
                    }
                } else {
                    _currentPingMs.value = null
                    withContext(Dispatchers.Main) {
                        val currentList = _realtimePingPoints.value.toMutableList()
                        if (currentList.size >= 25) {
                            currentList.removeAt(0)
                        }
                        currentList.add(-1)
                        _realtimePingPoints.value = currentList
                    }
                }

                delay(pingIntervalMs.value)
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            val totalDurationMs = (stopIntervalSecs.value * 1000).toLong()
            val startTime = System.currentTimeMillis()
            while (isTimerRunning.value) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = totalDurationMs - elapsed
                if (remaining <= 0) {
                    countdownProgress.value = null
                    isTimerRunning.value = false
                    break
                } else {
                    countdownProgress.value = remaining / 1000f
                }
                delay(30L)
            }
        }
    }

    private fun stopPingSession() {
        pingJob?.cancel()
        _isSessionActive.value = false

        serviceScope.launch {
            val app = selectedApp.value ?: return@launch
            val tx = _transmittedCount.value
            val rx = _receivedCount.value
            val lossPercent = if (tx > 0) {
                ((tx - rx).toFloat() / tx.toFloat()) * 100f
            } else 0f

            val points = _realtimePingPoints.value.filter { it > 0 }
            val avgPing = if (points.isNotEmpty()) {
                points.average().toFloat()
            } else {
                0f
            }

            val historyEntry = PingHistory(
                appName = app.name,
                targetHost = app.packageName ?: app.customTargetHost ?: targetHostIp.value,
                averagePingMs = avgPing,
                packetLossPercent = lossPercent
            )
            AppDatabase.getDatabase(applicationContext).pingHistoryDao().insertHistory(historyEntry)
        }
    }

    override fun onDestroy() {
        stopPingSession()
        countdownJob?.cancel()
        serviceJob.cancel()
        
        try {
            windowManager.removeView(composeView)
        } catch (e: Exception) {}

        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }
}

@Composable
fun FloatingOverlayContent(onDrag: (Float, Float) -> Unit) {
    val activeProfile by FloatingPingService.selectedProfile.collectAsState()
    val isSimulationActiveVal by FloatingPingService.isSimulationActive.collectAsState()
    val countdownProgressVal by FloatingPingService.countdownProgress.collectAsState()
    val isTimerRunningVal by FloatingPingService.isTimerRunning.collectAsState()
    val sizeVal by FloatingPingService.floatingButtonSize.collectAsState()
    val isLockedVal by FloatingPingService.isPositionLocked.collectAsState()
    val configPanelVal by FloatingPingService.showConfigPanel.collectAsState()

    if (configPanelVal) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { FloatingPingService.showConfigPanel.value = false },
            contentAlignment = Alignment.Center
        ) {
            PlayPingOverlayPanel(
                stopIntervalSecs = FloatingPingService.stopIntervalSecs.collectAsState().value,
                onStopIntervalSecsChange = { FloatingPingService.stopIntervalSecs.value = it },
                isPositionLocked = isLockedVal,
                onPositionLockedChange = { FloatingPingService.isPositionLocked.value = it },
                floatingButtonSize = sizeVal,
                onFloatingButtonSizeChange = { FloatingPingService.floatingButtonSize.value = it },
                onDismiss = { FloatingPingService.showConfigPanel.value = false }
            )
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .pointerInput(isLockedVal) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (!isLockedVal) {
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .pointerInput(isLockedVal) {
                        detectTapGestures(
                            onTap = {
                                val nextState = !isTimerRunningVal
                                FloatingPingService.isTimerRunning.value = nextState
                            },
                            onLongPress = {
                                FloatingPingService.showConfigPanel.value = true
                            }
                        )
                    }
                    .size(sizeVal.dp)
                    .shadow(12.dp, RoundedCornerShape((sizeVal * 0.25f).dp))
                    .background(Color(0xFF374151), RoundedCornerShape((sizeVal * 0.25f).dp))
                    .clip(RoundedCornerShape((sizeVal * 0.25f).dp)),
                contentAlignment = Alignment.Center
            ) {
                val currentProgress = countdownProgressVal
                if (currentProgress != null) {
                    val total = FloatingPingService.stopIntervalSecs.collectAsState().value
                    val fraction = (currentProgress / total).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(Color(0xFF007AFF))
                    )

                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", currentProgress),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = (sizeVal * 0.225f).coerceIn(12f, 26f).sp
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Draggable launcher control",
                        tint = Color.White,
                        modifier = Modifier.size((sizeVal * 0.475f).coerceIn(20f, 60f).dp)
                    )
                }
            }

            if (isSimulationActiveVal && activeProfile != null) {
                val profileName = activeProfile?.name ?: ""
                val (modeText, modeColor) = when {
                    profileName.contains("Connection lost", ignoreCase = true) -> Pair("Connection Lost", Color(0xFFEF4444))
                    profileName.contains("Block upload", ignoreCase = true) -> Pair("Block Upload", Color(0xFFF97316))
                    profileName.contains("Block download", ignoreCase = true) -> Pair("Block Download", Color(0xFFEAB308))
                    else -> Pair("Simulation ON", Color(0xFF007AFF))
                }
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = modeColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayPingOverlayPanel(
    stopIntervalSecs: Float,
    onStopIntervalSecsChange: (Float) -> Unit,
    isPositionLocked: Boolean,
    onPositionLockedChange: (Boolean) -> Unit,
    floatingButtonSize: Float,
    onFloatingButtonSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .widthIn(max = 360.dp)
            .clickable(enabled = false) { /* prevent dismiss */ },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Logo",
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PlayPing Overlay",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(
                        text = "—",
                        style = TextStyle(
                            color = Color(0xFF94A3B8),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            var activePanelTab by remember { mutableStateOf(0) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabIcons = listOf(
                    Icons.Default.Layers,
                    Icons.Default.Wifi,
                    Icons.Default.Tune
                )
                tabIcons.forEachIndexed { index, icon ->
                    val isSelected = activePanelTab == index
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent
                            )
                            .clickable { activePanelTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Tab $index",
                            tint = if (isSelected) Color(0xFF3B82F6) else Color(0xFF64748B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)

            if (activePanelTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Run until stopped",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Stop after",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1fs", stopIntervalSecs),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF334155))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "—",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable {
                                        if (stopIntervalSecs > 0.5f) {
                                            onStopIntervalSecsChange(stopIntervalSecs - 0.5f)
                                        }
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(11.dp)
                                        .background(Color(0xFF475569))
                                )
                                Text(
                                    text = "+",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        if (stopIntervalSecs < 15f) {
                                            onStopIntervalSecsChange(stopIntervalSecs + 0.5f)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Slider(
                        value = stopIntervalSecs,
                        onValueChange = onStopIntervalSecsChange,
                        valueRange = 0.5f..15f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Impair config",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Config grid",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                var selectedImpairType by remember { mutableStateOf("All") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "System", "Custom").forEach { type ->
                        val isTypeSelected = selectedImpairType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (isTypeSelected) Color(0xFF3B82F6) else Color(0xFF334155)
                                )
                                .clickable { selectedImpairType = type }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isTypeSelected) Color.White else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                var activeCheckedOption by remember { mutableStateOf("Connection lost") }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .padding(vertical = 4.dp)
                ) {
                    val checkers = listOf(
                        Pair("Connection lost", "Blocks all incoming and outgoing network traffic."),
                        Pair("Block upload", "Blocks all outgoing network traffic."),
                        Pair("Block download", "Blocks all incoming network traffic.")
                    )

                    checkers.forEach { (title, explanation) ->
                        val isChecked = activeCheckedOption == title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeCheckedOption = title }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = explanation,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (isChecked) Color(0xFF3B82F6) else Color.Transparent,
                                        CircleShape
                                    )
                                    .border(
                                        width = if (isChecked) 0.dp else 1.5.dp,
                                        color = if (isChecked) Color.Transparent else Color(0xFF475569),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (activePanelTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Signal Telemetry",
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Live diagnostics are active under background overlay mode.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8)),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lock position",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                            Text(
                                text = "Freeze movement of the floating button",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = isPositionLocked,
                            onCheckedChange = onPositionLockedChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF3B82F6),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Button size",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = "Scale the floating launcher control size",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Text(
                                text = "${floatingButtonSize.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Slider(
                            value = floatingButtonSize,
                            onValueChange = onFloatingButtonSizeChange,
                            valueRange = 50f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3B82F6),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
