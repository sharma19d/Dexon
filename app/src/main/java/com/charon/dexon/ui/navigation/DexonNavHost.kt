package com.charon.dexon.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.charon.dexon.BuildConfig
import com.charon.dexon.data.model.AppAuditSummary
import com.charon.dexon.data.shizuku.ShizukuStatus
import com.charon.dexon.ui.DetailState
import com.charon.dexon.ui.MainViewModel
import com.charon.dexon.ui.TesterTarget
import com.charon.dexon.ui.screens.ActiveTestingSheet
import com.charon.dexon.ui.screens.AppDetailScreen
import com.charon.dexon.ui.screens.AuthorizationDialog
import com.charon.dexon.ui.screens.HomeScreen
import com.charon.dexon.ui.screens.LiveConnectionsScreen
import com.charon.dexon.ui.screens.SettingsScreen
import com.charon.dexon.ui.screens.SystemChecksScreen
import com.charon.dexon.ui.screens.ToolsScreen
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

private enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    APPS("apps", "Apps", Icons.AutoMirrored.Filled.List),
    TOOLS("tools", "Tools", Icons.Default.Build),
    SETTINGS("settings", "Settings", Icons.Default.Settings),
}

@Composable
fun DexonNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val bridge by viewModel.bridge.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val testerOutput by viewModel.testerOutput.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showBridgeDialog by remember { mutableStateOf(false) }
    var activeTarget by remember { mutableStateOf<TesterTarget?>(null) }
    var pendingGatedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Runs [action] if the authorized-use agreement is accepted, otherwise prompts for it first.
    fun gated(action: () -> Unit) {
        if (viewModel.isAuthorized) action() else pendingGatedAction = action
    }

    val shareApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* nothing to handle on return */ }

    var pendingSave by remember { mutableStateOf<AppAuditSummary?>(null) }
    val saveApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        val summary = pendingSave ?: return@rememberLauncherForActivityResult
        pendingSave = null
        if (uri != null) viewModel.saveApkTo(summary, uri)
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    // Bottom bar shows only on the three top-level destinations.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BottomTab.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.APPS.route,
            modifier = Modifier.padding(scaffoldPadding),
        ) {

            composable(BottomTab.APPS.route) {
                HomeScreen(
                    apps = apps,
                    isLoading = isLoading,
                    query = query,
                    filter = filter,
                    onQueryChange = viewModel::setQuery,
                    onFilterChange = viewModel::setFilter,
                    onRefresh = viewModel::refresh,
                    onAppClick = { summary ->
                        viewModel.loadDetail(summary.packageName)
                        navController.navigate("detail/${URLEncoder.encode(summary.packageName, "UTF-8")}")
                    },
                )
            }

            composable(BottomTab.TOOLS.route) {
                ToolsScreen(
                    bridgeUrl = bridge.url,
                    onSystemChecks = { gated { navController.navigate("checks") } },
                    onLiveConnections = { gated { navController.navigate("connections") } },
                    onToggleBridge = {
                        if (bridge.url == null) viewModel.toggleBridge()
                        showBridgeDialog = true
                    },
                )
            }

            composable(BottomTab.SETTINGS.route) {
                SettingsScreen(
                    isAuthorized = viewModel.isAuthorized,
                    shizukuRunning = viewModel.shizukuStatus().let {
                        it == ShizukuStatus.READY || it == ShizukuStatus.NEEDS_PERMISSION
                    },
                    versionName = BuildConfig.VERSION_NAME,
                    onRevokeAuthorization = { viewModel.revokeAuthorization() },
                )
            }

            composable(
                route = "detail/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { backStack ->
                val packageName = URLDecoder.decode(
                    backStack.arguments?.getString("packageName") ?: "", "UTF-8"
                )
                val readySummary = (detail as? DetailState.Ready)?.report?.summary

                AppDetailScreen(
                    state = detail,
                    testerOutput = testerOutput,
                    shizukuStatusProvider = viewModel::shizukuStatus,
                    isRootAvailable = viewModel.isRootAvailable,
                    onBack = { navController.popBackStack() },
                    onShare = {
                        val summary = readySummary ?: return@AppDetailScreen
                        scope.launch {
                            val intent = viewModel.buildShareApkIntent(summary)
                            if (intent != null) {
                                shareApkLauncher.launch(Intent.createChooser(intent, "Share APK — ${summary.appName}"))
                            }
                        }
                    },
                    onSendToPc = {
                        val rpt = (detail as? DetailState.Ready)?.report ?: return@AppDetailScreen
                        viewModel.sendToPc(rpt)
                        showBridgeDialog = true
                    },
                    onSave = {
                        val summary = readySummary ?: return@AppDetailScreen
                        pendingSave = summary
                        saveApkLauncher.launch("${summary.packageName}-${summary.versionName}.apk".replace('/', '_'))
                    },
                    onOpenScreen = { activity -> viewModel.launchScreen(packageName, activity) },
                    onActiveTest = { target -> gated { activeTarget = target } },
                    onLaunchStandard = { t, a, d, e -> viewModel.launchStandard(t, a, d, e) },
                    onLaunchShizuku = { t, a, d, e -> viewModel.launchViaShizuku(t, a, d, e) },
                    onLaunchRoot = { t, a, d, e -> viewModel.launchViaRoot(t, a, d, e) },
                    onBuildAdbCommand = { t, a, d, e -> viewModel.buildAdbCommand(t, a, d, e) },
                    onClearTesterOutput = viewModel::clearTesterOutput,
                )
            }

            composable("checks") {
                SystemChecksScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable("connections") {
                LiveConnectionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }

    // Authorized-use gate
    if (pendingGatedAction != null) {
        AuthorizationDialog(
            onAccept = {
                viewModel.acceptAuthorization()
                val action = pendingGatedAction
                pendingGatedAction = null
                action?.invoke()
            },
            onDecline = { pendingGatedAction = null },
        )
    }

    // Active testing sheet
    activeTarget?.let { target ->
        ActiveTestingSheet(viewModel = viewModel, target = target, onDismiss = { activeTarget = null })
    }

    // Wi-Fi PC Bridge dialog
    if (showBridgeDialog) {
        AlertDialog(
            onDismissRequest = { showBridgeDialog = false },
            title = { Text("Wi-Fi PC Bridge") },
            text = {
                Text(
                    bridge.error
                        ?: bridge.url?.let { url ->
                            val pkg = bridge.servingPackage
                            if (pkg != null)
                                "Serving ${apps.firstOrNull { it.packageName == pkg }?.appName ?: pkg}\n\nOpen in browser:\n$url"
                            else
                                "Open in a browser on the same Wi-Fi:\n$url"
                        }
                        ?: "Starting server…"
                )
            },
            confirmButton = { TextButton(onClick = { showBridgeDialog = false }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { viewModel.stopBridge(); showBridgeDialog = false }) { Text("Stop Server") }
            },
        )
    }
}
