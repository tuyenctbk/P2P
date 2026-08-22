package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.viewmodel.ChatViewModel

@Composable
fun AppHealthDiagnosticCard(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val healthSummary by viewModel.healthSummary.collectAsStateWithLifecycle()
    val recentErrors by viewModel.recentErrors.collectAsStateWithLifecycle()
    val messagesSentCount by viewModel.messagesSentCount.collectAsStateWithLifecycle()
    val filesSharedCount by viewModel.filesSharedCount.collectAsStateWithLifecycle()
    val discoveriesInitiatedCount by viewModel.discoveriesInitiatedCount.collectAsStateWithLifecycle()

    var showRawErrorsLog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Health Score Badge
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
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = "App Health Diagnostics",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.health_diagnostics_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.health_diagnostics_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                val scoreColor = when {
                    healthSummary.healthScore >= 90 -> Color(0xFF16A34A)
                    healthSummary.healthScore >= 75 -> Color(0xFFD97706)
                    else -> Color(0xFFDC2626)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scoreColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.health_score, healthSummary.healthScore),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Firebase Analytics User Interaction Flow Counters
            Text(
                text = stringResource(R.string.firebase_analytics_interactions),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCounterBox(
                    label = stringResource(R.string.metric_messages),
                    eventName = "message_sent",
                    count = messagesSentCount,
                    icon = Icons.Default.Send,
                    modifier = Modifier.weight(1f)
                )
                MetricCounterBox(
                    label = stringResource(R.string.metric_files),
                    eventName = "file_shared",
                    count = filesSharedCount,
                    icon = Icons.Default.AttachFile,
                    modifier = Modifier.weight(1f)
                )
                MetricCounterBox(
                    label = stringResource(R.string.metric_scans),
                    eventName = "peer_discovery",
                    count = discoveriesInitiatedCount,
                    icon = Icons.Default.Radar,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error Trends Summary (Crashlytics Non-Fatal Exceptions)
            Text(
                text = stringResource(R.string.crashlytics_error_trends),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ErrorTrendItem(
                            title = stringResource(R.string.socket_failures),
                            count = healthSummary.totalSocketFailures,
                            isCritical = healthSummary.totalSocketFailures > 0
                        )
                        ErrorTrendItem(
                            title = stringResource(R.string.sync_failures),
                            count = healthSummary.totalSyncFailures,
                            isCritical = healthSummary.totalSyncFailures > 0
                        )
                        ErrorTrendItem(
                            title = stringResource(R.string.non_fatal_errors),
                            count = healthSummary.totalNonFatalErrors,
                            isCritical = healthSummary.totalNonFatalErrors > 3
                        )
                        ErrorTrendItem(
                            title = stringResource(R.string.active_sessions),
                            count = healthSummary.totalSessions,
                            isCritical = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Non-fatal Exception Handler Controls & Test Simulation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crashlytics Exception Handler Log (${recentErrors.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { showRawErrorsLog = !showRawErrorsLog },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(if (showRawErrorsLog) stringResource(R.string.hide_logs) else stringResource(R.string.show_details), fontSize = 11.sp)
                }
            }

            if (recentErrors.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✨ Zero socket or sync failures recorded in current session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentErrors.take(if (showRawErrorsLog) 10 else 3).forEach { err ->
                        DiagnosticErrorRow(event = err)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulation Action Buttons
            Text(
                text = stringResource(R.string.test_non_fatal_captures),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.triggerTestSocketError() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate_socket_error_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.simulate_socket_fail), fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.triggerTestBackgroundSyncError() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate_sync_error_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.SyncProblem, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.simulate_sync_fail), fontSize = 11.sp)
                }

                TextButton(
                    onClick = { viewModel.clearDiagnosticLogs() },
                    modifier = Modifier.testTag("clear_diagnostic_logs_btn")
                ) {
                    Text(stringResource(R.string.clear_logs), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCounterBox(
    label: String,
    eventName: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorTrendItem(
    title: String,
    count: Int,
    isCritical: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun DiagnosticErrorRow(event: com.example.util.P2PDiagnosticErrorEvent) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = event.category,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = event.formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
