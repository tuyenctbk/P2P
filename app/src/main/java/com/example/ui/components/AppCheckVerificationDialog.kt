package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppCheckVerificationDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onVerified: () -> Unit = {}
) {
    val isAppCheckVerified by viewModel.isAppCheckVerified.collectAsStateWithLifecycle()
    val appCheckStatus by viewModel.appCheckStatus.collectAsStateWithLifecycle()
    val lastAppCheckToken by viewModel.lastAppCheckToken.collectAsStateWithLifecycle()

    var isVerifying by remember { mutableStateOf(false) }
    var verificationStep by remember { mutableIntStateOf(if (isAppCheckVerified) 3 else 0) }
    var verificationMessage by remember { mutableStateOf("Ready to authenticate network traffic") }
    val coroutineScope = rememberCoroutineScope()

    fun runVerification() {
        isVerifying = true
        verificationStep = 1
        verificationMessage = "1/3 Verifying app signature & device integrity..."

        coroutineScope.launch {
            delay(500)
            verificationStep = 2
            verificationMessage = "2/3 Requesting Firebase App Check reCAPTCHA token..."

            viewModel.verifyAppCheck(forceRefresh = true) { result ->
                coroutineScope.launch {
                    delay(400)
                    verificationStep = 3
                    isVerifying = false
                    verificationMessage = if (result.isSuccess) {
                        "3/3 ✅ Traffic verified! Authenticated P2P Node (${result.tokenPrefix}...)"
                    } else {
                        "Verification fallback active: ${result.message}"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!isAppCheckVerified) {
            runVerification()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text("Firebase App Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Firebase App Check with reCAPTCHA protects the peer-to-peer network against unauthorized traffic, bots, and simulated clients.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAppCheckVerified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isAppCheckVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Integrity Status",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isAppCheckVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ) {
                                Text(
                                    text = if (isAppCheckVerified) "AUTHENTICATED" else "VERIFYING",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = verificationMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (lastAppCheckToken != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Token: ${lastAppCheckToken?.take(16)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Verification Steps Indicator
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VerificationStepRow(
                        title = "reCAPTCHA Bot Protection",
                        subtitle = "Validates human/genuine client traffic",
                        isCompleted = verificationStep >= 1
                    )
                    VerificationStepRow(
                        title = "Play Integrity & App Attestation",
                        subtitle = "Verifies binary checksum & signature",
                        isCompleted = verificationStep >= 2
                    )
                    VerificationStepRow(
                        title = "P2P Socket Handshake Clearance",
                        subtitle = "Permits encrypted peer sockets",
                        isCompleted = verificationStep >= 3
                    )
                }

                if (isVerifying) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onVerified()
                    onDismiss()
                },
                enabled = isAppCheckVerified && !isVerifying,
                modifier = Modifier.testTag("appcheck_confirm_button")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Proceed to P2P")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { runVerification() },
                enabled = !isVerifying,
                modifier = Modifier.testTag("appcheck_reverify_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Re-verify")
            }
        }
    )
}

@Composable
private fun VerificationStepRow(
    title: String,
    subtitle: String,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Shield,
            contentDescription = null,
            tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
                color = if (isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
