package com.example.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onPeerScanned: (ip: String, name: String) -> Unit
) {
    var scannedInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("qr_scanner_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_scanner_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = stringResource(R.string.qr_scanner_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Simulated camera scanner viewbox with animated laser line
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "scanner_laser")
                val scanOffsetY by infiniteTransition.animateFloat(
                    initialValue = 0.1f,
                    targetValue = 0.9f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "laser_y"
                )
                val primaryColor = MaterialTheme.colorScheme.primary

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val laserY = size.height * scanOffsetY
                        drawLine(
                            color = primaryColor,
                            start = androidx.compose.ui.geometry.Offset(16.dp.toPx(), laserY),
                            end = androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), laserY),
                            strokeWidth = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera Scanner Active\n(Align QR code within frame)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                OutlinedTextField(
                    value = scannedInput,
                    onValueChange = {
                        scannedInput = it
                        errorMessage = null
                    },
                    label = { Text("Scanned Data or IP Address") },
                    placeholder = { Text("p2pconnect://add?ip=...&name=...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_input_field"),
                    singleLine = true,
                    isError = errorMessage != null
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }



                Button(
                    onClick = {
                        val input = scannedInput.trim()
                        if (input.isBlank()) {
                            errorMessage = "Please enter or scan valid QR data"
                            return@Button
                        }

                        if (input.startsWith("p2pconnect://add?")) {
                            try {
                                val uri = android.net.Uri.parse(input)
                                val ip = uri.getQueryParameter("ip")
                                val name = uri.getQueryParameter("name") ?: "Peer"
                                if (!ip.isNullOrBlank()) {
                                    onPeerScanned(ip, name)
                                    onDismiss()
                                    return@Button
                                }
                            } catch (e: Exception) {
                                // fallback
                            }
                        }

                        // Treat as direct IP or name
                        if (input.contains(".") || input.length >= 7) {
                            onPeerScanned(input, "Peer_$input")
                            onDismiss()
                        } else {
                            errorMessage = "Invalid connection format"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_qr_scan_button")
                ) {
                    Text("Connect to Peer")
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
