package com.fribchol.loksteuerung

import android.net.Uri

/**
 * Wertet den Inhalt eines gescannten QR-Codes aus.
 *
 * Unterstützt:
 *  - WLAN-QR-Codes:  WIFI:T:WPA;S:_gruene_Dampflok_3915;P:geheim;;   (oder T:nopass ohne P)
 *  - Adress-QR-Codes: http://192.168.4.1/   (so einer liegt im PiedPiperS-Repo bei)
 */
object LocoQr {

    sealed interface Target {
        data class Wifi(val ssid: String, val password: String?) : Target
        data class Host(val host: String) : Target
    }

    fun parse(raw: String): Target? {
        val text = raw.trim()
        if (text.startsWith("WIFI:", ignoreCase = true)) return parseWifi(text.substring(5))

        val isAddress = listOf("http://", "https://", "ws://").any { text.startsWith(it, ignoreCase = true) }
        if (isAddress) {
            val host = Uri.parse(text).host ?: return null
            return Target.Host(host)
        }
        return null
    }

    private fun parseWifi(body: String): Target? {
        // In Teile an nicht-maskierten ';' zerlegen (Sonderzeichen werden mit '\' maskiert)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (c in body) {
            when {
                escaped -> { current.append('\\').append(c); escaped = false }
                c == '\\' -> escaped = true
                c == ';' -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()

        var ssid: String? = null
        var password: String? = null
        var type = ""
        for (part in parts) {
            val index = part.indexOf(':')
            if (index <= 0) continue
            val value = unescape(part.substring(index + 1))
            when (part.substring(0, index).uppercase()) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> type = value.uppercase()
            }
        }

        if (ssid.isNullOrEmpty()) return null
        if (type == "NOPASS") password = null
        return Target.Wifi(ssid, password?.takeIf { it.isNotEmpty() })
    }

    private fun unescape(text: String) = buildString {
        var escaped = false
        for (c in text) {
            when {
                escaped -> { append(c); escaped = false }
                c == '\\' -> escaped = true
                else -> append(c)
            }
        }
    }
}
