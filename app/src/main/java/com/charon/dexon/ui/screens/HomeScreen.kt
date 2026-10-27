package com.charon.dexon.ui.screens

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.charon.dexon.data.model.AppAuditSummary
import com.charon.dexon.data.model.PrivacyGrade
import com.charon.dexon.data.model.TechFramework
import com.charon.dexon.ui.AppFilter
import com.charon.dexon.ui.theme.RiskColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    apps: List<AppAuditSummary>,
    isLoading: Boolean,
    query: String,
    filter: AppFilter,
    bridgeActive: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onBridgeClick: () -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (AppAuditSummary) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dexon", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan apps")
                    }
                    IconButton(onClick = onBridgeClick) {
                        Icon(
                            if (bridgeActive) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Wi-Fi PC Bridge",
                            tint = if (bridgeActive) RiskColors.Emerald else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search apps or packages") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AppFilter.entries) { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { onFilterChange(f) },
                        label = { Text(f.label) }
                    )
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps match this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "${apps.size} apps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(apps, key = { it.packageName }) { app ->
                        AppCard(app, onClick = { onAppClick(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppAuditSummary, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TechBadge(app.techFramework)
                    if (app.isSystemApp) SmallBadge("System", MaterialTheme.colorScheme.secondary)
                    if (app.isDebuggable) SmallBadge("Debuggable", RiskColors.Crimson)
                }
            }
            Spacer(Modifier.width(8.dp))
            GradePill(app.privacyGrade)
        }
    }
}

@Composable
fun TechBadge(framework: TechFramework) {
    val text = if (framework == TechFramework.UNKNOWN) "Scanning…" else framework.label
    SmallBadge(text, MaterialTheme.colorScheme.primary)
}

@Composable
fun SmallBadge(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
fun GradePill(grade: PrivacyGrade, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(RiskColors.forGrade(grade)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            grade.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size > 48.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
        )
    }
}

private object IconCache {
    val cache = LruCache<String, ImageBitmap>(256)
}

@Composable
fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(IconCache.cache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(px, px).asImageBitmap()
                        .also { IconCache.cache.put(packageName, it) }
                }.getOrNull()
            }
        }
    }
    val current = bitmap
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
