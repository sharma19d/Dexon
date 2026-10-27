package com.charon.dexon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One-time authorized-use agreement gating the active testing tools (provider probing, deep-link
 * mutation, system checks). Shown on first use of those tools only — never on app launch.
 */
@Composable
fun AuthorizationDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Default.Shield, contentDescription = null) },
        title = { Text("Authorized use only") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "The tools beyond this point actively probe other apps on this device — querying " +
                        "content providers, firing mutated deep links, and running system checks.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("By continuing you confirm that:", fontWeight = FontWeight.SemiBold)
                Text(
                    "• You will only test apps you own or are explicitly authorized to test.\n" +
                        "• You are responsible for complying with the laws and agreements that apply to you.\n" +
                        "• Dexon runs entirely on-device and sends nothing to any server.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Testing apps without permission may be illegal. This agreement can be revoked later " +
                        "from the testing tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("I am authorized") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancel") } },
    )
}
