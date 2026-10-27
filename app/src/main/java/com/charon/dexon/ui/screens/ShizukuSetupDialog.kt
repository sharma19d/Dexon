package com.charon.dexon.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.charon.dexon.data.shizuku.ShizukuBridge
import com.charon.dexon.data.shizuku.ShizukuStatus
import com.charon.dexon.ui.theme.RiskColors

/**
 * Step-by-step Shizuku setup, shown only after the user taps "Run via Shizuku" while it is not
 * running. Status is re-read every time the user returns from Settings or the Shizuku app.
 */
@Composable
fun ShizukuSetupDialog(
    statusProvider: () -> ShizukuStatus,
    onDismiss: () -> Unit,
    onReady: () -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(statusProvider()) }
    var devOptionsOn by remember { mutableStateOf(isDeveloperOptionsEnabled(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = statusProvider()
        devOptionsOn = isDeveloperOptionsEnabled(context)
    }

    val installed = status != ShizukuStatus.NOT_INSTALLED
    val running = status == ShizukuStatus.READY || status == ShizukuStatus.NEEDS_PERMISSION
    val hasWirelessDebugging = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (running) "Shizuku is running" else "Set up Shizuku") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Shizuku lets Dexon run commands with ADB shell rights, right on this phone. " +
                        "No computer or root needed. One-time setup:",
                    style = MaterialTheme.typography.bodyMedium,
                )

                SetupStep(number = 1, title = "Install Shizuku", done = installed) {
                    if (!installed) {
                        Button(onClick = { openShizukuStorePage(context) }) { Text("Install from Play Store") }
                    }
                }

                SetupStep(
                    number = 2,
                    title = "Turn on Developer options",
                    done = devOptionsOn,
                    description = if (devOptionsOn) null
                    else "Open About phone and tap \"Build number\" 7 times.",
                ) {
                    if (!devOptionsOn) {
                        OutlinedButton(onClick = { openSettings(context, Settings.ACTION_DEVICE_INFO_SETTINGS) }) {
                            Text("Open About phone")
                        }
                    }
                }

                if (hasWirelessDebugging) {
                    SetupStep(
                        number = 3,
                        title = "Start Shizuku with Wireless debugging",
                        done = running,
                        description = if (running) null else
                            "In Shizuku, tap Pairing under \"Start via Wireless debugging\", then open " +
                                "Developer options → Wireless debugging → \"Pair device with pairing code\" " +
                                "and enter the code in Shizuku's notification. Then tap Start in Shizuku. " +
                                "Wi-Fi must be on. Repeat Start after every reboot.",
                    ) {
                        if (!running) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openShizuku(context) }, enabled = installed) { Text("Open Shizuku") }
                                OutlinedButton(
                                    onClick = { openSettings(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                                    enabled = devOptionsOn,
                                ) { Text("Developer options") }
                            }
                        }
                    }
                } else {
                    // Android 8–10 have no Wireless debugging
                    SetupStep(
                        number = 3,
                        title = "Start Shizuku from a computer (once per reboot)",
                        done = running,
                        description = if (running) null else
                            "Android ${Build.VERSION.RELEASE} has no Wireless debugging. Enable USB debugging, " +
                                "connect to a computer and run the command Shizuku shows under " +
                                "\"Start by connecting to a computer\". Rooted phones can use \"Start\" with root instead.",
                    ) {
                        if (!running) {
                            Button(onClick = { openShizuku(context) }, enabled = installed) { Text("Open Shizuku") }
                        }
                    }
                }

                if (running) {
                    Text(
                        if (status == ShizukuStatus.NEEDS_PERMISSION)
                            "Tap Run now and allow Dexon in the Shizuku prompt."
                        else
                            "All set.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiskColors.Emerald,
                    )
                }
            }
        },
        confirmButton = {
            if (running) {
                Button(onClick = onReady) { Text("Run now") }
            } else {
                TextButton(onClick = { status = statusProvider() }) { Text("Check again") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    done: Boolean,
    description: String? = null,
    actions: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) RiskColors.Emerald else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.padding(top = 2.dp)) { actions() }
        }
    }
}

private fun isDeveloperOptionsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
}.getOrDefault(false)

private fun openShizukuStorePage(context: Context) {
    val pkg = ShizukuBridge.SHIZUKU_PACKAGE
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
    startSafely(context, market) || startSafely(context, web)
}

private fun openShizuku(context: Context) {
    context.packageManager.getLaunchIntentForPackage(ShizukuBridge.SHIZUKU_PACKAGE)
        ?.let { startSafely(context, it) }
}

private fun openSettings(context: Context, action: String) {
    startSafely(context, Intent(action)) || startSafely(context, Intent(Settings.ACTION_SETTINGS))
}

private fun startSafely(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
