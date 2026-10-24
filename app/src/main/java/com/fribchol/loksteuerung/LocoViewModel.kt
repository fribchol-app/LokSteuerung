package com.fribchol.loksteuerung

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class Screen { START, CONTROL }

data class UiState(
    val screen: Screen = Screen.START,
    val connectingText: String? = null, // != null -> Startseite zeigt "Verbinde ..."
    val error: String? = null,
    val locoName: String = "",
    val isDemo: Boolean = false,
    val linkStatus: LinkStatus = LinkStatus.CLOSED,
    val target: Int = 0,          // Fahrregler: -32 (voll rückwärts) .. 0 .. +32 (voll vorwärts)
    val level: Int = 0,           // aktuelle Fahrstufe der Lok 0..32
    val forward: Boolean = true,
    val light: Boolean = false,
    val scaleKmh: Float = 0f,     // berechnetes Modell-Tempo
    val distanceM: Double = 0.0,  // tatsächlich gefahrene Meter auf der Anlage (berechnet)
    val driveMillis: Long = 0L,   // Zeit, in der sich die Lok bewegt hat
    val scale: Int = 87,          // Maßstab 1:87 (H0)
    val maxKmh: Int = 160,        // Modell-Tempo bei Stufe 32
    val foreignControl: Boolean = false, // ein anderes Gerät hat gerade einen Befehl geschickt
)

class LocoViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val DEFAULT_HOST = "192.168.4.1"
        private const val SEND_THROTTLE_MS = 150L
        private const val PREF_SCALE = "scale"
        private const val PREF_MAX_KMH = "maxKmh"
    }

    private val prefs = app.getSharedPreferences("lok_settings", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        UiState(
            scale = prefs.getInt(PREF_SCALE, 87),
            maxKmh = prefs.getInt(PREF_MAX_KMH, 160),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val wifi = LocoWifi(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket bleibt offen
        .build()

    private var link: LocoLink? = null
    private val linkJobs = mutableListOf<Job>()
    private var lastSendTime = 0L
    private var dragging = false
    private var foreignWarningJob: Job? = null

    // ---------------------------------------------------------------------------------------
    // Verbinden
    // ---------------------------------------------------------------------------------------

    fun startDemo() = openLink(DemoLink(viewModelScope), name = "Demo-Lok", demo = true)

    fun connectDirect(host: String = DEFAULT_HOST) {
        wifi.bindToCurrentWifi()
        openLink(EspLink(viewModelScope, http, host), name = "Lok ($host)", demo = false)
    }

    fun onQrScanned(raw: String) {
        when (val target = LocoQr.parse(raw)) {
            is LocoQr.Target.Wifi -> connectWifi(target.ssid, target.password)
            is LocoQr.Target.Host -> connectDirect(target.host)
            null -> showError("Dieser QR-Code gehört zu keiner Lok. Erwartet wird ein WLAN-QR-Code oder http://192.168.4.1")
        }
    }

    private fun connectWifi(ssid: String, password: String?) {
        _ui.update { it.copy(connectingText = "Verbinde mit dem WLAN „$ssid“ …", error = null) }
        wifi.connect(
            ssid = ssid,
            password = password,
            onReady = {
                viewModelScope.launch {
                    if (_ui.value.connectingText == null) return@launch // inzwischen abgebrochen
                    openLink(EspLink(viewModelScope, http, DEFAULT_HOST), name = prettyName(ssid), demo = false)
                }
            },
            onFailed = { message ->
                viewModelScope.launch { _ui.update { it.copy(connectingText = null, error = message) } }
            },
        )
    }

    fun cancelConnecting() {
        wifi.release()
        _ui.update { it.copy(connectingText = null) }
    }

    private fun openLink(newLink: LocoLink, name: String, demo: Boolean) {
        closeLink()
        link = newLink
        dragging = false
        _ui.update {
            it.copy(
                screen = Screen.CONTROL, locoName = name, isDemo = demo,
                error = null, connectingText = null, linkStatus = LinkStatus.CONNECTING,
                target = 0, level = 0, forward = true, light = false,
                scaleKmh = 0f, distanceM = 0.0, driveMillis = 0L, foreignControl = false,
            )
        }
        linkJobs += viewModelScope.launch {
            newLink.status.collect { status ->
                if (status == LinkStatus.OCCUPIED) onOccupied()
                else _ui.update { it.copy(linkStatus = status) }
            }
        }
        linkJobs += viewModelScope.launch {
            newLink.foreignCommands.collect { showForeignWarning() }
        }
        linkJobs += viewModelScope.launch {
            newLink.model.state.collect { state -> onModelState(state) }
        }
        linkJobs += viewModelScope.launch { runTripComputer() }
        newLink.open()
    }

    fun disconnect() {
        // Sicherheit: beim Verlassen die Lok sanft anhalten
        link?.send("=0")
        closeLink()
        wifi.release()
        _ui.update { it.copy(screen = Screen.START, linkStatus = LinkStatus.CLOSED, connectingText = null) }
    }

    /** Ein anderes Gerät ist schon im Lok-WLAN -> zurück zur Startseite mit Hinweis. */
    private fun onOccupied() {
        closeLink()
        wifi.release()
        _ui.update {
            it.copy(
                screen = Screen.START, linkStatus = LinkStatus.CLOSED, connectingText = null,
                error = "Diese Lok wird gerade schon von einem anderen Gerät gesteuert. Versuch es gleich nochmal.",
            )
        }
    }

    /** Warnung "Jemand anderes steuert" für ein paar Sekunden anzeigen. */
    private fun showForeignWarning() {
        _ui.update { it.copy(foreignControl = true) }
        foreignWarningJob?.cancel()
        foreignWarningJob = viewModelScope.launch {
            delay(6000)
            _ui.update { it.copy(foreignControl = false) }
        }
    }

    private fun closeLink() {
        linkJobs.forEach { it.cancel() }
        linkJobs.clear()
        foreignWarningJob?.cancel()
        foreignWarningJob = null
        link?.close()
        link = null
    }

    // ---------------------------------------------------------------------------------------
    // Fahren
    // ---------------------------------------------------------------------------------------

    /** Fahrregler bewegt. [final] = Finger losgelassen. */
    fun setTarget(value: Int, final: Boolean) {
        // Ohne Verbindung bleibt der Regler stehen – sonst würde er etwas anzeigen, was die Lok nicht tut
        if (link?.status?.value != LinkStatus.CONNECTED) {
            dragging = false
            return
        }
        dragging = !final
        val newTarget = value.coerceIn(-FirmwareModel.MAX_LEVEL, FirmwareModel.MAX_LEVEL)
        val changed = newTarget != _ui.value.target
        _ui.update { it.copy(target = newTarget) }

        // Beim Ziehen nicht jeden Pixel senden, aber beim Loslassen immer
        val now = SystemClock.elapsedRealtime()
        if (final || (changed && now - lastSendTime >= SEND_THROTTLE_MS)) {
            lastSendTime = now
            sendDriveCommand(newTarget)
        }
    }

    private fun sendDriveCommand(target: Int) {
        val activeLink = link ?: return
        val loco = activeLink.model.state.value
        val wantsForward = target > 0

        val command = when {
            target == 0 -> "=0"
            wantsForward == loco.forward -> "=${abs(target)}"
            // Richtungswechsel: erst sanft runter (+2 Reserve), dann umdrehen, dann wieder hoch.
            // Die Firmware arbeitet die Zeichenkette einfach der Reihe nach ab.
            else -> buildString {
                if (loco.level > 0) repeat(loco.level + 2) { append('-') }
                append('<')
                repeat(abs(target)) { append('+') }
            }
        }
        activeLink.send(command)
    }

    /** "Halten": Lok bremst Stufe für Stufe sanft bis zum Stillstand. */
    fun halt() {
        dragging = false
        _ui.update { it.copy(target = 0) }
        link?.send("=0")
    }

    /** "Stopp": Vollbremsung, Motor sofort aus. */
    fun emergencyStop() {
        dragging = false
        _ui.update { it.copy(target = 0) }
        link?.send("00")
    }

    fun toggleLight() {
        val activeLink = link ?: return
        // Ein neuer Befehl ersetzt in der Firmware die laufende Kette.
        // Damit ein laufendes Beschleunigen/Bremsen nicht abbricht, hängen wir den Rest wieder an.
        activeLink.send("L" + activeLink.model.pending)
    }

    // ---------------------------------------------------------------------------------------
    // Anzeige: Tempo & Strecke (berechnet, ohne Sensoren)
    // ---------------------------------------------------------------------------------------

    private fun onModelState(state: FirmwareModel.State) {
        _ui.update { ui ->
            val signedLevel = if (state.forward) state.level else -state.level
            // Hat sich die Lok "von außen" verändert (z. B. zweites Handy), Regler nachziehen
            val targetIsOff = (ui.target > 0) != (signedLevel > 0) && signedLevel != 0 ||
                    abs(ui.target - signedLevel) > 1
            val newTarget = if (!dragging && !state.busy && targetIsOff) signedLevel else ui.target
            ui.copy(level = state.level, forward = state.forward, light = state.light, target = newTarget)
        }
    }

    private suspend fun runTripComputer() {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(100)
            val now = SystemClock.elapsedRealtime()
            val dtMillis = now - last
            last = now
            _ui.update { ui ->
                // Modell-Tempo wächst linear mit der Fahrstufe (wie in der Firmware: Stufe x 5 km/h)
                val kmh = ui.level.toFloat() / FirmwareModel.MAX_LEVEL * ui.maxKmh
                // Echte Geschwindigkeit auf der Anlage = Modell-Tempo / Maßstab
                val realMetersPerSecond = kmh / 3.6 / ui.scale
                ui.copy(
                    scaleKmh = kmh,
                    distanceM = ui.distanceM + realMetersPerSecond * dtMillis / 1000.0,
                    driveMillis = ui.driveMillis + if (ui.level > 0) dtMillis else 0L,
                )
            }
        }
    }

    fun resetTrip() = _ui.update { it.copy(distanceM = 0.0, driveMillis = 0L) }

    fun saveSettings(scale: Int, maxKmh: Int) {
        prefs.edit().putInt(PREF_SCALE, scale).putInt(PREF_MAX_KMH, maxKmh).apply()
        _ui.update { it.copy(scale = scale, maxKmh = maxKmh) }
    }

    // ---------------------------------------------------------------------------------------

    fun showError(message: String) = _ui.update { it.copy(error = message) }
    fun clearError() = _ui.update { it.copy(error = null) }

    /** "_gruene_Dampflok_3915" -> "Gruene Dampflok 3915" */
    private fun prettyName(ssid: String) =
        ssid.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifEmpty { "Lok" }

    override fun onCleared() {
        closeLink()
        wifi.release()
        http.dispatcher.executorService.shutdown()
    }
}