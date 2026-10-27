package com.charon.dexon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charon.dexon.ui.theme.RiskColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    bridgeUrl: String?,
    onSystemChecks: () -> Unit,
    onLiveConnections: () -> Unit,
    onToggleBridge: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Tools", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Device-level tools. The active ones run through Shizuku; nothing here changes your apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ToolCard(
                    icon = Icons.Default.Rule,
                    title = "System Checks",
                    subtitle = "Read-only device posture: SELinux, ADB-over-TCP, verified boot…",
                    onClick = onSystemChecks,
                )
            }
            item {
                ToolCard(
                    icon = Icons.Default.NetworkCheck,
                    title = "Live Connections",
                    subtitle = "See who each app is talking to, right now. No VPN, no decryption.",
                    onClick = onLiveConnections,
                )
            }
            item {
                ToolCard(
                    icon = if (bridgeUrl != null) Icons.Default.Wifi else Icons.Default.WifiOff,
                    title = "Wi-Fi PC Bridge",
                    subtitle = bridgeUrl?.let { "Active — open $it on a computer" }
                        ?: "Start a local server to pull APKs, manifests and reports to your PC",
                    tint = if (bridgeUrl != null) RiskColors.Emerald else null,
                    onClick = onToggleBridge,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = tint ?: MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
