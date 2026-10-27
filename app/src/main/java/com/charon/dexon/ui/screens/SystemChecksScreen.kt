package com.charon.dexon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charon.dexon.data.model.Severity
import com.charon.dexon.data.model.SystemCheckResult
import com.charon.dexon.ui.MainViewModel
import com.charon.dexon.ui.theme.RiskColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemChecksScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val checks by viewModel.systemChecks.collectAsStateWithLifecycle()
    val running by viewModel.checksRunning.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadSystemChecksIfEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Checks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.runAllChecks() }, enabled = !running) {
                        if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PlayArrow, contentDescription = "Run all")
                    }
                },
            )
        },
    ) { padding ->
        if (checks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Read-only checks run through Shizuku's ADB shell. Tap ▶ to run them. " +
                        "Nothing here changes the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(checks, key = { it.check.id }) { CheckCard(it) }
        }
    }
}

@Composable
private fun CheckCard(result: SystemCheckResult) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(result)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.check.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        result.check.command,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SeverityTag(result.check.severity)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                result.check.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.rawOutput.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    result.rawOutput.trim().take(500),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(result: SystemCheckResult) = when (result.passed) {
    true -> Icon(Icons.Default.CheckCircle, contentDescription = "Pass", tint = RiskColors.Emerald, modifier = Modifier.size(22.dp))
    false -> Icon(Icons.Default.Error, contentDescription = "Flagged", tint = RiskColors.Crimson, modifier = Modifier.size(22.dp))
    null -> Icon(Icons.Default.HelpOutline, contentDescription = "Not run", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
}

@Composable
private fun SeverityTag(severity: Severity) {
    val color: Color = when (severity) {
        Severity.HIGH -> RiskColors.Crimson
        Severity.MEDIUM -> RiskColors.Amber
        Severity.LOW -> RiskColors.Teal
        Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(severity.name, style = MaterialTheme.typography.labelSmall, color = color)
}
