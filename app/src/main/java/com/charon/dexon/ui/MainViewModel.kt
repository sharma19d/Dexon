package com.charon.dexon.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.LruCache
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
import com.charon.dexon.data.consent.AuthorizationManager
import com.charon.dexon.data.model.AppConnections
import com.charon.dexon.data.net.ConnectionMonitor
import com.charon.dexon.data.model.DeepLinkMutation
import com.charon.dexon.data.model.ProviderProbe
import com.charon.dexon.data.model.ProviderReport
import com.charon.dexon.data.model.SystemCheckResult
import com.charon.dexon.data.pentest.BugReportBuilder
import com.charon.dexon.data.pentest.DeepLinkMutator
import com.charon.dexon.data.pentest.ProviderTester
import com.charon.dexon.data.pentest.SystemCheckRunner
import com.charon.dexon.data.server.WifiPcBridgeServer
import com.charon.dexon.data.shizuku.IntentDispatcher
import com.charon.dexon.data.shizuku.ShizukuBridge
import com.charon.dexon.data.shizuku.ShizukuStatus
import com.charon.dexon.domain.AdbCommandBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
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
import java.util.concurrent.ConcurrentHashMap

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

sealed interface ProviderScanState {
    data object Idle : ProviderScanState
    data object Running : ProviderScanState
    data class Done(val report: ProviderReport) : ProviderScanState
    data class Error(val message: String) : ProviderScanState
}

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
    private val authorization = AuthorizationManager(app)
    private val providerTester = ProviderTester(app)
    private val deepLinkMutator = DeepLinkMutator(app)
    private val systemCheckRunner = SystemCheckRunner(app, shizukuBridge)
    private val connectionMonitor = ConnectionMonitor(app, shizukuBridge)

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
    private var detailPackage: String? = null
    private var frameworkScanJob: Job? = null

    /** Fully decoded reports, so revisiting an app skips the slow APK decode. */
    private val reportCache = LruCache<String, AppDetailedReport>(REPORT_CACHE_SIZE)

    private val _bridge = MutableStateFlow(BridgeState())
    val bridge: StateFlow<BridgeState> = _bridge.asStateFlow()

    private val _testerOutput = MutableStateFlow<String?>(null)
    val testerOutput: StateFlow<String?> = _testerOutput.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---- Active testing (authorized-use gated) ----
    val isAuthorized: Boolean get() = authorization.hasAccepted
    fun acceptAuthorization() = authorization.accept()
    fun revokeAuthorization() { authorization.revoke() }

    private val _providerScan = MutableStateFlow<ProviderScanState>(ProviderScanState.Idle)
    val providerScan: StateFlow<ProviderScanState> = _providerScan.asStateFlow()

    private val _mutations = MutableStateFlow<List<DeepLinkMutation>>(emptyList())
    val mutations: StateFlow<List<DeepLinkMutation>> = _mutations.asStateFlow()
    private val _mutationsLoading = MutableStateFlow(false)
    val mutationsLoading: StateFlow<Boolean> = _mutationsLoading.asStateFlow()

    private val _systemChecks = MutableStateFlow<List<SystemCheckResult>>(emptyList())
    val systemChecks: StateFlow<List<SystemCheckResult>> = _systemChecks.asStateFlow()
    private val _checksRunning = MutableStateFlow(false)
    val checksRunning: StateFlow<Boolean> = _checksRunning.asStateFlow()

    fun scanProvider(target: TesterTarget) {
        val authority = target.providerAuthority ?: run {
            _message.value = "This provider declares no authority to test."
            return
        }
        _providerScan.value = ProviderScanState.Running
        viewModelScope.launch {
            val report = runCatching { providerTester.test(authority, target.componentName) }
            _providerScan.value = report.fold(
                { ProviderScanState.Done(it) },
                { ProviderScanState.Error(it.message ?: "Scan failed") },
            )
        }
    }

    fun clearProviderScan() { _providerScan.value = ProviderScanState.Idle }

    fun mutateDeepLink(templateUri: String, targetPackage: String) {
        if (templateUri.isBlank()) return
        _mutationsLoading.value = true
        viewModelScope.launch {
            _mutations.value = runCatching { deepLinkMutator.mutate(templateUri, targetPackage) }.getOrDefault(emptyList())
            _mutationsLoading.value = false
        }
    }

    fun clearMutations() { _mutations.value = emptyList() }

    fun loadSystemChecksIfEmpty() {
        if (_systemChecks.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val checks = systemCheckRunner.loadChecks()
            _systemChecks.value = checks.map { SystemCheckResult(it, passed = null, matched = false, rawOutput = "") }
        }
    }

    fun runAllChecks() {
        if (_checksRunning.value) return
        _checksRunning.value = true
        viewModelScope.launch {
            val current = _systemChecks.value.ifEmpty {
                withContext(Dispatchers.IO) { systemCheckRunner.loadChecks() }
                    .map { SystemCheckResult(it, null, false, "") }
            }
            val results = current.map { withContext(Dispatchers.IO) { systemCheckRunner.run(it.check) } }
            _systemChecks.value = results
            _checksRunning.value = false
        }
    }

    /** Markdown bug report for the selected component, or null if no detail report is loaded. */
    fun bugReportForComponent(target: TesterTarget): String? {
        val report = (_detail.value as? DetailState.Ready)?.report ?: return null
        val component = report.components.firstOrNull { it.name == target.componentName } ?: return null
        return BugReportBuilder.forExportedComponent(report, component).toMarkdown()
    }

    /** Markdown bug report for one provider probe finding. */
    fun bugReportForProvider(target: TesterTarget, provider: ProviderReport, probe: ProviderProbe): String {
        val report = (_detail.value as? DetailState.Ready)?.report
        return if (report != null) {
            BugReportBuilder.forProviderFinding(report, provider, probe).toMarkdown()
        } else {
            "# ${probe.payloadLabel}\n\n`${probe.uri}`\n\n${probe.detail}\n"
        }
    }

    // ---- Live connections ----
    private val _connections = MutableStateFlow<List<AppConnections>>(emptyList())
    val connections: StateFlow<List<AppConnections>> = _connections.asStateFlow()
    private val _connectionsError = MutableStateFlow<String?>(null)
    val connectionsError: StateFlow<String?> = _connectionsError.asStateFlow()

    fun refreshConnections() {
        viewModelScope.launch {
            val snap = connectionMonitor.snapshot()
            snap.fold(
                onSuccess = { list ->
                    _connectionsError.value = null
                    _connections.value = connectionMonitor.group(list)
                },
                onFailure = { _connectionsError.value = it.message ?: "Needs Shizuku to read connections." },
            )
        }
    }

    /** Builds a Markdown report and returns a share intent, or sets a message on failure. */
    suspend fun buildBugReportShare(markdown: String, fileName: String): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "reports").apply { mkdirs() }
            val out = File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            out.writeText(markdown)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", out)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, markdown)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.onFailure { _message.value = "Report failed: ${it.message}" }.getOrNull()
    }

    val isRootAvailable: Boolean by lazy { dispatcher.isRootAvailable() }

    /** Cheap binder ping plus package lookup; never requests permission or prompts the user. */
    fun shizukuStatus(): ShizukuStatus = shizukuBridge.status()

    init {
        refresh()
    }

    fun refresh() {
        reportCache.evictAll()
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
        frameworkScanJob?.cancel()
        frameworkScanJob = viewModelScope.launch {
            // Results land in a buffer and reach the UI in batches; publishing each of the
            // hundreds of results separately re-filtered and re-laid-out the list every time.
            val buffer = ConcurrentHashMap<String, TechFramework>()
            val flusher = launch {
                while (isActive) {
                    delay(FRAMEWORK_FLUSH_MS)
                    flushFrameworks(buffer)
                }
            }
            pending.map { summary ->
                launch(zipScanDispatcher) {
                    buffer[summary.packageName] = TechStackDetector.scanApkZip(summary.apkPath).framework
                }
            }.joinAll()
            flusher.cancel()
            flushFrameworks(buffer)
        }
    }

    private fun flushFrameworks(buffer: ConcurrentHashMap<String, TechFramework>) {
        if (buffer.isEmpty()) return
        val batch = HashMap(buffer)
        batch.keys.forEach(buffer::remove)
        _frameworks.update { it + batch }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: AppFilter) { _filter.value = value }
    fun consumeMessage() { _message.value = null }

    fun loadDetail(packageName: String) {
        if (packageName == detailPackage && _detail.value !is DetailState.Error) return
        detailJob?.cancel()
        detailPackage = packageName
        reportCache.get(packageName)?.let {
            _detail.value = DetailState.Ready(it)
            return
        }
        _detail.value = DetailState.Loading
        detailJob = viewModelScope.launch {
            // Phase 1: PackageManager data only, shown as soon as it is ready.
            val quick = withContext(Dispatchers.IO) {
                runCatching {
                    val summary = _apps.value.firstOrNull { it.packageName == packageName }
                        ?: packageScanner.getSummary(packageName)
                        ?: error("Package $packageName is not installed.")
                    manifestParser.buildQuickReport(summary, _frameworks.value[packageName])
                }
            }.getOrElse {
                _detail.value = DetailState.Error(it.message ?: "Failed to audit $packageName")
                return@launch
            }
            _detail.value = DetailState.Ready(quick)

            // Phase 2: decode the APK (manifest XML, deep links, signing, native libs).
            val full = withContext(Dispatchers.IO) {
                runCatching { manifestParser.completeReport(quick) }.getOrElse {
                    quick.copy(signingSchemeSummary = "Could not decode APK: ${it.message}", isDeepScanComplete = true)
                }
            }
            reportCache.put(packageName, full)
            _frameworks.update { it + (packageName to full.summary.techFramework) }
            if (_bridge.value.servingPackage == packageName) bridgeServer.activeReport = full
            _detail.value = DetailState.Ready(full)
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

    private companion object {
        const val FRAMEWORK_FLUSH_MS = 300L
        const val REPORT_CACHE_SIZE = 8
    }

    override fun onCleared() {
        bridgeServer.stopServer()
        shizukuBridge.release()
    }
}
