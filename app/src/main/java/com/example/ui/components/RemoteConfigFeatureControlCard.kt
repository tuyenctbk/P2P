package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.viewmodel.ChatViewModel

@Composable
fun RemoteConfigFeatureControlCard(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val isVoiceNotesEnabled by viewModel.isVoiceNotesEnabled.collectAsStateWithLifecycle()
    val isReactionsEnabled by viewModel.isReactionsEnabled.collectAsStateWithLifecycle()
    val isAutoPurgeEnabled by viewModel.isAutoPurgeEnabled.collectAsStateWithLifecycle()
    val isQuickReplyEnabled by viewModel.isQuickReplyEnabled.collectAsStateWithLifecycle()
    val isAppCheckEnforced by viewModel.isAppCheckEnforced.collectAsStateWithLifecycle()
    val maxAttachmentSizeMb by viewModel.maxAttachmentSizeMb.collectAsStateWithLifecycle()
    val remoteBannerAnnouncement by viewModel.remoteBannerAnnouncement.collectAsStateWithLifecycle()
    val remoteConfigStatus by viewModel.remoteConfigStatus.collectAsStateWithLifecycle()

    var showEditBannerDialog by remember { mutableStateOf(false) }
    var bannerInput by remember { mutableStateOf(remoteBannerAnnouncement) }
    var bannerStatusFeedback by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Cloud Remote Config",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Dynamic Feature Flags",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Firebase Remote Config Over-the-Air Controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "OTA Live",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Control app behaviors and rollout features in real-time across devices without requiring an APK update or app store release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic UI Accent Color Picker (Remote Config)
            val remoteAccentColor by viewModel.remoteAccentColor.collectAsStateWithLifecycle()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(com.example.ui.theme.parseHexColor(remoteAccentColor, MaterialTheme.colorScheme.primary))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.dynamic_accent_color),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = remoteAccentColor.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val colorPresets = listOf(
                        "#4F46E5" to "Indigo",
                        "#0D9488" to "Teal",
                        "#E11D48" to "Rose",
                        "#7C3AED" to "Purple",
                        "#D97706" to "Amber",
                        "#16A34A" to "Emerald"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        colorPresets.forEach { (hex, label) ->
                            val isSelected = remoteAccentColor.equals(hex, ignoreCase = true)
                            val color = com.example.ui.theme.parseHexColor(hex, MaterialTheme.colorScheme.primary)
                            Surface(
                                onClick = { viewModel.updateRemoteAccentColor(hex) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("accent_color_$label")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Feature Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteFeatureToggleItem(
                    title = "Voice Notes & Audio Clips",
                    description = "Enable mic recording button and audio waveform player",
                    isEnabled = isVoiceNotesEnabled,
                    icon = Icons.Default.Mic,
                    testTag = "toggle_remote_voice_notes",
                    onToggle = { viewModel.updateRemoteFeatureFlag("enable_voice_notes", it) }
                )

                RemoteFeatureToggleItem(
                    title = "Emoji Message Reactions",
                    description = "Allow adding quick emoji reactions on chat bubbles",
                    isEnabled = isReactionsEnabled,
                    icon = Icons.Default.AddReaction,
                    testTag = "toggle_remote_reactions",
                    onToggle = { viewModel.updateRemoteFeatureFlag("enable_reactions", it) }
                )

                RemoteFeatureToggleItem(
                    title = "Quick Reply Suggestions",
                    description = "Display instant response suggestion chips in chat bar",
                    isEnabled = isQuickReplyEnabled,
                    icon = Icons.Default.Quickreply,
                    testTag = "toggle_remote_quick_reply",
                    onToggle = { viewModel.updateRemoteFeatureFlag("enable_quick_reply", it) }
                )

                RemoteFeatureToggleItem(
                    title = "Auto-Purge Data Policy",
                    description = "Allow automatic cleanup of expired encrypted messages",
                    isEnabled = isAutoPurgeEnabled,
                    icon = Icons.Default.AutoDelete,
                    testTag = "toggle_remote_auto_purge",
                    onToggle = { viewModel.updateRemoteFeatureFlag("enable_auto_purge", it) }
                )

                RemoteFeatureToggleItem(
                    title = "App Check & reCAPTCHA Enforcement",
                    description = "Require legitimate device attestation before P2P connection",
                    isEnabled = isAppCheckEnforced,
                    icon = Icons.Default.Security,
                    testTag = "toggle_remote_app_check",
                    onToggle = { viewModel.updateRemoteFeatureFlag("enable_app_check_enforcement", it) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Max Attachment Size Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Max Attachment Limit",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dynamic payload cap: ${maxAttachmentSizeMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5L, 10L, 25L, 50L).forEach { sizeMb ->
                            FilterChip(
                                selected = maxAttachmentSizeMb == sizeMb,
                                onClick = { viewModel.updateRemoteConfigLong("max_attachment_size_mb", sizeMb) },
                                label = { Text("${sizeMb}MB", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Remote Banner Announcement
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Global In-App Announcement Banner", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                bannerInput = remoteBannerAnnouncement
                                showEditBannerDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (remoteBannerAnnouncement.isBlank()) "+ Set Banner" else "Edit", fontSize = 11.sp)
                        }
                    }

                    if (remoteBannerAnnouncement.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📢 $remoteBannerAnnouncement",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.updateRemoteBanner("") },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Banner", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cloud Sync Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.refreshRemoteConfig()
                        bannerStatusFeedback = "Fetched & activated latest cloud parameters"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_remote_config_parameters_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fetch & Sync Cloud", fontSize = 12.sp)
                }
            }

            if (bannerStatusFeedback != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bannerStatusFeedback ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showEditBannerDialog) {
        AlertDialog(
            onDismissRequest = { showEditBannerDialog = false },
            title = { Text("Set In-App Remote Banner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Broadcast a live announcement text bar across all chat and home screens via Remote Config.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bannerInput,
                        onValueChange = { bannerInput = it },
                        placeholder = { Text("e.g. 🚀 Welcome to P2P Secure Mesh v6.0!") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRemoteBanner(bannerInput.trim())
                        showEditBannerDialog = false
                    }
                ) {
                    Text("Broadcast")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBannerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RemoteFeatureToggleItem(
    title: String,
    description: String,
    isEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isEnabled) "ACTIVE" else "DISABLED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
