package com.charon.dexon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.charon.dexon.data.model.AppDetailedReport
import com.charon.dexon.data.model.ComponentItem
import com.charon.dexon.data.model.ComponentType
import com.charon.dexon.data.model.DeepLinkItem
import com.charon.dexon.data.model.PrivacyGrade
import com.charon.dexon.ui.DetailState
import com.charon.dexon.ui.MainViewModel
import com.charon.dexon.ui.TesterTarget
import com.charon.dexon.ui.theme.RiskColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    state: DetailState,
    testerOutput: String?,
    isShizukuAvailable: Boolean,
    isRootAvailable: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSendToPc: () -> Unit,
    onSave: () -> Unit,
    onOpenScreen: (String) -> Unit,
    onLaunchStandard: (TesterTarget, String, String, Map<String, String>) -> Unit,
    onLaunchShizuku: (TesterTarget, String, String, Map<String, String>) -> Unit,
    onLaunchRoot: (TesterTarget, String, String, Map<String, String>) -> Unit,
    onBuildAdbCommand: (TesterTarget, String, String, Map<String, String>) -> String,
    onClearTesterOutput: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var testerTarget by remember { mutableStateOf<TesterTarget?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (state) {
                        is DetailState.Ready -> Text(
                            state.report.summary.appName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        else -> Text("App Detail")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is DetailState.Ready) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share APK")
                        }
                        IconButton(onClick = onSendToPc) {
                            Icon(Icons.Default.Computer, contentDescription = "Send to PC via Wi-Fi")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->

        when (state) {
            is DetailState.Idle, is DetailState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is DetailState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Could not load audit", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is DetailState.Ready -> {
                val report = state.report
                Column(Modifier.padding(padding).fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Overview") },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Deep Audit") },
                        )
                    }
                    when (selectedTab) {
                        0 -> OverviewTab(report, onShare, onSave, onSendToPc, onOpenScreen)
                        1 -> DeepAuditTab(
                            report = report,
                            onComponentTap = { target -> testerTarget = target },
                        )
                    }
                }

                testerTarget?.let { target ->
                    ComponentTesterSheet(
                        target = target,
                        testerOutput = testerOutput,
                        isShizukuAvailable = isShizukuAvailable,
                        isRootAvailable = isRootAvailable,
                        sheetState = sheetState,
                        onDismiss = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { testerTarget = null }
                        },
                        onLaunchStandard = { a, d, e -> onLaunchStandard(target, a, d, e) },
                        onLaunchShizuku = { a, d, e -> onLaunchShizuku(target, a, d, e) },
                        onLaunchRoot = { a, d, e -> onLaunchRoot(target, a, d, e) },
                        onBuildAdbCommand = { a, d, e -> onBuildAdbCommand(target, a, d, e) },
                        onClearOutput = onClearTesterOutput,
                    )
                }
            }
        }
    }
}

// ─── Tab 1: Overview ────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    report: AppDetailedReport,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onSendToPc: () -> Unit,
    onOpenScreen: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Privacy grade card
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GradePill(report.summary.privacyGrade, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Privacy Score: ${report.summary.privacyScore}/100", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            privacyGradeDescription(report),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // App info row
        item {
            InfoCard("App Info") {
                InfoRow("Package", report.summary.packageName)
                InfoRow("Version", report.summary.versionName)
                InfoRow("SDK range", "min ${report.minSdk} — target ${report.targetSdk}")
                InfoRow("Framework", report.summary.techFramework.label)
                InfoRow("APK size", "%.1f MB".format(report.summary.apkSizeMb))
                InfoRow("Type", if (report.summary.isSystemApp) "System app" else "User app")
            }
        }

        // Trackers
        item {
            val trackers = report.detectedTrackers
            InfoCard(
                title = "Third-Party Trackers & Ads (${trackers.size})",
                indicatorColor = if (trackers.isEmpty()) RiskColors.Emerald else RiskColors.Crimson,
            ) {
                if (trackers.isEmpty()) {
                    Text(
                        "No known trackers detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RiskColors.Emerald,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                } else {
                    trackers.forEach { t ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = RiskColors.Crimson,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(t, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Native libraries
        if (report.nativeLibraries.isNotEmpty()) {
            item {
                ExpandableCard("Native Libraries (${report.nativeLibraries.size})") {
                    report.nativeLibraries.forEach { lib ->
                        Text(
                            lib,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Hidden screen launcher: exported activities any app may open without a permission
        val openableScreens = report.components.filter {
            it.type == ComponentType.ACTIVITY && it.isUnprotectedExported
        }
        if (openableScreens.isNotEmpty()) {
            item {
                ExpandableCard("Openable Screens (${openableScreens.size})") {
                    Text(
                        "Screens this app lets other apps open directly, including ones not in its menus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    openableScreens.forEach { screen ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenScreen(screen.name) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(screen.name.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    screen.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open screen",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // Action buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share / Export APK")
                }
                FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save APK to Storage")
                }
                FilledTonalButton(onClick = onSendToPc, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send to PC via Wi-Fi")
                }
            }
        }
    }
}

// ─── Tab 2: Deep Audit ──────────────────────────────────────────────────────

@Composable
private fun DeepAuditTab(report: AppDetailedReport, onComponentTap: (TesterTarget) -> Unit) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Security flags
        item {
            InfoCard("OWASP MASVS Security Flags") {
                SecurityFlagRow("Debuggable", report.isDebuggable, dangerIfTrue = true)
                SecurityFlagRow("Allow Backup", report.allowBackup, dangerIfTrue = true)
                SecurityFlagRow("Cleartext Traffic", report.usesCleartextTraffic, dangerIfTrue = true)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        report.signingSchemeSummary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Dangerous permissions
        if (report.dangerousPermissions.isNotEmpty()) {
            item {
                ExpandableCard(
                    title = "Dangerous Permissions (${report.dangerousPermissions.size})",
                    initiallyExpanded = true,
                ) {
                    report.dangerousPermissions.forEach { perm ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(13.dp),
                                tint = RiskColors.Amber)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                perm.removePrefix("android.permission."),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        // Component groups
        val grouped = report.components.groupBy { it.type }
        listOf(
            ComponentType.ACTIVITY to "Activities",
            ComponentType.SERVICE to "Services",
            ComponentType.RECEIVER to "Broadcast Receivers",
            ComponentType.PROVIDER to "Content Providers",
        ).forEach { (type, label) ->
            val list = grouped[type] ?: return@forEach
            item(key = label) {
                ComponentGroup(
                    title = "$label (${list.size})",
                    components = list,
                    onTap = { item ->
                        onComponentTap(
                            TesterTarget(
                                packageName = report.summary.packageName,
                                componentName = item.name,
                                type = item.type,
                                providerAuthority = item.authority,
                            )
                        )
                    },
                )
            }
        }

        // Deep links
        if (report.deepLinks.isNotEmpty()) {
            item {
                DeepLinkGroup(
                    deepLinks = report.deepLinks,
                    packageName = report.summary.packageName,
                    onTap = { link ->
                        onComponentTap(
                            TesterTarget(
                                packageName = report.summary.packageName,
                                componentName = link.activityName,
                                type = ComponentType.ACTIVITY,
                                presetDataUri = link.fullUriTemplate,
                            )
                        )
                    },
                )
            }
        }

        // Raw manifest
        item {
            ExpandableCard("Raw AndroidManifest.xml") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(report.rawManifestXml)) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy manifest",
                            modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = report.rawManifestXml,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ─── Component list ──────────────────────────────────────────────────────────

@Composable
private fun ComponentGroup(
    title: String,
    components: List<ComponentItem>,
    onTap: (ComponentItem) -> Unit,
) {
    ExpandableCard(title = title) {
        components.forEach { item ->
            val isUnprotected = item.isUnprotectedExported
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTap(item) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (item.isExported) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isUnprotected) RiskColors.Crimson
                    else if (item.isExported) RiskColors.Amber
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name.substringAfterLast('.'),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (item.isExported) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isUnprotected) {
                    Spacer(Modifier.width(6.dp))
                    SmallBadge("UNPROTECTED", RiskColors.Crimson)
                } else if (item.isExported) {
                    Spacer(Modifier.width(6.dp))
                    SmallBadge("EXPORTED", RiskColors.Amber)
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Test",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DeepLinkGroup(
    deepLinks: List<DeepLinkItem>,
    packageName: String,
    onTap: (DeepLinkItem) -> Unit,
) {
    ExpandableCard("Deep Links / URI Schemes (${deepLinks.size})") {
        deepLinks.forEach { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTap(link) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        link.fullUriTemplate,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        link.activityName.substringAfterLast('.'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Test deep link",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ─── Reusable small composables ─────────────────────────────────────────────

@Composable
private fun InfoCard(
    title: String,
    indicatorColor: Color? = null,
    content: @Composable () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (indicatorColor != null) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(indicatorColor))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SecurityFlagRow(label: String, value: Boolean, dangerIfTrue: Boolean) {
    val dangerous = dangerIfTrue && value
    Row(
        modifier = Modifier.padding(vertical = 3.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (dangerous) Icons.Default.Warning else Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (dangerous) RiskColors.Crimson else RiskColors.Emerald,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = if (dangerous) RiskColors.Crimson else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (dangerous) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private val PERMISSION_PLAIN_ENGLISH = mapOf(
    "android.permission.READ_CONTACTS" to "read your contacts",
    "android.permission.RECORD_AUDIO" to "use your microphone",
    "android.permission.CAMERA" to "use your camera",
    "android.permission.ACCESS_FINE_LOCATION" to "see your precise location",
    "android.permission.READ_SMS" to "read your SMS messages",
    "android.permission.READ_CALL_LOG" to "read your call history",
    "android.permission.SYSTEM_ALERT_WINDOW" to "draw over other apps",
    "android.permission.READ_EXTERNAL_STORAGE" to "read your photos and files",
    "android.permission.MANAGE_EXTERNAL_STORAGE" to "access all files on your phone"
)

private fun privacyGradeDescription(report: AppDetailedReport): String {
    val sentences = mutableListOf<String>()
    val abilities = report.dangerousPermissions.mapNotNull { PERMISSION_PLAIN_ENGLISH[it] }
    if (abilities.isNotEmpty()) sentences += "This app can ${joinNaturally(abilities)}."
    if (report.usesCleartextTraffic) sentences += "It is allowed to send data without encryption (plain HTTP)."
    if (report.allowBackup) sentences += "Its private data can be copied out via ADB backup."
    if (report.isDebuggable) sentences += "It was shipped in debug mode."
    if (report.detectedTrackers.isNotEmpty()) {
        val n = report.detectedTrackers.size
        sentences += "It contains $n tracking or ad SDK${if (n == 1) "" else "s"}."
    }
    if (sentences.isEmpty()) return "No sensitive permissions or risky settings found."
    val prefix = if (report.summary.privacyGrade >= PrivacyGrade.D) "Warning: " else ""
    return prefix + sentences.joinToString(" ")
}

private fun joinNaturally(items: List<String>): String = when (items.size) {
    1 -> items[0]
    2 -> "${items[0]} and ${items[1]}"
    else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
}
