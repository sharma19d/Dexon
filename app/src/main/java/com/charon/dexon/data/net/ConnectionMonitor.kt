package com.charon.dexon.data.net

import android.content.Context
import android.content.pm.PackageManager
import com.charon.dexon.data.model.AppConnections
import com.charon.dexon.data.model.LiveConnection
import com.charon.dexon.data.shizuku.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * Lightweight, VPN-free view of who each app is talking to right now. Reads the kernel's socket
 * tables under /proc/net through the Shizuku ADB shell, so it can see every app's connections and
 * their owning UID. No packet capture, no certificate, no decryption — connection metadata only.
 */
class ConnectionMonitor(
    context: Context,
    private val shizuku: ShizukuBridge,
) {

    private val pm = context.applicationContext.packageManager
    private val labelCache = HashMap<Int, Pair<String, String?>>()
    private val hostCache = HashMap<String, String>()

    /** Reads and parses a one-shot snapshot of active outbound connections. */
    suspend fun snapshot(): Result<List<LiveConnection>> = withContext(Dispatchers.IO) {
        // A marker line before each table lets us label the protocol correctly after catting.
        shizuku.execute("for f in tcp tcp6 udp udp6; do echo \"@@\$f\"; cat /proc/net/\$f 2>/dev/null; done")
            .map { raw ->
                var proto = "TCP"
                buildList {
                    for (line in raw.lineSequence()) {
                        if (line.startsWith("@@")) {
                            proto = line.removePrefix("@@").uppercase()
                            continue
                        }
                        parseLine(line, proto)?.let { add(it) }
                    }
                }.filter { it.remotePort != 0 && !it.remoteIp.startsWith("0.0.0.0") && it.remoteIp != "::" }
                    .distinctBy { "${it.uid}|${it.protocol}|${it.remoteEndpoint}" }
            }
    }

    /** Groups connections by owning app and attaches best-effort reverse-DNS hostnames. */
    suspend fun group(connections: List<LiveConnection>): List<AppConnections> = withContext(Dispatchers.IO) {
        val ips = connections.map { it.remoteIp }.distinct()
        val hosts = resolveHosts(ips)
        connections.groupBy { it.uid }
            .map { (uid, list) ->
                AppConnections(
                    appLabel = list.first().appLabel,
                    packageName = list.first().packageName,
                    uid = uid,
                    connections = list.sortedBy { it.remoteEndpoint },
                    hostnames = hosts.filterKeys { ip -> list.any { it.remoteIp == ip } },
                )
            }
            .sortedByDescending { it.connections.size }
    }

    private fun parseLine(line: String, proto: String): LiveConnection? {
        val f = line.trim().split(Regex("\\s+"))
        // sl local_address rem_address st … uid …
        if (f.size < 8 || !f[0].endsWith(":")) return null
        val remote = f[2]
        val state = STATES[f[3]] ?: f[3]
        val uid = f[7].toIntOrNull() ?: return null

        val (ip, port) = parseAddress(remote) ?: return null
        val (label, pkg) = labelFor(uid)
        return LiveConnection(proto, uid, label, pkg, ip, port, state)
    }

    /** hex "0100007F:1538" → "127.0.0.1" to 5432. Handles IPv4 (8 hex) and IPv6 (32 hex). */
    private fun parseAddress(field: String): Pair<String, Int>? {
        val parts = field.split(':')
        if (parts.size != 2) return null
        val hexIp = parts[0]
        val port = parts[1].toIntOrNull(16) ?: return null
        val ip = when (hexIp.length) {
            8 -> {
                val b = hexIp.chunked(2).map { it.toInt(16) }
                // /proc stores each 32-bit word little-endian
                "${b[3]}.${b[2]}.${b[1]}.${b[0]}"
            }
            32 -> {
                // 4 little-endian 32-bit words → 16 bytes; collapse to a readable v6 string
                val bytes = IntArray(16)
                for (w in 0 until 4) {
                    val word = hexIp.substring(w * 8, w * 8 + 8).chunked(2).map { it.toInt(16) }
                    bytes[w * 4 + 0] = word[3]
                    bytes[w * 4 + 1] = word[2]
                    bytes[w * 4 + 2] = word[1]
                    bytes[w * 4 + 3] = word[0]
                }
                runCatching {
                    InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray()).hostAddress
                }.getOrNull() ?: return null
            }
            else -> return null
        }
        return ip to port
    }

    private fun labelFor(uid: Int): Pair<String, String?> = labelCache.getOrPut(uid) {
        val pkgs = runCatching { pm.getPackagesForUid(uid) }.getOrNull()
        val pkg = pkgs?.firstOrNull()
        val label = when {
            pkg != null -> runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            uid == 0 -> "root"
            uid < 10000 -> "Android system (uid $uid)"
            else -> "uid $uid"
        }
        label to pkg
    }

    private suspend fun resolveHosts(ips: List<String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (ip in ips) {
            val cached = hostCache[ip]
            if (cached != null) {
                if (cached.isNotEmpty()) out[ip] = cached
                continue
            }
            val host = withTimeoutOrNull(800) {
                runCatching {
                    val addr = InetAddress.getByName(ip)
                    val name = addr.canonicalHostName
                    if (name == ip) "" else name
                }.getOrDefault("")
            } ?: ""
            hostCache[ip] = host
            if (host.isNotEmpty()) out[ip] = host
        }
        return out
    }

    private companion object {
        val STATES = mapOf(
            "01" to "ESTABLISHED", "02" to "SYN_SENT", "03" to "SYN_RECV",
            "04" to "FIN_WAIT1", "05" to "FIN_WAIT2", "06" to "TIME_WAIT",
            "07" to "CLOSE", "08" to "CLOSE_WAIT", "09" to "LAST_ACK",
            "0A" to "LISTEN", "0B" to "CLOSING",
        )
    }
}
