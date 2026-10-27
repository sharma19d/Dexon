package com.charon.dexon.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.charon.dexon.data.model.ComponentType
import java.io.File

class IntentDispatcher(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {

    // Tier 1: Works on all Android 8-16 devices with zero setup
    fun launchStandardIntent(
        packageName: String,
        componentName: String,
        type: ComponentType,
        action: String?,
        dataUri: String?,
        extras: Map<String, String>,
        providerAuthority: String? = null
    ): Result<String> = runCatching {
        if (type == ComponentType.PROVIDER) {
            return@runCatching queryProvider(dataUri?.ifBlank { null } ?: "content://$providerAuthority/")
        }
        val intent = Intent().apply {
            component = ComponentName(packageName, componentName)
            if (!action.isNullOrBlank()) this.action = action
            if (!dataUri.isNullOrBlank()) this.data = Uri.parse(dataUri)
            extras.forEach { (k, v) -> if (k.isNotBlank()) putExtra(k, v) }
        }
        when (type) {
            ComponentType.ACTIVITY -> {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Launched successfully via standard Android Intent."
            }
            ComponentType.SERVICE -> {
                val started = context.startService(intent)
                if (started != null) "Service start requested: ${started.flattenToShortString()}"
                else "Service could not be resolved."
            }
            ComponentType.RECEIVER -> {
                context.sendBroadcast(intent)
                "Broadcast sent."
            }
            ComponentType.PROVIDER -> error("unreachable")
        }
    }

    // Tier 2: ADB shell (UID 2000) via Shizuku
    suspend fun executeViaShizuku(rawShellCommand: String): Result<String> =
        shizukuBridge.execute(rawShellCommand)

    // Tier 3 Fallback: For older rooted Android 8-10 devices without Shizuku running
    fun executeRootFallback(rawShellCommand: String): Result<String> = runCatching {
        require(isRootAvailable()) { "Device is not rooted and Shizuku is not active." }
        val proc = ProcessBuilder("su", "-c", rawShellCommand).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().use { it.readText() }
        val exit = proc.waitFor()
        output.ifBlank { "Dispatched via Root (su). Exit code: $exit" }
    }

    fun isRootAvailable(): Boolean = SU_PATHS.any { File(it).exists() }

    private fun queryProvider(uri: String): String {
        context.contentResolver.query(Uri.parse(uri), null, null, null, null).use { cursor ->
            if (cursor == null) return "Provider returned no cursor."
            val columns = cursor.columnNames.joinToString(", ")
            val preview = buildString {
                var row = 0
                while (cursor.moveToNext() && row < 20) {
                    append("Row $row: ")
                    append((0 until cursor.columnCount).joinToString(", ") { i ->
                        "${cursor.getColumnName(i)}=${runCatching { cursor.getString(i) }.getOrDefault("<blob>")}"
                    })
                    append('\n')
                    row++
                }
            }
            return "Rows: ${cursor.count} | Columns: $columns\n$preview".trim()
        }
    }

    private companion object {
        val SU_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su"
        )
    }
}
