package com.fribchol.loksteuerung

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verbindet das Handy mit dem WLAN-Access-Point der Lok.
 *
 * Android zeigt dafür einen System-Dialog ("Mit Gerät verbinden?"). Weil das Lok-WLAN
 * kein Internet hat, binden wir die App fest an dieses Netz – sonst würde Android die
 * Verbindung zu 192.168.4.1 evtl. über mobile Daten schicken.
 */
class LocoWifi(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun connect(
        ssid: String,
        password: String?,
        onReady: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        release()

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply { if (!password.isNullOrEmpty()) setWpa2Passphrase(password) }
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val delivered = AtomicBoolean(false)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.bindProcessToNetwork(network)
                if (delivered.compareAndSet(false, true)) onReady()
            }

            override fun onUnavailable() {
                if (delivered.compareAndSet(false, true)) {
                    onFailed("Keine Verbindung zum Lok-WLAN „$ssid“. Ist die Lok eingeschaltet und in der Nähe?")
                }
            }
        }
        callback = networkCallback

        try {
            connectivity.requestNetwork(request, networkCallback, 60_000)
        } catch (e: SecurityException) {
            callback = null
            onFailed("Die App darf sich nicht mit dem WLAN verbinden. Bitte die Berechtigung erlauben.")
        }
    }

    /**
     * Für QR-Codes ohne WLAN-Daten (z. B. nur "http://192.168.4.1"):
     * Das Handy ist hoffentlich schon im Lok-WLAN – dann binden wir die App an dieses WLAN.
     */
    @Suppress("DEPRECATION")
    fun bindToCurrentWifi(): Boolean {
        val wifiNetwork = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return false
        return connectivity.bindProcessToNetwork(wifiNetwork)
    }

    /** Gibt das Lok-WLAN wieder frei; Android verbindet sich dann wieder mit dem normalen WLAN. */
    fun release() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        connectivity.bindProcessToNetwork(null)
    }
}
