package com.charon.dexon.data.shizuku

import com.charon.dexon.shizuku.IPrivilegedService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/** Instantiated by Shizuku inside a separate process running as the ADB shell user (UID 2000). */
class PrivilegedService : IPrivilegedService.Stub() {

    override fun executeShellCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exitCode = process.waitFor()
            "Exit Code: $exitCode\n$output".trim()
        } catch (e: Exception) {
            "Execution Error: ${e.message}"
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
