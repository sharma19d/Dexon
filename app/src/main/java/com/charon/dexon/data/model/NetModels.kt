package com.charon.dexon.data.model

/** One live socket read from /proc/net, attributed to the app that owns it. */
data class LiveConnection(
    val protocol: String,        // TCP, TCP6, UDP, UDP6
    val uid: Int,
    val appLabel: String,
    val packageName: String?,
    val remoteIp: String,
    val remotePort: Int,
    val state: String,           // ESTABLISHED, SYN_SENT, …
) {
    val remoteEndpoint: String get() = "$remoteIp:$remotePort"
}

/** Connections grouped under one app, for the Live Connections list. */
data class AppConnections(
    val appLabel: String,
    val packageName: String?,
    val uid: Int,
    val connections: List<LiveConnection>,
    val hostnames: Map<String, String> = emptyMap(),  // remoteIp -> resolved host
)
