package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ImpairProfile
import com.example.data.model.PingHistory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NamedApp
import com.example.ui.viewmodel.PlayPingViewModel
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PlayPingMainScreen()
            }
        }
    }
}

@Composable
fun PlayPingMainScreen(
    viewModel: PlayPingViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val isSessionActive by viewModel.isSessionActive.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedApp.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    
    var showAppPicker by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showConfigPanel by remember { mutableStateOf(false) }

    // Floating Button lock and size adjustments
    var isPositionLocked by remember { mutableStateOf(false) }
    var floatingButtonSize by remember { mutableStateOf(80f) }

    // Countdown Timer control state
    var stopIntervalSecs by remember { mutableStateOf(3.4f) }
    var countdownProgress by remember { mutableStateOf<Float?>(null) }
    var isTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning) {
        viewModel.setSimulationActive(isTimerRunning)
    }

    LaunchedEffect(isSessionActive) {
        if (!isSessionActive) {
            isTimerRunning = false
        }
    }

    LaunchedEffect(isTimerRunning, stopIntervalSecs) {
        if (isTimerRunning) {
            val totalDurationMs = (stopIntervalSecs * 1000).toLong()
            val startTime = System.currentTimeMillis()
            while (isTimerRunning) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = totalDurationMs - elapsed
                if (remaining <= 0) {
                    countdownProgress = null
                    isTimerRunning = false
                    break
                } else {
                    countdownProgress = remaining / 1000f
                }
                delay(30L)
            }
        } else {
            countdownProgress = null
        }
    }

    // Remembering draggable button position offsets
    var floatingX by remember { mutableStateOf(80f) }
    var floatingY by remember { mutableStateOf(240f) }

    Scaffold(
        bottomBar = {
            if (!showAppPicker) {
                PlayPingBottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        },
        containerColor = Color(0xFFFAFBFC)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showAppPicker) PaddingValues(0.dp) else paddingValues)
        ) {
            // Grid background strictly replicating the reference layout grid lines
            if (!showAppPicker) {
                GridBackgroundOverlay()
            }

            // Simulated Top HUD Alert Android Notification has been removed as requested

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    "home" -> HomeTabScreen(
                        viewModel = viewModel,
                        isPremium = isPremium,
                        onUpgradeClick = { showUpgradeDialog = true },
                        showAppPicker = showAppPicker,
                        onShowAppPickerChange = { showAppPicker = it }
                    )
                    "impairs" -> ImpairsTabScreen(
                        viewModel = viewModel
                    )
                    "settings" -> SettingsTabScreen(
                        viewModel = viewModel,
                        isPremium = isPremium,
                        onUpgradeClick = { showUpgradeDialog = true }
                    )
                }
            }


        }
    }

    if (showUpgradeDialog) {
        PremiumUpgradeDialog(
            isPremium = isPremium,
            onDismiss = { showUpgradeDialog = false },
            onUpgradeSuccess = {
                viewModel.setPremium(true)
                showUpgradeDialog = false
            }
        )
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
    return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 360.dp)
                .clickable(enabled = false) { /* Prevent dismissing on panel clicks */ },
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
                // Header (App Title & Minimize/Close handle)
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
                        text = "PlayPing",
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

                // Three Option tabs (Layered cards, Radar calibration scan, tune adjusters icon)
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
                    // TAB 0: Interactive Simulation Config (Matches screenshot exactly!)
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
                                    text = String.format("%.1fs", stopIntervalSecs),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )

                                // Custom Minus/Divider/Plus Capsule Button
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

                        // Blue track slider
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

                    // Impair config subtitle
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

                    // Selectable Tag Pill Controls
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

                    // Network checklist box column
                    var activeCheckedOption by remember { mutableStateOf("Connection lost") }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A))
                            .padding(vertical = 4.dp)
                    ) {
                        val checkers = listOf(
                            Pair("Connection lost", "Blocks all incoming and outgoing network traffic. No network data can be transmitted."),
                            Pair("Block upload", "Blocks all outgoing network traffic. The device cannot upload or send data to the network."),
                            Pair("Block download", "Blocks all incoming network traffic. The device cannot download or receive data from the network.")
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
                                // Green status LED dot
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

                                // Custom blue checkbox with tick circle
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
                            text = "Live diagnostic graphs and package status details are streamed dynamically.",
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
                        // Lock position control
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

                        // Button size control
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
}

@Composable
fun GridBackgroundOverlay() {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val gridSpacing = 36.dp.toPx()
        val paintColor = Color(0xFFE5E7EB).copy(alpha = 0.7f)
        val strokeWidthValue = 0.5.dp.toPx()

        var y = 0f
        while (y < size.height) {
            drawLine(
                color = paintColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidthValue
            )
            y += gridSpacing
        }

        var x = 0f
        while (x < size.width) {
            drawLine(
                color = paintColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidthValue
            )
            x += gridSpacing
        }
    }
}

@Composable
fun HomeTabScreen(
    viewModel: PlayPingViewModel,
    isPremium: Boolean,
    onUpgradeClick: () -> Unit,
    showAppPicker: Boolean,
    onShowAppPickerChange: (Boolean) -> Unit
) {
    val selectedApp by viewModel.selectedApp.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val isSessionActive by viewModel.isSessionActive.collectAsStateWithLifecycle()
    val currentPingMs by viewModel.currentPingMs.collectAsStateWithLifecycle()
    val currentLossPercent by viewModel.currentLossPercent.collectAsStateWithLifecycle()
    val currentJitterMs by viewModel.currentJitterMs.collectAsStateWithLifecycle()
    val appsList by viewModel.appsList.collectAsStateWithLifecycle()
    val isSimulationActive by viewModel.isSimulationActive.collectAsStateWithLifecycle()

    var dropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // TOP LOGO BAR
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogoIcon(modifier = Modifier.size(32.dp))
                    Text(
                        text = "PlayPing",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = Color(0xFF111827)
                        )
                    )
                }

                // Premium subscription crown matching mockup banner
                Button(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF111827)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("upgrade_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "👑",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Upgrade",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                        )
                    }
                }
            }
        }

        // REAL-TIME WARNING BANNER CARD
        if (isSimulationActive && selectedProfile != null) {
            item {
                val profileName = selectedProfile?.name ?: ""
                val isConnectionLost = profileName.contains("Connection lost", ignoreCase = true)
                val isBlockUpload = profileName.contains("Block upload", ignoreCase = true)
                val isBlockDownload = profileName.contains("Block download", ignoreCase = true)

                val color = if (isConnectionLost) Color(0xFFEF4444) else if (isBlockUpload) Color(0xFFF97316) else if (isBlockDownload) Color(0xFFEAB308) else Color(0xFF3B82F6)
                val titleText = if (isConnectionLost) "Connection Lost Active" else if (isBlockUpload) "Upload Blocked" else if (isBlockDownload) "Download Blocked" else "${selectedProfile?.name} Mode Active"
                val descText = if (isConnectionLost) "Poore internet connection ke khatam hone ka simulation. All packets are dropped." else if (isBlockUpload) "Sirf data bhejna band karo. Outgoing requests are dropped and won't reach servers." else if (isBlockDownload) "Sirf data receive karna band karo. Server replies fail to load on incoming channel." else "Simulation active on the network channels."
                val bgGradColor = if (isConnectionLost) Color(0xFFFEF2F2) else if (isBlockUpload) Color(0xFFFFF7ED) else if (isBlockDownload) Color(0xFFFEFCE8) else Color(0xFFEFF6FF)

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgGradColor),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConnectionLost) Icons.Default.WifiOff else if (isBlockUpload) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = "Active Filter warning icon",
                                tint = color,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = descText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF374151),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // SONAR RADAR CONTROLLER GRAPHICS BLOCK
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center
            ) {
                RadarSonarPulseView(
                    isAnimating = isSessionActive,
                    currentPing = currentPingMs,
                    selectedApp = selectedApp
                )
            }
        }

        // PROFILE DROPDOWN SELECTOR
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("connection_dropdown"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Connection profiles list",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = selectedProfile?.name ?: "Basic connection",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.Black
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Open selections",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = profile.name, fontWeight = FontWeight.Medium)
                                        if (profile.isPreset) {
                                            Badge(containerColor = Color(0xFFF3F4F6)) {
                                                Text("System", color = Color(0xFF4B5563), fontSize = 10.sp)
                                            }
                                        } else {
                                            Badge(containerColor = Color(0xFFECFDF5)) {
                                                Text("Custom", color = Color(0xFF047857), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectProfile(profile)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // MAIN DIALOG / CARD WITH "+" AND "SELECT APP"
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("launch_app_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                if (isSessionActive && selectedApp != null) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title row: "Selected app" and edit icon on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selected app",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    fontSize = 15.sp
                                )
                            )
                            IconButton(
                                onClick = { onShowAppPickerChange(true) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit selection",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // App Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconContainer(app = selectedApp!!, size = 32.dp)
                            Text(
                                text = selectedApp!!.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black,
                                    fontSize = 16.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    // Deselects app or terminates session
                                    viewModel.togglePingSession()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove selected app",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // Disconnect Button styled with containerColor = Color(0xFF64748B) (Slate gray)
                        Button(
                            onClick = { viewModel.togglePingSession() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("disconnect_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF64748B),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "Disconnect",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        // Subtitle: "Floating control panel"
                        Text(
                            text = "Floating control panel",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "Floating button 1" Outlined Chip
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Floating button 1",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            // "Add floating button" Chip with gift icon
                            Row(
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .background(Color.Transparent, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = "Bonus",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Add floating button",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                )
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Regular standard launcher column
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (!isSessionActive) {
                                        onShowAppPickerChange(true)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val currentSelectedApp = selectedApp
                            if (currentSelectedApp != null) {
                                AppIconContainer(app = currentSelectedApp)
                            } else {
                                // Raw black thin outlined circle with a plus sign inside
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .border(1.5.dp, Color(0xFF1F2937), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add symbol",
                                        tint = Color(0xFF1F2937),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = selectedApp?.name ?: "Select app",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                    fontSize = 18.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (selectedApp != null) "Ready to monitor ${selectedApp?.name}" else "Simulate network conditions for the selected app",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        val context = LocalContext.current
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                    android.widget.Toast.makeText(context, "Please enable Draw over other apps permission to display the floating ping widget in the background.", android.widget.Toast.LENGTH_LONG).show()
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    viewModel.togglePingSession()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("launch_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSessionActive) Color(0xFFEF4444) else Color(0xFF007AFF),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSessionActive) Icons.Default.StopCircle else Icons.Default.RocketLaunch,
                                    contentDescription = "Session actions",
                                    modifier = Modifier.size(20.dp).rotate(if (isSessionActive) 0f else -15f)
                                )
                                Text(
                                    text = if (isSessionActive) "Stop Diagnostics" else "Launch",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // TELEMETRY ACTIVE COMPONENT SCREEN
        if (isSessionActive) {
            item {
                ActiveTelemetryCard(
                    loss = currentLossPercent,
                    jitter = currentJitterMs,
                    tx = viewModel.transmittedCount.collectAsStateWithLifecycle().value,
                    rx = viewModel.receivedCount.collectAsStateWithLifecycle().value,
                    pointsList = viewModel.realtimePingPoints.collectAsStateWithLifecycle().value
                )
            }
        }

        // UTILITIES ZONE
        item {
            Text(
                text = "Utilities",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UtilityGridItem(
                    title = "DNS Lookup",
                    description = "Resolve and audit DNS records",
                    icon = Icons.Default.Dns,
                    colorAccent = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.selectTab("settings")
                }

                UtilityGridItem(
                    title = "Port Scanner",
                    description = "Check target firewalls",
                    icon = Icons.Default.OfflineShare,
                    colorAccent = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.selectTab("settings")
                }
            }
        }
    }

    if (showAppPicker) {
        TargetAppSelectionScreen(
            apps = appsList,
            selectedApp = selectedApp,
            onDismiss = { onShowAppPickerChange(false) },
            onAppSelected = {
                viewModel.selectApp(it)
                onShowAppPickerChange(false)
            }
        )
    }
}

@Composable
fun LogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color(0xFF007AFF)
        val strokeWidthPx = 3.dp.toPx()
        
        // Left rounded vertical bar (the anchor of waves)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, size.height * 0.35f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.08f, size.height * 0.3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        
        // Arc 1 (inner wave)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidthPx,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            ),
            topLeft = Offset(size.width * 0.12f, size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.45f, size.height * 0.6f)
        )
        
        // Arc 2 (middle wave)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidthPx,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            ),
            topLeft = Offset(size.width * -0.05f, size.height * 0.05f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.75f, size.height * 0.9f)
        )
    }
}

@Composable
fun RadarSonarPulseView(
    isAnimating: Boolean,
    currentPing: Int?,
    selectedApp: NamedApp? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    
    val pulseAlpha1 by if (isAnimating) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha1"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val pulseScale1 by if (isAnimating) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale1"
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Transparent radiating pulse wave when simulation is active
        if (isAnimating) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        color = (if (selectedApp != null) Color(0xFF10B981) else Color(0xFF007AFF)).copy(alpha = pulseAlpha1),
                        shape = CircleShape
                    )
            )
        }

        if (isAnimating && selectedApp != null) {
            // ACTIVE GREEN SONAR RADAR VIEW (MATCHES SECOND SCREENSHOT)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF86EFAC).copy(alpha = 0.4f),
                                Color(0xFFA7F3D0).copy(alpha = 0.2f),
                                Color(0xFFD1FAE5).copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, Color(0xFF22C55E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Sweep indicator line from center to top
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = center,
                        end = Offset(size.width / 2f, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Green dot at the absolute center
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color(0xFF10B981), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )

                // Miniature App Icon placed perfectly at the top edge of the circle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-11).dp) // Exactly center-aligned on the 140.dp circle outline
                        .size(24.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconContainer(app = selectedApp, size = 16.dp)
                }
            }
        } else {
            // ORIGINAL BLUE / STYLED / IDLE SPHERE VIEW (MATCHES FIRST SCREENSHOT)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE2E8F0).copy(alpha = 0.9f),
                                Color(0xFFE2E8F0).copy(alpha = 0.6f),
                                Color(0xFFCBD5E1).copy(alpha = 0.4f),
                                Color(0xFF94A3B8).copy(alpha = 0.05f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner Core structure
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color.White.copy(alpha = 0.4f), CircleShape)
                        .border(0.5.dp, Color(0xFFE2E8F0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inside content
                    if (isAnimating) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (currentPing != null) {
                                Text(
                                    text = "$currentPing",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = when {
                                            currentPing < 55 -> Color(0xFF10B981)
                                            currentPing < 140 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                )
                                Text(
                                    text = "ms",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9CA3AF)
                                    )
                                )
                            } else {
                                Text(
                                    text = "DROP",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFEF4444)
                                    )
                                )
                            }
                        }
                    } else {
                        // EXACT IDLE STATE: Solid white center pill with slate grey core dot in the absolute center
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF94A3B8), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveTelemetryCard(
    loss: Float,
    jitter: Int,
    tx: Int,
    rx: Int,
    pointsList: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Live diagnostic tracking",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryStatColumn(label = "Packet Loss", value = "${String.format("%.1f", loss)}%", sub = "Drops rate")
                TelemetryStatColumn(label = "Jitter Rate", value = "$jitter ms", sub = "Fluctuation")
                TelemetryStatColumn(label = "TX / RX", value = "$tx / $rx", sub = "Transport logs")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Live latency response profile:",
                style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF64748B)),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LatencyRealtimeChart(points = pointsList)
        }
    }
}

@Composable
fun TelemetryStatColumn(label: String, value: String, sub: String) {
    Column {
        Text(text = label, color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = sub, color = Color(0xFF475569), fontSize = 10.sp)
    }
}

@Composable
fun LatencyRealtimeChart(points: List<Int>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
    ) {
        if (points.isEmpty()) return@Canvas

        val sampleGap = size.width / 24f
        val maxLatency = (points.filter { it > 0 }.maxOrNull() ?: 100).coerceAtLeast(150).toFloat()
        
        var prevX = 0f
        var prevY = size.height

        for (i in points.indices) {
            val pointValue = points[i]
            val x = i * sampleGap
            
            val y = if (pointValue <= 0) {
                size.height - 2.dp.toPx()
            } else {
                size.height - (pointValue.toFloat() / maxLatency) * (size.height * 0.8f)
            }

            if (i > 0) {
                drawLine(
                    color = if (pointValue <= 0) Color(0xFFEF4444) else Color(0xFF007AFF),
                    start = Offset(prevX, prevY),
                    end = Offset(x, y),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = if (pointValue <= 0) Color(0xFFEF4444) else Color(0xFF38BDF8),
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
            prevX = x
            prevY = y
        }
    }
}

@Composable
fun UtilityGridItem(
    title: String,
    description: String,
    icon: ImageVector,
    colorAccent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colorAccent.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF4B5563)
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionBottomSheet(
    apps: List<NamedApp>,
    onDismiss: () -> Unit,
    onAppSelected: (NamedApp) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Select application node",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = Color(0xFFF3F4F6))
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(apps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (app.presetIconRes) {
                                    "sports_esports" -> Icons.Default.SportsEsports
                                    "play_circle" -> Icons.Default.PlayCircleOutline
                                    "music_note" -> Icons.Default.MusicNote
                                    "movie" -> Icons.Default.Movie
                                    "chat" -> Icons.Default.ChatBubbleOutline
                                    "dns" -> Icons.Default.Dns
                                    else -> Icons.Default.Apps
                                },
                                contentDescription = null,
                                tint = Color(0xFF007AFF)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = app.packageName ?: "custom.target.host",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TargetAppSelectionScreen(
    apps: List<NamedApp>,
    selectedApp: NamedApp?,
    onDismiss: () -> Unit,
    onAppSelected: (NamedApp) -> Unit
) {
    BackHandler {
        onDismiss()
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                (it.packageName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar matching screenshot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = "Target apps",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                IconButton(onClick = { /* More options dropdown etc. */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Search Bar Container with exact spacing/color
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp)
                    .background(Color(0xFFE5E7EB).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    cursorBrush = SolidColor(Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search apps...",
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF94A3B8))
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable App list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredApps) { app ->
                    val isSelected = selectedApp?.id == app.id
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Custom premium App Icon rendering
                        AppIconContainer(app = app)

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = app.packageName ?: "custom.target.host",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // State rounded-rectangle checkbox on the right (matches screenshot!)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = if (isSelected) Color(0xFF007AFF) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = if (isSelected) 0.dp else 2.dp,
                                    color = if (isSelected) Color.Transparent else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected indicator",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconContainer(app: NamedApp, size: Dp = 46.dp) {
    val context = LocalContext.current
    val systemAppIcon = remember(app.packageName) {
        app.packageName?.let { pkg ->
            try {
                context.packageManager.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null
            }
        }
    }

    if (systemAppIcon != null) {
        AsyncImage(
            model = systemAppIcon,
            contentDescription = "${app.name} icon",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * (10f / 46f)))
        )
    } else {
        // Fallback drawings based on preset design
        val (bgColor, iconVector) = remember(app.presetIconRes) {
            when (app.presetIconRes) {
                "sports_esports" -> Pair(Color(0xFFEFF6FF), Icons.Default.SportsEsports)
                "play_circle" -> Pair(Color(0xFFFEF2F2), Icons.Default.PlayCircleOutline)
                "music_note" -> Pair(Color(0xFFECFDF5), Icons.Default.MusicNote)
                "movie" -> Pair(Color(0xFFFFF7ED), Icons.Default.Movie)
                "chat" -> Pair(Color(0xFFF5F3FF), Icons.Default.ChatBubbleOutline)
                "dns" -> Pair(Color(0xFFF0FDF4), Icons.Default.Dns)
                else -> Pair(Color(0xFFF3F4F6), Icons.Default.Apps)
            }
        }
        
        val tintColor = remember(app.presetIconRes) {
            when (app.presetIconRes) {
                "sports_esports" -> Color(0xFF3B82F6)
                "play_circle" -> Color(0xFFEF4444)
                "music_note" -> Color(0xFF10B981)
                "movie" -> Color(0xFFF97316)
                "chat" -> Color(0xFF8B5CF6)
                "dns" -> Color(0xFF22C55E)
                else -> Color(0xFF6B7280)
            }
        }

        Box(
            modifier = Modifier
                .size(size)
                .background(bgColor, RoundedCornerShape(size * (10f / 46f)))
                .border(0.5.dp, tintColor.copy(alpha = 0.2f), RoundedCornerShape(size * (10f / 46f))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(size * (24f / 46f))
            )
        }
    }
}

@Composable
fun ImpairsTabScreen(
    viewModel: PlayPingViewModel
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Network Impairments",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF111827)
                )
            )
            Text(
                text = "Configure specific packet drop filters to simulate common connection impairments.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF4B5563))
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val checkers = listOf(
                        Triple(
                            "Connection lost",
                            "Blocks all incoming and outgoing network traffic. No network data can be transmitted.",
                            Color(0xFFEF4444)
                        ),
                        Triple(
                            "Block upload",
                            "Blocks all outgoing network traffic. The device cannot upload or send data to the network.",
                            Color(0xFFF59E0B)
                        ),
                        Triple(
                            "Block download",
                            "Blocks all incoming network traffic. The device cannot download or receive data from the network.",
                            Color(0xFF3B82F6)
                        )
                    )

                    checkers.forEach { (title, explanation, statusColor) ->
                        val isChecked = selectedProfile?.name == title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isChecked) Color(0xFFF1F5F9) else Color.Transparent)
                                .clickable {
                                    val matched = profiles.find { it.name == title }
                                    if (matched != null) {
                                        viewModel.selectProfile(matched)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Status LED dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusColor, CircleShape)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFF111827),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = explanation,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF4B5563),
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            // Blue checkbox with tick circle
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        if (isChecked) Color(0xFF007AFF) else Color.Transparent,
                                        CircleShape
                                    )
                                    .border(
                                        width = if (isChecked) 0.dp else 1.5.dp,
                                        color = if (isChecked) Color.Transparent else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTabScreen(
    viewModel: PlayPingViewModel,
    isPremium: Boolean,
    onUpgradeClick: () -> Unit
) {
    val targetIp by viewModel.targetHostIp.collectAsStateWithLifecycle()
    val isDnsQuerying by viewModel.dnsQuerying.collectAsStateWithLifecycle()
    val dnsResults by viewModel.dnsQueryResults.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var inputIp by remember { mutableStateOf(targetIp) }
    var dnsInput by remember { mutableStateOf("") }
    
    val isPortScanning by viewModel.portScanning.collectAsStateWithLifecycle()
    val portScanResults by viewModel.portScanResults.collectAsStateWithLifecycle()
    var portScanHostInput by remember { mutableStateOf("google.com") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Diagnostics & Toolkit",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF111827)
                )
            )
        }

        // TOOL 1: ECHO TARGET IP ADDR Config
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Global Echo Target",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    )

                    OutlinedTextField(
                        value = inputIp,
                        onValueChange = { inputIp = it },
                        label = { Text("Echo Server Host / IP") },
                        placeholder = { Text("e.g. 8.8.8.8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            viewModel.setTargetHostIp(inputIp)
                            Toast.makeText(context, "Echo Target server configured!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply Target Host Address", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // TOOL 2: DNS RESOLUTION TOOL
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "🌐 Active DNS Audit Checker",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    )

                    OutlinedTextField(
                        value = dnsInput,
                        onValueChange = { dnsInput = it },
                        label = { Text("Query Domain Target") },
                        placeholder = { Text("e.g. playping.net") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (dnsInput.isNotBlank()) {
                                viewModel.runDnsQuery(dnsInput)
                            } else {
                                Toast.makeText(context, "Please write a domain name!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isDnsQuerying
                    ) {
                        if (isDnsQuerying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Query DNS Resolution", fontWeight = FontWeight.Bold)
                        }
                    }

                    dnsResults?.let { results ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                results.forEach { (key, value) ->
                                    Text(text = key, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF374151))
                                    Text(text = value, fontSize = 13.sp, color = Color(0xFF1F2937), fontFamily = FontFamily.Monospace)
                                    HorizontalDivider(color = Color(0xFFE5E7EB))
                                }
                            }
                        }
                    }
                }
            }
        }

        // TOOL 3: PORT SCANNER TOOL
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "🔌 Connection Port Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    )

                    OutlinedTextField(
                        value = portScanHostInput,
                        onValueChange = { portScanHostInput = it },
                        label = { Text("Host to check") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            viewModel.runPortScan(portScanHostInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPortScanning
                    ) {
                        if (isPortScanning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Scan Transport Ports", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (portScanResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            portScanResults.forEach { result ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Port ${result.first}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (result.second) "OPEN (REACHABLE)" else "CLOSED",
                                        color = if (result.second) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TOOL 4: PREMIUM CARD CONTROL
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isPremium) Color(0xFFFEF3C7) else Color.White),
                border = BorderStroke(1.dp, if (isPremium) Color(0xFFFBBF24) else Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPremium) "👑 Premium access active" else "🔒 Upgrade PlayPing License",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    )
                    Text(
                        text = if (isPremium) "Thank you for getting PlayPing Pro! All custom diagnostic variables, multi-packet pipelines, and DNS resolutions are unlocked to full capacity." else "Unlock premium settings dashboard, unlimited packet simulations, priority DNS checking servers, and zero distracting ads.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF78350F))
                    )

                    if (!isPremium) {
                        Button(
                            onClick = onUpgradeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Upgrade license keys now", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayPingBottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.border(0.5.dp, Color(0xFFE5E7EB)),
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = currentTab == "home",
            onClick = { onTabSelected("home") },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Home, 
                    contentDescription = "Dashboard Hub", 
                    modifier = Modifier.size(22.dp)
                ) 
            },
            label = { 
                Text(
                    "Home", 
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ) 
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color.Black,
                unselectedIconColor = Color(0xFF94A3B8),
                unselectedTextColor = Color(0xFF94A3B8),
                indicatorColor = Color(0xFFDBEAFE)
            ),
            modifier = Modifier.testTag("nav_home")
        )

        NavigationBarItem(
            selected = currentTab == "impairs",
            onClick = { onTabSelected("impairs") },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Layers, 
                    contentDescription = "Configure Impairment profile",
                    modifier = Modifier.size(22.dp)
                ) 
            },
            label = { 
                Text(
                    "Impairs", 
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ) 
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color.Black,
                unselectedIconColor = Color(0xFF94A3B8),
                unselectedTextColor = Color(0xFF94A3B8),
                indicatorColor = Color(0xFFDBEAFE)
            ),
            modifier = Modifier.testTag("nav_impairs")
        )

        NavigationBarItem(
            selected = currentTab == "settings",
            onClick = { onTabSelected("settings") },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Settings, 
                    contentDescription = "Diagnostics tools list",
                    modifier = Modifier.size(22.dp)
                ) 
            },
            label = { 
                Text(
                    "Settings", 
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ) 
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color.Black,
                unselectedIconColor = Color(0xFF94A3B8),
                unselectedTextColor = Color(0xFF94A3B8),
                indicatorColor = Color(0xFFDBEAFE)
            ),
            modifier = Modifier.testTag("nav_settings")
        )
    }
}

@Composable
fun PremiumUpgradeDialog(
    isPremium: Boolean,
    onDismiss: () -> Unit,
    onUpgradeSuccess: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "PlayPing Pro License",
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = "Upgrade benefits",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "Unlock high tier diagnostic access:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LabelInfoRow("✓ Unlimited Custom Impairments")
                    LabelInfoRow("✓ Real-time raw IP packets analysis")
                    LabelInfoRow("✓ Faster high speed DNS audit scanner")
                    LabelInfoRow("✓ Zero licensing interruptions")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgradeSuccess,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm Upgrade $1.99", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = Color(0xFF4B5563))
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun LabelInfoRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.Check, contentDescription = "Active benefit check", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
        Text(text = text, fontSize = 13.sp, color = Color(0xFF374151))
    }
}
