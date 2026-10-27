package com.charon.dexon.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.charon.dexon.data.model.AppAuditSummary
import com.charon.dexon.data.model.PrivacyGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class PackageScanner(private val context: Context) {

    companion object {
        private const val LABEL_THREADS = 6

        val HIGH_RISK_PERMISSIONS = setOf(
            "android.permission.READ_CONTACTS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )

        fun scoreToGrade(score: Int): PrivacyGrade = when {
            score >= 85 -> PrivacyGrade.A
            score >= 70 -> PrivacyGrade.B
            score >= 55 -> PrivacyGrade.C
            score >= 40 -> PrivacyGrade.D
            else -> PrivacyGrade.F
        }
    }

    suspend fun getInstalledSummaries(): List<AppAuditSummary> = coroutineScope {
        val pm = context.packageManager
        val packages = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES)
        }.getOrElse {
            // Some devices with many packages overflow the binder buffer; retry without activities.
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        // Label loading opens each app's resources, the slowest part of the scan, so spread it across threads.
        val chunkSize = (packages.size / LABEL_THREADS + 1).coerceAtLeast(1)
        packages.chunked(chunkSize)
            .map { chunk ->
                async(Dispatchers.IO) {
                    chunk.mapNotNull { pkg -> runCatching { toSummary(pm, pkg) }.getOrNull() }
                }
            }
            .awaitAll()
            .flatten()
            .sortedBy { it.appName.lowercase() }
    }

    fun getSummary(packageName: String): AppAuditSummary? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkg = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES)
        toSummary(pm, pkg)
    }.getOrNull()

    private fun toSummary(pm: PackageManager, pkg: PackageInfo): AppAuditSummary? {
        val appInfo = pkg.applicationInfo ?: return null
        val appName = pm.getApplicationLabel(appInfo).toString()
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val allowBackup = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        val usesCleartext = (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0

        val requestedPerms = pkg.requestedPermissions?.toSet().orEmpty()
        val dangerousCount = requestedPerms.count { it in HIGH_RISK_PERMISSIONS }

        val exportedUnprotectedCount = pkg.activities?.count {
            it.exported && it.permission.isNullOrBlank()
        } ?: 0

        var score = 100
        if (isDebuggable) score -= 20
        if (usesCleartext) score -= 12
        if (allowBackup) score -= 5
        score -= (dangerousCount * 6)
        if (exportedUnprotectedCount > 1) {
            score -= ((exportedUnprotectedCount - 1) * 4).coerceAtMost(20)
        }
        val finalScore = score.coerceIn(0, 100)

        val apkFile = File(appInfo.sourceDir)
        val sizeMb = if (apkFile.exists()) apkFile.length() / (1024f * 1024f) else 0f

        return AppAuditSummary(
            appName = appName,
            packageName = pkg.packageName,
            versionName = pkg.versionName ?: "1.0",
            isSystemApp = isSystem,
            apkPath = appInfo.sourceDir,
            apkSizeMb = sizeMb,
            privacyScore = finalScore,
            privacyGrade = scoreToGrade(finalScore),
            isDebuggable = isDebuggable
        )
    }
}
