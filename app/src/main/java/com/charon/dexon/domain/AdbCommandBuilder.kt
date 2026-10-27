package com.charon.dexon.domain

import com.charon.dexon.data.model.ComponentType

object AdbCommandBuilder {

    /**
     * Device-side shell command (what runs after `adb shell`, or directly via Shizuku / su).
     * Every argument is single-quoted so spaces and shell metacharacters reach `am` untouched.
     */
    fun buildShellCommand(
        packageName: String,
        componentName: String,
        type: ComponentType,
        action: String?,
        dataUri: String?,
        extras: Map<String, String>,
        providerAuthority: String? = null
    ): String = buildString {
        val target = quote("$packageName/$componentName")
        when (type) {
            ComponentType.ACTIVITY -> append("am start -n $target")
            ComponentType.SERVICE -> append("am startservice -n $target")
            ComponentType.RECEIVER -> append("am broadcast -n $target")
            ComponentType.PROVIDER -> {
                val uri = dataUri?.ifBlank { null } ?: "content://${providerAuthority ?: componentName}/"
                append("content query --uri ${quote(uri)}")
                return@buildString
            }
        }
        if (!action.isNullOrBlank()) append(" -a ${quote(action)}")
        if (!dataUri.isNullOrBlank()) append(" -d ${quote(dataUri)}")
        extras.forEach { (key, value) ->
            if (key.isNotBlank()) append(" --es ${quote(key)} ${quote(value)}")
        }
    }

    /** One-line host command, copy-paste ready for a POSIX terminal. */
    fun buildCommand(
        packageName: String,
        componentName: String,
        type: ComponentType,
        action: String?,
        dataUri: String?,
        extras: Map<String, String>,
        providerAuthority: String? = null
    ): String {
        val shell = buildShellCommand(packageName, componentName, type, action, dataUri, extras, providerAuthority)
        // Wrap in double quotes so adb forwards it as one string and the device shell parses the single quotes.
        val escaped = shell.replace(Regex("""([\\"$`])"""), """\\$1""")
        return "adb shell \"$escaped\""
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
