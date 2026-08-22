package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.ShareProfileDialog
import com.example.ui.QrScannerDialog
import com.example.ui.components.RemoteConfigFeatureControlCard
import com.example.ui.components.AppHealthDiagnosticCard
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    var nicknameInput by remember { mutableStateOf(uiState.myName) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showShareQrDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Ambient Mesh Gradient Glows
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val indigoGlow = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(androidx.compose.ui.graphics.Color(0x336366F1), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    radius = size.width * 0.8f
                )
                drawRect(indigoGlow)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Profile Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = nicknameInput,
                            onValueChange = { nicknameInput = it },
                            label = { Text("Display Nickname") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nickname_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.updateNickname(nicknameInput)
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("save_nickname_button")
                        ) {
                            Text("Save Nickname")
                        }
                    }
                }

                // Security & Privacy Card (Blocked Users)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Privacy & Connection Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Surface(
                            onClick = onNavigateToBlockedUsers,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Blocked Users Manager", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text("View and unblock restricted peers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // App Language & Localization Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("App Language & Localization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (appLanguage == "SYSTEM") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = if (appLanguage == "SYSTEM") "🌐 Auto (System)" else "🌐 Custom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (appLanguage == "SYSTEM") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val currentSysLang = remember { java.util.Locale.getDefault().displayLanguage }
                        Text(
                            text = if (appLanguage == "SYSTEM") "Auto-selecting language after system settings ($currentSysLang)" else "Manual locale override active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick choices
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "SYSTEM" to "Auto Select",
                                "en" to "English",
                                "es" to "Español",
                                "vi" to "Tiếng Việt"
                            ).forEach { (code, label) ->
                                val isSelected = appLanguage == code
                                Surface(
                                    onClick = { viewModel.setAppLanguage(code) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("lang_quick_btn_$code")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showLanguageDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_all_languages_dialog_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select from 68 Supported Languages…")
                        }
                    }
                }

                // Preferences & Sound Settings Card
                val isSoundEnabled by viewModel.isSoundNotificationsEnabled.collectAsStateWithLifecycle()
                val isHapticEnabled by viewModel.isHapticEnabled.collectAsStateWithLifecycle()
                val isPowerSaverEnabled by viewModel.isPowerSaverEnabled.collectAsStateWithLifecycle()
                val isOverlayVisible by viewModel.isDiagnosticOverlayVisible.collectAsStateWithLifecycle()
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Preferences & Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Theme Switcher Row
                        Text("Theme Style Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                                val isSelected = themeMode == mode
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                val borderStroke = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                Surface(
                                    onClick = { viewModel.setThemeMode(mode) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_btn_$mode"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = containerColor,
                                    border = borderStroke
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = contentColor
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TEXT_ROW_LABEL_SOUND("Notification Sounds")
                            }
                            Switch(
                                checked = isSoundEnabled,
                                onCheckedChange = { viewModel.setSoundNotificationsEnabled(it) },
                                modifier = Modifier.testTag("sound_notifications_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Haptic Feedback (Vibration)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Vibrate motor on incoming messages & file transfers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isHapticEnabled,
                                onCheckedChange = { viewModel.setHapticEnabled(it) },
                                modifier = Modifier.testTag("haptic_feedback_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery Saver P2P Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Throttle beacon broadcasts to save battery during background search", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isPowerSaverEnabled,
                                onCheckedChange = { viewModel.setPowerSaverEnabled(it) },
                                modifier = Modifier.testTag("power_saver_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Real-Time Diagnostic Overlay", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Display live transfer speeds and network stats on home screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isOverlayVisible,
                                onCheckedChange = { viewModel.toggleDiagnosticOverlay() },
                                modifier = Modifier.testTag("diagnostic_overlay_switch")
                            )
                        }
                    }
                }

                // Profile QR & Scanner Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Profile QR & Pairing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showShareQrDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("show_qr_button")
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share QR")
                            }
                            OutlinedButton(
                                onClick = { showScannerDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_qr_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan QR")
                            }
                        }
                    }
                }

                // Custom Daily Data Transfer Volume Chart
                DailyDataTransferChart(viewModel = viewModel)

                // Firebase Free Services & Telemetry Card
                val isFirebaseActive by viewModel.isFirebaseActive.collectAsStateWithLifecycle()
                val firebaseEventsLog by viewModel.firebaseEventsLog.collectAsStateWithLifecycle()
                val remoteConfigStatus by viewModel.remoteConfigStatus.collectAsStateWithLifecycle()
                var testEventStatus by remember { mutableStateOf<String?>(null) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Firebase Services & Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFirebaseActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isFirebaseActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = if (isFirebaseActive) "● Active" else "○ Local Fallback",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFirebaseActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Integrated Services Status Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Analytics" to "📊 Live",
                                "Crashlytics" to "🛡️ Ready",
                                "Performance" to "⚡ Tracing",
                                "RemoteConfig" to "⚙️ Synced"
                            ).forEach { (service, badge) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(text = service, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Remote Config: $remoteConfigStatus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.testFirebaseDiagnostics()
                                    testEventStatus = "Logged diagnostic event & test exception to Crashlytics"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_firebase_diagnostic_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Telemetry", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.refreshRemoteConfig()
                                    testEventStatus = "Triggered Remote Config refresh"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("refresh_remote_config_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Config", fontSize = 12.sp)
                            }
                        }

                        if (testEventStatus != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = testEventStatus ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (firebaseEventsLog.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Recent Telemetry Events Stream:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    firebaseEventsLog.take(4).forEach { entry ->
                                        Text(
                                            text = entry,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Firebase Remote Config Feature Flags Management
                RemoteConfigFeatureControlCard(viewModel = viewModel)

                // App Health & Diagnostics (Firebase Analytics & Crashlytics Integration)
                AppHealthDiagnosticCard(viewModel = viewModel)

                // Network & Subnet Diagnostics Card
                val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Network & Wi-Fi Subnet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (networkStatus.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (networkStatus.isConnected) "● Connected" else "○ Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (networkStatus.isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Active Interface: ${networkStatus.networkName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Local IP Address: ${networkStatus.localIp}", style = MaterialTheme.typography.bodyMedium)
                        Text("Local Subnet: ${networkStatus.subnetPrefix}.*", style = MaterialTheme.typography.bodyMedium)
                        Text("P2P Ports: TCP 8888 (Messaging/Files) • UDP 8889 (Discovery)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.triggerDiscoveryRefresh() },
                            modifier = Modifier.testTag("rescan_network_settings_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-scan Local Subnet Peers")
                        }
                    }
                }

                // WorkManager Background Sync Persistence Card
                val isBackgroundSyncEnabled by viewModel.isBackgroundSyncEnabled.collectAsStateWithLifecycle()
                val lastBackgroundSync by viewModel.lastBackgroundSync.collectAsStateWithLifecycle()
                var syncFeedbackMsg by remember { mutableStateOf<String?>(null) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("WorkManager Background Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Persists P2P presence discovery in the background and delivers queued scheduled messages while respecting battery constraints.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Background P2P Sync Handler", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (lastBackgroundSync != null) "Last sync: $lastBackgroundSync" else "Periodic sync scheduled every 15 min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isBackgroundSyncEnabled,
                                onCheckedChange = { viewModel.setBackgroundSyncEnabled(it) },
                                modifier = Modifier.testTag("background_sync_switch")
                            )
                        }

                        if (isBackgroundSyncEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.triggerImmediateBackgroundSync()
                                        syncFeedbackMsg = "Triggered expedited background sync worker"
                                    },
                                    modifier = Modifier.testTag("trigger_background_sync_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Trigger Sync Now", style = MaterialTheme.typography.labelMedium)
                                }
                                if (syncFeedbackMsg != null) {
                                    Text(
                                        text = syncFeedbackMsg ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Data Management Card (with Auto-Purge & Auto-Archive)
                val isAutoArchiveEnabled by viewModel.isAutoArchiveEnabled.collectAsStateWithLifecycle()
                val autoPurgeDuration by viewModel.autoPurgeDuration.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()
                var purgeCountInfo by remember { mutableStateOf<String?>(null) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Data Management & Retention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Auto-Purge Setting
                        Text("Auto-Purge Expired Messages", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Automatically delete local messages older than the selected retention window for increased privacy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val purgeOptions = listOf(
                            "OFF" to "Off",
                            "24_HOURS" to "24 Hours",
                            "7_DAYS" to "7 Days",
                            "30_DAYS" to "30 Days",
                            "90_DAYS" to "90 Days"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            purgeOptions.forEach { (key, label) ->
                                val isSelected = autoPurgeDuration == key
                                Surface(
                                    onClick = {
                                        viewModel.setAutoPurgeDuration(key)
                                        purgeCountInfo = null
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("purge_option_$key")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (autoPurgeDuration != "OFF") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val deleted = viewModel.purgeOldMessages(autoPurgeDuration)
                                            purgeCountInfo = "Purged $deleted message(s) older than selected period."
                                        }
                                    },
                                    modifier = Modifier.testTag("run_purge_now_button")
                                ) {
                                    Text("Purge Now (${purgeOptions.find { it.first == autoPurgeDuration }?.second})", style = MaterialTheme.typography.labelMedium)
                                }
                                if (purgeCountInfo != null) {
                                    Text(
                                        text = purgeCountInfo ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        // Auto Archive Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Archive Old Chats", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Automatically move chats with no activity for 30 days to archive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAutoArchiveEnabled,
                                onCheckedChange = { viewModel.setAutoArchiveEnabled(it) },
                                modifier = Modifier.testTag("auto_archive_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Chat History")
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat History") },
            text = { Text("Are you sure you want to delete all stored messages? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showShareQrDialog) {
        ShareProfileDialog(
            myName = uiState.myName,
            localIp = uiState.localIp,
            onDismiss = { showShareQrDialog = false }
        )
    }

    if (showScannerDialog) {
        QrScannerDialog(
            onDismiss = { showScannerDialog = false },
            onPeerScanned = { ip, name ->
                viewModel.addManualPeer(ip, name)
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = appLanguage,
            onSelectLanguage = { code ->
                viewModel.setAppLanguage(code)
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun TEXT_ROW_LABEL_SOUND(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    Text("Play sound effects on incoming messages & file transfers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DailyDataTransferChart(viewModel: ChatViewModel) {
    val stats = remember { viewModel.getDailyDataStats() }
    val maxVal = remember(stats) {
        val highest = stats.maxOfOrNull { maxOf(it.second.first, it.second.second) } ?: 1000000L
        if (highest <= 0L) 1000000L else highest
    }

    var selectedDayIndex by remember { mutableStateOf(-1) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Daily Network Data Transfer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Weekly view of sent & received volume",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Graph Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                stats.forEachIndexed { index, (dayLabel, data) ->
                    val sentBytes = data.first
                    val recvBytes = data.second

                    val sentRatio = if (sentBytes > 0) maxOf(0.04f, sentBytes.toFloat() / maxVal) else 0f
                    val recvRatio = if (recvBytes > 0) maxOf(0.04f, recvBytes.toFloat() / maxVal) else 0f

                    val isSelected = selectedDayIndex == index

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedDayIndex = if (isSelected) -1 else index
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 1.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Sent Bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(sentRatio)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            )
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            // Received Bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(recvRatio)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.tertiary,
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Detail panel on tap
            if (selectedDayIndex != -1 && selectedDayIndex < stats.size) {
                val dayData = stats[selectedDayIndex]
                val sentFormatted = formatSize(dayData.second.first)
                val recvFormatted = formatSize(dayData.second.second)
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Stats for: ${dayData.first}",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📤 Sent: $sentFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "📥 Received: $recvFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "(Tap bars for stats)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

val ALL_LANGUAGES = listOf(
    "SYSTEM" to ("Auto Select (System Default)" to "Auto select language after device system language"),
    "af" to ("Afrikaans" to "Afrikaans"),
    "am" to ("Amharic" to "አማርኛ"),
    "ar" to ("Arabic" to "العربية"),
    "az" to ("Azerbaijani" to "Azərbaycan"),
    "bg" to ("Bulgarian" to "Български"),
    "bn" to ("Bengali" to "বাংলা"),
    "bs" to ("Bosnian" to "Bosanski"),
    "ca" to ("Catalan" to "Català"),
    "cs" to ("Czech" to "Čeština"),
    "da" to ("Danish" to "Dansk"),
    "de" to ("German" to "Deutsch"),
    "el" to ("Greek" to "Ελληνικά"),
    "en" to ("English" to "English"),
    "es" to ("Spanish" to "Español"),
    "et" to ("Estonian" to "Eesti"),
    "fa" to ("Persian" to "فارسی"),
    "fi" to ("Finnish" to "Suomi"),
    "fr" to ("French" to "Français"),
    "gl" to ("Galician" to "Galego"),
    "gu" to ("Gujarati" to "ગુજરાતી"),
    "he" to ("Hebrew" to "עברית"),
    "hi" to ("Hindi" to "हिन्दी"),
    "hr" to ("Croatian" to "Hrvatski"),
    "hu" to ("Hungarian" to "Magyar"),
    "hy" to ("Armenian" to "Հայերեն"),
    "id" to ("Indonesian" to "Bahasa Indonesia"),
    "is" to ("Icelandic" to "Íslenska"),
    "it" to ("Italian" to "Italiano"),
    "ja" to ("Japanese" to "日本語"),
    "ka" to ("Georgian" to "ქართული"),
    "km" to ("Khmer" to "ភាសាខ្មែរ"),
    "kn" to ("Kannada" to "ಕನ್ನಡ"),
    "ko" to ("Korean" to "한국어"),
    "ky" to ("Kyrgyz" to "Кыргызча"),
    "lo" to ("Lao" to "ພາສາລາວ"),
    "lt" to ("Lithuanian" to "Lietuvių"),
    "lv" to ("Latvian" to "Latviešu"),
    "mk" to ("Macedonian" to "Македонски"),
    "ml" to ("Malayalam" to "മലയാളം"),
    "mn" to ("Mongolian" to "Монгол"),
    "mr" to ("Marathi" to "मराठी"),
    "ms" to ("Malay" to "Bahasa Melayu"),
    "my" to ("Burmese" to "မြန်မာဘာသာ"),
    "nb" to ("Norwegian" to "Norsk Bokmål"),
    "ne" to ("Nepali" to "नेपाली"),
    "nl" to ("Dutch" to "Nederlands"),
    "pa" to ("Punjabi" to "ਪੰਜਾਬੀ"),
    "pl" to ("Polish" to "Polski"),
    "pt" to ("Portuguese" to "Português"),
    "ro" to ("Romanian" to "Română"),
    "ru" to ("Russian" to "Русский"),
    "si" to ("Sinhala" to "සිංහල"),
    "sk" to ("Slovak" to "Slovenčina"),
    "sl" to ("Slovenian" to "Slovenščina"),
    "sq" to ("Albanian" to "Shqip"),
    "sr" to ("Serbian" to "Српски"),
    "sv" to ("Swedish" to "Svenska"),
    "sw" to ("Swahili" to "Kiswahili"),
    "ta" to ("Tamil" to "தமிழ்"),
    "te" to ("Telugu" to "తెలుగు"),
    "th" to ("Thai" to "ไทย"),
    "tl" to ("Tagalog / Filipino" to "Filipino"),
    "tr" to ("Turkish" to "Türkçe"),
    "uk" to ("Ukrainian" to "Українська"),
    "ur" to ("Urdu" to "اردو"),
    "uz" to ("Uzbek" to "Oʻzbekcha"),
    "vi" to ("Vietnamese" to "Tiếng Việt"),
    "zh" to ("Chinese" to "中文")
)

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ALL_LANGUAGES
        } else {
            ALL_LANGUAGES.filter {
                it.first.contains(searchQuery, ignoreCase = true) ||
                it.second.first.contains(searchQuery, ignoreCase = true) ||
                it.second.second.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select App Language", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 68 languages...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("language_search_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLanguages.size) { index ->
                        val (code, names) = filteredLanguages[index]
                        val (englishName, nativeName) = names
                        val isSelected = currentLanguage == code

                        Surface(
                            onClick = {
                                onSelectLanguage(code)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().testTag("lang_item_$code")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = nativeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (code == "SYSTEM") "Auto select language after system settings" else "$englishName ($code)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
