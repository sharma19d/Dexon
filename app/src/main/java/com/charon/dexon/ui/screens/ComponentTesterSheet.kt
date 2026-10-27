package com.charon.dexon.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.charon.dexon.data.model.ComponentType
import com.charon.dexon.data.shizuku.ShizukuStatus
import com.charon.dexon.ui.TesterTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentTesterSheet(
    target: TesterTarget,
    testerOutput: String?,
    shizukuStatusProvider: () -> ShizukuStatus,
    isRootAvailable: Boolean,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismiss: () -> Unit,
    onLaunchStandard: (action: String, dataUri: String, extras: Map<String, String>) -> Unit,
    onLaunchShizuku: (action: String, dataUri: String, extras: Map<String, String>) -> Unit,
    onLaunchRoot: (action: String, dataUri: String, extras: Map<String, String>) -> Unit,
    onBuildAdbCommand: (action: String, dataUri: String, extras: Map<String, String>) -> String,
    onClearOutput: () -> Unit,
) {
    var action by remember { mutableStateOf(target.presetAction) }
    var dataUri by remember { mutableStateOf(target.presetDataUri) }
    val extras = remember { mutableStateListOf<Pair<String, String>>() }
    val clipboard = LocalClipboardManager.current
    var shizukuStatus by remember { mutableStateOf(shizukuStatusProvider()) }
    var showShizukuSetup by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { shizukuStatus = shizukuStatusProvider() }

    val adbCommand = remember(action, dataUri, extras.toList()) {
        onBuildAdbCommand(action, dataUri, extras.associate { it })
    }

    ModalBottomSheet(
        onDismissRequest = {
            onClearOutput()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = componentTypeLabel(target.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = target.componentName.substringAfterLast('.'),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = target.componentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onClearOutput(); onDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Content Providers only support query, skip action/extras inputs
            if (target.type != ComponentType.PROVIDER) {
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Action (optional)") },
                    placeholder = { Text("e.g. android.intent.action.VIEW") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = dataUri,
                onValueChange = { dataUri = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (target.type == ComponentType.PROVIDER)
                            "Content URI (content://…)"
                        else
                            "Data URI (optional)"
                    )
                },
                placeholder = {
                    Text(
                        if (target.type == ComponentType.PROVIDER)
                            "content://${target.providerAuthority ?: target.componentName}/"
                        else
                            "e.g. https://example.com"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
            )

            if (target.type != ComponentType.PROVIDER) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Intent Extras", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { extras.add("" to "") }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                extras.forEachIndexed { index, (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = k,
                            onValueChange = { extras[index] = it to v },
                            modifier = Modifier.weight(1f),
                            label = { Text("Key") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = v,
                            onValueChange = { extras[index] = k to it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Value") },
                            singleLine = true,
                        )
                        IconButton(onClick = { extras.removeAt(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove extra",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            // ADB command box
            Text("ADB Command", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = adbCommand,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                    )
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(adbCommand)) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy ADB command",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Launch buttons
            val extrasMap = extras.associate { it }
            Button(
                onClick = { onLaunchStandard(action, dataUri, extrasMap) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (target.type == ComponentType.PROVIDER) "Query Provider" else "Launch (Standard)")
            }

            // Always offered; if Shizuku is not ready the tap opens the setup guide instead.
            run {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        shizukuStatus = shizukuStatusProvider()
                        when (shizukuStatus) {
                            ShizukuStatus.READY, ShizukuStatus.NEEDS_PERMISSION ->
                                onLaunchShizuku(action, dataUri, extrasMap)
                            else -> showShizukuSetup = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (shizukuStatus) {
                            ShizukuStatus.READY -> "Run via Shizuku (ADB UID 2000)"
                            ShizukuStatus.NEEDS_PERMISSION -> "Run via Shizuku (allow access)"
                            ShizukuStatus.NOT_RUNNING -> "Run via Shizuku (start Shizuku)"
                            ShizukuStatus.NOT_INSTALLED -> "Run via Shizuku (set up)"
                        }
                    )
                }
            }

            if (showShizukuSetup) {
                ShizukuSetupDialog(
                    statusProvider = shizukuStatusProvider,
                    onDismiss = {
                        showShizukuSetup = false
                        shizukuStatus = shizukuStatusProvider()
                    },
                    onReady = {
                        showShizukuSetup = false
                        shizukuStatus = shizukuStatusProvider()
                        onLaunchShizuku(action, dataUri, extrasMap)
                    },
                )
            }

            if (isRootAvailable) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onLaunchRoot(action, dataUri, extrasMap) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run via Root (su)")
                }
            }

            // Output box
            AnimatedVisibility(visible = testerOutput != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Output", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = onClearOutput) { Text("Clear") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = testerOutput ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun componentTypeLabel(type: ComponentType): String = when (type) {
    ComponentType.ACTIVITY -> "Activity"
    ComponentType.SERVICE -> "Service"
    ComponentType.RECEIVER -> "Broadcast Receiver"
    ComponentType.PROVIDER -> "Content Provider"
}
