package com.charon.dexon.data.scanner

import android.content.Context
import android.content.pm.PackageInfo
import org.json.JSONArray

class TrackerScanner(context: Context) {

    data class TrackerSignature(val name: String, val prefix: String)

    private val appContext = context.applicationContext

    private val signatures: List<TrackerSignature> by lazy {
        val list = mutableListOf<TrackerSignature>()
        runCatching {
            val jsonText = appContext.assets.open("trackers.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(TrackerSignature(obj.getString("name"), obj.getString("prefix")))
            }
        }
        list
    }

    fun detectTrackers(pkgInfo: PackageInfo): List<String> {
        val componentNames = buildList {
            pkgInfo.activities?.forEach { add(it.name) }
            pkgInfo.services?.forEach { add(it.name) }
            pkgInfo.receivers?.forEach { add(it.name) }
            pkgInfo.providers?.forEach { add(it.name) }
        }
        return signatures
            .filter { sig -> componentNames.any { it.startsWith(sig.prefix) } }
            .map { it.name }
            .distinct()
    }
}
