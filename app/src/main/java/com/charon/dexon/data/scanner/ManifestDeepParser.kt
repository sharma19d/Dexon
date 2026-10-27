package com.charon.dexon.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.charon.dexon.data.model.*
import net.dongliu.apk.parser.ApkFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.security.MessageDigest

class ManifestDeepParser(
    private val context: Context,
    private val trackerScanner: TrackerScanner
) {

    /**
     * Phase 1: everything PackageManager already knows. Takes milliseconds, so the detail
     * screen can render immediately while [completeReport] decodes the APK.
     */
    fun buildQuickReport(summary: AppAuditSummary, knownFramework: TechFramework?): AppDetailedReport {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.MATCH_DISABLED_COMPONENTS
        @Suppress("DEPRECATION")
        val pkgInfo = pm.getPackageInfo(summary.packageName, flags)
        val appInfo = requireNotNull(pkgInfo.applicationInfo) { "No ApplicationInfo for ${summary.packageName}" }

        val components = mutableListOf<ComponentItem>()
        pkgInfo.activities?.forEach {
            components.add(ComponentItem(it.name, ComponentType.ACTIVITY, it.exported, it.permission))
        }
        pkgInfo.services?.forEach {
            components.add(ComponentItem(it.name, ComponentType.SERVICE, it.exported, it.permission))
        }
        pkgInfo.receivers?.forEach {
            components.add(ComponentItem(it.name, ComponentType.RECEIVER, it.exported, it.permission))
        }
        pkgInfo.providers?.forEach {
            components.add(
                ComponentItem(
                    name = it.name,
                    type = ComponentType.PROVIDER,
                    isExported = it.exported,
                    permission = null,
                    readPermission = it.readPermission,
                    writePermission = it.writePermission,
                    authority = it.authority?.substringBefore(';')
                )
            )
        }

        val dangerousPerms = pkgInfo.requestedPermissions
            ?.filter { it in PackageScanner.HIGH_RISK_PERMISSIONS }
            .orEmpty()

        return AppDetailedReport(
            summary = knownFramework?.let { summary.copy(techFramework = it) } ?: summary,
            minSdk = appInfo.minSdkVersion,
            targetSdk = appInfo.targetSdkVersion,
            isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            allowBackup = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
            usesCleartextTraffic = (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
            signingSchemeSummary = "",
            dangerousPermissions = dangerousPerms,
            detectedTrackers = trackerScanner.detectTrackers(pkgInfo),
            nativeLibraries = emptyList(),
            components = components,
            deepLinks = emptyList(),
            rawManifestXml = "",
            isDeepScanComplete = false
        )
    }

    /** Phase 2: zip scan, binary manifest decode, deep links and signing certificate. Can take seconds. */
    fun completeReport(quick: AppDetailedReport): AppDetailedReport {
        val summary = quick.summary
        val zipScan = TechStackDetector.scanApkZip(summary.apkPath)

        var rawXml = ""
        var signingSummary = "Signature details unavailable"
        runCatching {
            ApkFile(File(summary.apkPath)).use { apk ->
                rawXml = apk.manifestXml ?: ""
                runCatching {
                    val v2Signers = apk.apkV2Singers
                    val v1Signers = apk.apkSingers
                    val cert = v2Signers.firstOrNull()?.certificateMetas?.firstOrNull()
                        ?: v1Signers.firstOrNull()?.certificateMetas?.firstOrNull()
                    val scheme = if (v2Signers.isNotEmpty()) "v2+" else if (v1Signers.isNotEmpty()) "v1 (JAR)" else "unknown"
                    if (cert != null) {
                        val sha256 = cert.data?.let { bytes ->
                            MessageDigest.getInstance("SHA-256").digest(bytes)
                                .joinToString(":") { "%02X".format(it) }
                        }
                        signingSummary = "Scheme: $scheme | Algo: ${cert.signAlgorithm} | " +
                                "SHA-256: ${sha256?.take(23) ?: "n/a"}..."
                    }
                }
            }
        }

        return quick.copy(
            summary = summary.copy(techFramework = zipScan.framework),
            signingSchemeSummary = signingSummary,
            nativeLibraries = zipScan.nativeLibs,
            deepLinks = extractDeepLinksFromXml(rawXml, summary.packageName),
            rawManifestXml = rawXml,
            isDeepScanComplete = true
        )
    }

    /**
     * Android merges every <data> element inside one <intent-filter>, so a filter declaring
     * schemes {http, https} and hosts {a.com, b.com} matches all four combinations.
     */
    private fun extractDeepLinksFromXml(xml: String, packageName: String): List<DeepLinkItem> {
        if (xml.isBlank()) return emptyList()
        val links = mutableListOf<DeepLinkItem>()
        runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))

            var currentActivity = ""
            val schemes = mutableListOf<String>()
            val hosts = mutableListOf<String>()
            val paths = mutableListOf<String>()

            fun attr(name: String): String? =
                parser.getAttributeValue(null, "android:$name") ?: parser.getAttributeValue(null, name)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "activity", "activity-alias" -> {
                            val name = attr("name").orEmpty()
                            currentActivity = if (name.startsWith(".")) packageName + name else name
                        }
                        "intent-filter" -> {
                            schemes.clear(); hosts.clear(); paths.clear()
                        }
                        "data" -> {
                            attr("scheme")?.takeIf { it.isNotBlank() }?.let(schemes::add)
                            attr("host")?.takeIf { it.isNotBlank() }?.let(hosts::add)
                            listOf("path", "pathPrefix", "pathPattern", "pathAdvancedPattern", "pathSuffix")
                                .firstNotNullOfOrNull { attr(it)?.takeIf(String::isNotBlank) }
                                ?.let(paths::add)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "intent-filter" -> if (currentActivity.isNotBlank()) {
                            for (scheme in schemes) {
                                for (host in hosts.ifEmpty { listOf("") }) {
                                    for (path in paths.ifEmpty { listOf("") }) {
                                        links.add(DeepLinkItem(scheme, host, path, currentActivity))
                                    }
                                }
                            }
                        }
                        "activity", "activity-alias" -> currentActivity = ""
                    }
                }
                eventType = parser.next()
            }
        }
        return links.distinct()
    }
}
