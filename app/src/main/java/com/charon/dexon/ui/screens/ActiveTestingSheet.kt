package com.charon.dexon.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charon.dexon.data.model.ComponentType
import com.charon.dexon.data.model.DeepLinkMutation
import com.charon.dexon.data.model.ProviderProbe
import com.charon.dexon.data.model.ProviderReport
import com.charon.dexon.data.model.Severity
import com.charon.dexon.ui.MainViewModel
import com.charon.dexon.ui.ProviderScanState
import com.charon.dexon.ui.TesterTarget
import com.charon.dexon.ui.theme.RiskColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTestingSheet(
    viewModel: MainViewModel,
    target: TesterTarget,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val providerScan by viewModel.providerScan.collectAsStateWithLifecycle()
    val mutations by viewModel.mutations.collectAsStateWithLifecycle()
    val mutationsLoading by viewModel.mutationsLoading.collectAsStateWithLifecycle()

    var deepLinkInput by remember { mutableStateOf(target.presetDataUri) }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearProviderScan()
            viewModel.clearMutations()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active testing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        target.componentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Content provider probing ----
            if (target.type == ComponentType.PROVIDER) {
                Text("Content provider probe", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Enumerates common paths and runs SQL-injection and path-traversal probes against " +
                        "${target.providerAuthority ?: "this provider"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.scanProvider(target) },
                    enabled = providerScan !is ProviderScanState.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan provider")
                }
                Spacer(Modifier.height(8.dp))
                when (val s = providerScan) {
                    is ProviderScanState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Probing…", style = MaterialTheme.typography.bodySmall)
                    }
                    is ProviderScanState.Error -> Text(
                        s.message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is ProviderScanState.Done -> ProviderResults(
                        report = s.report,
                        onReport = { probe ->
                            scope.launch {
                                val md = viewModel.bugReportForProvider(target, s.report, probe)
                                shareMarkdown(context, viewModel, md, "provider_${s.report.authority}.md", scope)
                            }
                        },
                    )
                    ProviderScanState.Idle -> {}
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }

            // ---- Deep-link mutation ----
            Text("Deep-link mutation", style = MaterialTheme.typography.titleSmall)
            Text(
                "Generates redirect and injection variants of a URI, then resolves where each would go.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = deepLinkInput,
                onValueChange = { deepLinkInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Template URI") },
                placeholder = { Text("myapp://open?url=https://example.com") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.mutateDeepLink(deepLinkInput, target.packageName) },
                enabled = deepLinkInput.isNotBlank() && !mutationsLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate & resolve")
            }
            Spacer(Modifier.height(8.dp))
            if (mutationsLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            mutations.forEach { MutationRow(it) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Bug report for the component itself ----
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val md = viewModel.bugReportForComponent(target)
                        if (md != null) {
                            shareMarkdown(context, viewModel, md,
                                "report_${target.componentName.substringAfterLast('.')}.md", scope)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate bug report")
            }
        }
    }
}

private fun shareMarkdown(
    context: android.content.Context,
    viewModel: MainViewModel,
    markdown: String,
    fileName: String,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    scope.launch {
        val intent = viewModel.buildBugReportShare(markdown, fileName) ?: return@launch
        context.startActivity(
            Intent.createChooser(intent, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun ProviderResults(report: ProviderReport, onReport: (ProviderProbe) -> Unit) {
    Text(
        "${report.probes.size} probes · worst: ${report.worst.name}",
        style = MaterialTheme.typography.labelMedium,
        color = severityColor(report.worst),
    )
    Spacer(Modifier.height(4.dp))
    report.probes.sortedByDescending { it.severity }.forEach { probe ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityDot(probe.severity)
                Spacer(Modifier.width(6.dp))
                Text(probe.payloadLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Text(probe.outcome.name, style = MaterialTheme.typography.labelSmall, color = severityColor(probe.severity))
                if (probe.severity >= Severity.MEDIUM) {
                    IconButton(onClick = { onReport(probe) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.BugReport, contentDescription = "Report", modifier = Modifier.size(15.dp))
                    }
                }
            }
            Text(
                probe.detail,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun MutationRow(m: DeepLinkMutation) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeverityDot(if (m.leavesApp) Severity.HIGH else Severity.INFO)
            Spacer(Modifier.width(6.dp))
            Text(m.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 2.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(m.uri, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), softWrap = false)
        }
        if (m.note.isNotBlank()) {
            Text(
                m.note,
                style = MaterialTheme.typography.labelSmall,
                color = if (m.leavesApp) RiskColors.Crimson else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun SeverityDot(severity: Severity) {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(severityColor(severity)))
}

@Composable
private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.HIGH -> RiskColors.Crimson
    Severity.MEDIUM -> RiskColors.Amber
    Severity.LOW -> RiskColors.Teal
    Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}
