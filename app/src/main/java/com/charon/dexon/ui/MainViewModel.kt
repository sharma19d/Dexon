package com.charon.dexon.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charon.dexon.data.model.AppAuditSummary
import com.charon.dexon.data.model.AppDetailedReport
import com.charon.dexon.data.model.ComponentType
import com.charon.dexon.data.model.TechFramework
import com.charon.dexon.data.scanner.ManifestDeepParser
import com.charon.dexon.data.scanner.PackageScanner
import com.charon.dexon.data.scanner.TechStackDetector
import com.charon.dexon.data.scanner.TrackerScanner
import com.charon.dexon.data.server.WifiPcBridgeServer
import com.charon.dexon.data.shizuku.IntentDispatcher
import com.charon.dexon.data.shizuku.ShizukuBridge
import com.charon.dexon.domain.AdbCommandBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppFilter(val label: String) {
    ALL("All"),
    USER("User Apps"),
    HIGH_RISK("Grade D/F (High Risk)"),
    FLUTTER("Flutter"),
    REACT_NATIVE("React Native"),
    DEBUGGABLE("Debuggable")
}

sealed interface DetailState {
    data object Idle : DetailState
    data object Loading : DetailState
    data class Ready(val report: AppDetailedReport) : DetailState
    data class Error(val message: String) : DetailState
}

data class BridgeState(val url: String? = null, val error: String? = null, val servingPackage: String? = null)

/** Everything the Component Tester sheet needs to target one component or deep link. */
data class TesterTarget(
    val packageName: String,
    val componentName: String,
    val type: ComponentType,
    val providerAuthority: String? = null,
    val presetAction: String = "",
    val presetDataUri: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val packageScanner = PackageScanner(app)
    private val trackerScanner = TrackerScanner(app)
    private val manifestParser = ManifestDeepParser(app, trackerScanner)
    private val shizukuBridge = ShizukuBridge(app)
    private val dispatcher = IntentDispatcher(app, shizukuBridge)
    private val bridgeServer = WifiPcBridgeServer(app)

    private val zipScanDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val _apps = MutableStateFlow<List<AppAuditSummary>>(emptyList())
    private val _frameworks = MutableStateFlow<Map<String, TechFramework>>(emptyMap())
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(AppFilter.ALL)
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    val visibleApps: StateFlow<List<AppAuditSummary>> =
        combine(_apps, _frameworks, _query, _filter) { apps, frameworks, query, filter ->
            val q = query.trim().lowercase()
            apps.asSequence()
                .map { s -> frameworks[s.packageName]?.let { s.copy(techFramework = it) } ?: s }
                .filter { s ->
                    when (filter) {
                        AppFilter.ALL -> true
                        AppFilter.USER -> !s.isSystemApp
                        AppFilter.HIGH_RISK -> s.privacyGrade >= com.charon.dexon.data.model.PrivacyGrade.D
                        AppFilter.FLUTTER -> s.techFramework == TechFramework.FLUTTER
                        AppFilter.REACT_NATIVE -> s.techFramework == TechFramework.REACT_NATIVE
                        AppFilter.DEBUGGABLE -> s.isDebuggable
                    }
                }
                .filter { s -> q.isEmpty() || s.appName.lowercase().contains(q) || s.packageName.contains(q) }
                .toList()
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow<DetailState>(DetailState.Idle)
    val detail: StateFlow<DetailState> = _detail.asStateFlow()
    private var detailJob: Job? = null

    private val _bridge = MutableStateFlow(BridgeState())
    val bridge: StateFlow<BridgeState> = _bridge.asStateFlow()

    private val _testerOutput = MutableStateFlow<String?>(null)
    val testerOutput: StateFlow<String?> = _testerOutput.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val isRootAvailable: Boolean by lazy { dispatcher.isRootAvailable() }

    /** Cheap binder ping; never requests permission, so it is safe to call while composing. */
    fun isShizukuRunning(): Boolean = shizukuBridge.isRunning()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val summaries = withContext(Dispatchers.IO) { packageScanner.getInstalledSummaries() }
            _apps.value = summaries
            _isLoading.value = false
            scanFrameworksLazily(summaries)
        }
    }

    private fun scanFrameworksLazily(summaries: List<AppAuditSummary>) {
        // User apps first: they are what people scroll to and filter on most.
        val pending = summaries.filter { it.packageName !in _frameworks.value }
            .sortedBy { it.isSystemApp }
        pending.forEach { summary ->
            viewModelScope.launch(zipScanDispatcher) {
                val framework = TechStackDetector.scanApkZip(summary.apkPath).framework
                _frameworks.update { it + (summary.packageName to framework) }
            }
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: AppFilter) { _filter.value = value }
    fun consumeMessage() { _message.value = null }

    fun loadDetail(packageName: String) {
        val current = _detail.value
        if (current is DetailState.Ready && current.report.summary.packageName == packageName) return
        detailJob?.cancel()
        _detail.value = DetailState.Loading
        detailJob = viewModelScope.launch {
            _detail.value = withContext(Dispatchers.IO) {
                runCatching {
                    val summary = _apps.value.firstOrNull { it.packageName == packageName }
                        ?: packageScanner.getSummary(packageName)
                        ?: error("Package $packageName is not installed.")
                    manifestParser.buildDetailedReport(summary)
                }.fold(
                    onSuccess = { report ->
                        _frameworks.update { it + (packageName to report.summary.techFramework) }
                        DetailState.Ready(report)
                    },
                    onFailure = { DetailState.Error(it.message ?: "Failed to audit $packageName") }
                )
            }
        }
    }

    // ---- Wi-Fi PC Bridge ----

    fun toggleBridge() {
        if (_bridge.value.url != null) stopBridge() else startBridge()
    }

    fun sendToPc(report: AppDetailedReport) {
        bridgeServer.activeReport = report
        _bridge.update { it.copy(servingPackage = report.summary.packageName) }
        if (_bridge.value.url == null) startBridge()
    }

    private fun startBridge() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { bridgeServer.startServer() }
            _bridge.update {
                it.copy(url = result.getOrNull(), error = result.exceptionOrNull()?.let { e -> "Server failed: ${e.message}" })
            }
        }
    }

    fun stopBridge() {
        viewModelScope.launch(Dispatchers.IO) {
            bridgeServer.stopServer()
            _bridge.value = BridgeState()
            bridgeServer.activeReport = null
        }
    }

    // ---- APK export ----

    /** Copies the base APK into the cache and returns a share Intent, or null on failure. */
    suspend fun buildShareApkIntent(summary: AppAuditSummary): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "apk_exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "${summary.packageName}-${summary.versionName}.apk".replace('/', '_'))
            File(summary.apkPath).copyTo(out, overwrite = true)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", out)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.onFailure { _message.value = "Export failed: ${it.message}" }.getOrNull()
    }

    fun saveApkTo(summary: AppAuditSummary, target: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(target).use { out ->
                    requireNotNull(out) { "Cannot open destination" }
                    File(summary.apkPath).inputStream().use { it.copyTo(out) }
                }
            }
            _message.value = result.fold({ "APK saved." }, { "Save failed: ${it.message}" })
        }
    }

    // ---- Component tester ----

    fun buildAdbCommand(t: TesterTarget, action: String, dataUri: String, extras: Map<String, String>): String =
        AdbCommandBuilder.buildCommand(t.packageName, t.componentName, t.type, action, dataUri, extras, t.providerAuthority)

    fun launchStandard(t: TesterTarget, action: String, dataUri: String, extras: Map<String, String>) {
        viewModelScope.launch {
            val result = withContext(if (t.type == ComponentType.PROVIDER) Dispatchers.IO else Dispatchers.Main) {
                dispatcher.launchStandardIntent(
                    t.packageName, t.componentName, t.type, action, dataUri, extras, t.providerAuthority
                )
            }
            _testerOutput.value = result.fold({ it }, { "Standard launch failed: ${it.javaClass.simpleName}: ${it.message}" })
        }
    }

    fun launchViaShizuku(t: TesterTarget, action: String, dataUri: String, extras: Map<String, String>) {
        val shell = AdbCommandBuilder.buildShellCommand(
            t.packageName, t.componentName, t.type, action, dataUri, extras, t.providerAuthority
        )
        viewModelScope.launch {
            _testerOutput.value = "Running via Shizuku…"
            val result = dispatcher.executeViaShizuku(shell)
            _testerOutput.value = result.fold({ it }, { "Shizuku: ${it.message}" })
        }
    }

    fun launchViaRoot(t: TesterTarget, action: String, dataUri: String, extras: Map<String, String>) {
        val shell = AdbCommandBuilder.buildShellCommand(
            t.packageName, t.componentName, t.type, action, dataUri, extras, t.providerAuthority
        )
        viewModelScope.launch {
            _testerOutput.value = "Running via su…"
            val result = withContext(Dispatchers.IO) { dispatcher.executeRootFallback(shell) }
            _testerOutput.value = result.fold({ it }, { "Root: ${it.message}" })
        }
    }

    fun launchScreen(packageName: String, activityName: String) {
        val result = dispatcher.launchStandardIntent(packageName, activityName, ComponentType.ACTIVITY, null, null, emptyMap())
        result.onFailure { _message.value = "Could not open screen: ${it.message}" }
    }

    fun clearTesterOutput() { _testerOutput.value = null }

    override fun onCleared() {
        bridgeServer.stopServer()
        shizukuBridge.release()
    }
}
