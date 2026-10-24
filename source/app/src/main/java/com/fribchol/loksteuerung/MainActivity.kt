package com.fribchol.loksteuerung

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fribchol.loksteuerung.ui.ControlScreen
import com.fribchol.loksteuerung.ui.LokTheme
import com.fribchol.loksteuerung.ui.StartScreen
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {

    private val viewModel: LocoViewModel by viewModels()

    /** WLAN-QR-Code, der auf die Berechtigungs-Antwort wartet. */
    private var pendingWifiQr: String? = null

    private val wifiPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Auch wenn abgelehnt: wir versuchen es trotzdem – oft klappt es auch ohne.
            pendingWifiQr?.let(viewModel::onQrScanned)
            pendingWifiQr = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Bildschirm bleibt beim Fahren an
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            LokTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                Crossfade(targetState = ui.screen, label = "screen") { screen ->
                    when (screen) {
                        Screen.START -> StartScreen(
                            ui = ui,
                            onScan = ::startQrScan,
                            onDemo = viewModel::startDemo,
                            onDirect = { viewModel.connectDirect() },
                            onCancelConnecting = viewModel::cancelConnecting,
                            onDismissError = viewModel::clearError,
                        )

                        Screen.CONTROL -> {
                            BackHandler { viewModel.disconnect() }
                            ControlScreen(
                                ui = ui,
                                onTargetChange = viewModel::setTarget,
                                onHalt = viewModel::halt,
                                onStop = viewModel::emergencyStop,
                                onToggleLight = viewModel::toggleLight,
                                onResetTrip = viewModel::resetTrip,
                                onSaveSettings = viewModel::saveSettings,
                                onDisconnect = viewModel::disconnect,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startQrScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode -> handleQr(barcode.rawValue.orEmpty()) }
            .addOnFailureListener { e ->
                viewModel.showError("QR-Scanner konnte nicht gestartet werden: ${e.localizedMessage ?: "unbekannter Fehler"}")
            }
        // Abbrechen durch den Nutzer -> nichts tun
    }

    private fun handleQr(raw: String) {
        val needsWifi = LocoQr.parse(raw) is LocoQr.Target.Wifi
        val missing = wifiPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsWifi && missing.isNotEmpty()) {
            pendingWifiQr = raw
            wifiPermissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.onQrScanned(raw)
        }
    }

    private fun wifiPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
}
