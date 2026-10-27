package com.charon.dexon.data.server

import android.content.Context
import android.net.wifi.WifiManager
import com.charon.dexon.data.model.AppDetailedReport
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class WifiPcBridgeServer(private val context: Context) {

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    var activeReport: AppDetailedReport? = null

    val isRunning: Boolean get() = server != null

    fun startServer(port: Int = 8080): String {
        stopServer()
        val ipAddress = getLocalWifiIp()
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            routing {
                get("/") {
                    call.respondText(renderPage(activeReport), ContentType.Text.Html)
                }

                get("/download/apk") {
                    val report = activeReport ?: return@get call.respond(HttpStatusCode.NotFound, "No app selected")
                    val apkFile = File(report.summary.apkPath)
                    if (!apkFile.canRead()) return@get call.respond(HttpStatusCode.NotFound, "APK not readable")
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            "${report.summary.packageName}.apk"
                        ).toString()
                    )
                    call.respondFile(apkFile)
                }

                get("/download/manifest") {
                    val report = activeReport ?: return@get call.respond(HttpStatusCode.NotFound, "No app selected")
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            "${report.summary.packageName}_AndroidManifest.xml"
                        ).toString()
                    )
                    call.respondText(report.rawManifestXml, ContentType.Application.Xml.withCharset(Charsets.UTF_8))
                }
            }
        }.start(wait = false)

        return "http://$ipAddress:$port"
    }

    fun stopServer() {
        server?.stop(500, 1000)
        server = null
    }

    private fun renderPage(report: AppDetailedReport?): String {
        val body = if (report == null) {
            "<h2>Dexon Wi-Fi PC Bridge Active</h2>" +
                "<p>Select any app on your phone and tap <b>Send to PC via Wi-Fi</b>, then refresh this page.</p>"
        } else {
            val s = report.summary
            val size = String.format(Locale.US, "%.1f", s.apkSizeMb)
            val flags = listOf(
                "debuggable" to report.isDebuggable,
                "allowBackup" to report.allowBackup,
                "usesCleartextTraffic" to report.usesCleartextTraffic
            ).joinToString("") { (k, v) -> "<li>$k: <b>${if (v) "true" else "false"}</b></li>" }
            val trackers = report.detectedTrackers.ifEmpty { listOf("None detected") }
                .joinToString("") { "<li>${it.escapeHtml()}</li>" }
            """
            <h2>${s.appName.escapeHtml()} <small style="color:#999">(${s.packageName.escapeHtml()})</small></h2>
            <p>Version ${s.versionName.escapeHtml()} | Privacy Grade: <b>${s.privacyGrade}</b> (${s.privacyScore}/100) |
               Framework: <b>${s.techFramework.label}</b> | SDK ${report.minSdk}&ndash;${report.targetSdk}</p>
            <p><a href="/download/apk">&#11015; Download Base APK ($size MB)</a></p>
            <p><a href="/download/manifest">&#11015; Download Decoded AndroidManifest.xml</a></p>
            <h3>Manifest Flags</h3><ul>$flags</ul>
            <h3>Trackers</h3><ul>$trackers</ul>
            <p style="color:#999">Exported components: ${report.components.count { it.isExported }} |
               Deep links: ${report.deepLinks.size}</p>
            """.trimIndent()
        }
        return """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><title>Dexon PC Bridge</title>
            <style>body{background:#121212;color:#eee;font-family:sans-serif;padding:2rem;max-width:860px;margin:auto}
            a{color:#4da6ff;font-size:1.2rem}</style></head><body>$body</body></html>
        """.trimIndent()
    }

    private fun String.escapeHtml(): String = buildString(length) {
        for (c in this@escapeHtml) when (c) {
            '<' -> append("&lt;"); '>' -> append("&gt;"); '&' -> append("&amp;")
            '"' -> append("&quot;"); '\'' -> append("&#39;"); else -> append(c)
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocalWifiIp(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = runCatching { wm.connectionInfo.ipAddress }.getOrDefault(0)
        if (ip != 0) {
            return String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        }
        // Fallback for hotspot mode or when WifiManager withholds the address.
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
    }
}
