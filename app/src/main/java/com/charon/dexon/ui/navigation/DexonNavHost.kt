package com.charon.dexon.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.charon.dexon.data.model.AppAuditSummary
import com.charon.dexon.ui.DetailState
import com.charon.dexon.ui.MainViewModel
import com.charon.dexon.ui.screens.AppDetailScreen
import com.charon.dexon.ui.screens.HomeScreen
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun DexonNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val bridge by viewModel.bridge.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val testerOutput by viewModel.testerOutput.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showBridgeDialog by remember { mutableStateOf(false) }

    // APK share launcher
    val shareApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* nothing to handle on return */ }

    // APK save-to-disk launcher
    var pendingSave by remember { mutableStateOf<AppAuditSummary?>(null) }
    val saveApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        val summary = pendingSave ?: return@rememberLauncherForActivityResult
        pendingSave = null
        if (uri != null) viewModel.saveApkTo(summary, uri)
    }

    // One-shot status messages (export/save results, launch failures) shown on any screen
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    NavHost(navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                apps = apps,
                isLoading = isLoading,
                query = query,
                filter = filter,
                bridgeActive = bridge.url != null,
                onQueryChange = viewModel::setQuery,
                onFilterChange = viewModel::setFilter,
                onBridgeClick = {
                    if (bridge.url == null) viewModel.toggleBridge()
                    showBridgeDialog = true
                },
                onRefresh = viewModel::refresh,
                onAppClick = { summary ->
                    viewModel.loadDetail(summary.packageName)
                    val encoded = URLEncoder.encode(summary.packageName, "UTF-8")
                    navController.navigate("detail/$encoded")
                },
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
            // Re-checked whenever the tester runs something, so starting Shizuku mid-session is picked up.
            val shizukuRunning = remember(detail, testerOutput) { viewModel.isShizukuRunning() }

            AppDetailScreen(
                state = detail,
                testerOutput = testerOutput,
                isShizukuAvailable = shizukuRunning,
                isRootAvailable = viewModel.isRootAvailable,
                onBack = { navController.popBackStack() },
                onShare = {
                    val summary = readySummary ?: return@AppDetailScreen
                    scope.launch {
                        val intent = viewModel.buildShareApkIntent(summary)
                        if (intent != null) {
                            shareApkLauncher.launch(
                                Intent.createChooser(intent, "Share APK — ${summary.appName}")
                            )
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
                onLaunchStandard = { t, a, d, e -> viewModel.launchStandard(t, a, d, e) },
                onLaunchShizuku = { t, a, d, e -> viewModel.launchViaShizuku(t, a, d, e) },
                onLaunchRoot = { t, a, d, e -> viewModel.launchViaRoot(t, a, d, e) },
                onBuildAdbCommand = { t, a, d, e -> viewModel.buildAdbCommand(t, a, d, e) },
                onClearTesterOutput = viewModel::clearTesterOutput,
            )
        }
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
            confirmButton = {
                TextButton(onClick = { showBridgeDialog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.stopBridge(); showBridgeDialog = false }) {
                    Text("Stop Server")
                }
            },
        )
    }
}
