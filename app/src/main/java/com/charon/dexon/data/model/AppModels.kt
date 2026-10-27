package com.charon.dexon.data.model

enum class PrivacyGrade { A, B, C, D, F }

enum class TechFramework(val label: String) {
    FLUTTER("Flutter"),
    REACT_NATIVE("React Native"),
    UNITY("Unity"),
    CORDOVA("Cordova / Ionic"),
    KOTLIN_NATIVE("Kotlin / Java"),
    UNKNOWN("Native")
}

enum class ComponentType { ACTIVITY, SERVICE, RECEIVER, PROVIDER }

data class AppAuditSummary(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val apkPath: String,
    val apkSizeMb: Float,
    val privacyScore: Int,
    val privacyGrade: PrivacyGrade,
    val techFramework: TechFramework = TechFramework.UNKNOWN,
    val isDebuggable: Boolean = false
)

data class ComponentItem(
    val name: String,
    val type: ComponentType,
    val isExported: Boolean,
    val permission: String?,
    val readPermission: String? = null,
    val writePermission: String? = null,
    /** Provider authority (first entry when several are declared); null for non-providers. */
    val authority: String? = null
) {
    val isUnprotectedExported: Boolean
        get() = isExported && permission.isNullOrBlank() && readPermission.isNullOrBlank()
}

data class DeepLinkItem(
    val scheme: String,
    val host: String,
    val pathPattern: String,
    val activityName: String
) {
    val fullUriTemplate: String
        get() = buildString {
            append("$scheme://")
            if (host.isNotBlank()) append(host)
            if (pathPattern.isNotBlank()) {
                if (!pathPattern.startsWith("/")) append("/")
                append(pathPattern)
            }
        }
}

data class AppDetailedReport(
    val summary: AppAuditSummary,
    val minSdk: Int,
    val targetSdk: Int,
    val isDebuggable: Boolean,
    val allowBackup: Boolean,
    val usesCleartextTraffic: Boolean,
    val signingSchemeSummary: String,
    val dangerousPermissions: List<String>,
    val detectedTrackers: List<String>,
    val nativeLibraries: List<String>,
    val components: List<ComponentItem>,
    val deepLinks: List<DeepLinkItem>,
    val rawManifestXml: String
)
