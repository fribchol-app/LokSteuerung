package com.fribchol.loksteuerung

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class LinkStatus { CONNECTING, CONNECTED, RECONNECTING, OCCUPIED, CLOSED }

/** Gemeinsame Schnittstelle für echte Lok und Demo-Lok. */
interface LocoLink {
    val model: FirmwareModel
    val status: StateFlow<LinkStatus>

    /** Meldet sich, wenn ein anderes Gerät einen Befehl an die Lok geschickt hat. */
    val foreignCommands: SharedFlow<Unit>

    fun open()
    fun send(command: String)
    fun close()
}

/** Demo-Lok: keine Hardware, das FirmwareModel spielt die Lok. */
class DemoLink(private val scope: CoroutineScope) : LocoLink {
    override val model = FirmwareModel(scope)
    private val _status = MutableStateFlow(LinkStatus.CONNECTING)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()
    override val foreignCommands: SharedFlow<Unit> = MutableSharedFlow<Unit>().asSharedFlow()

    override fun open() {
        model.start()
        scope.launch {
            delay(400) // kurzer "Verbinde..."-Moment, damit es sich echt anfühlt
            _status.value = LinkStatus.CONNECTED
        }
    }

    override fun send(command: String) = model.submit(command)

    override fun close() {
        model.stop()
        _status.value = LinkStatus.CLOSED
    }
}

/**
 * Echte Lok: WebSocket zu ws://<host>/ws, genau wie die index.html der PiedPiperS-Firmware.
 * Der ESP schickt JSON wie {"status":"Vorwärts &nbsp;...","speed":" > > >","command":"=12"}.
 *
 * "Nur einer steuert":
 *  1. Vor dem Verbinden fragt die App die Startseite /portal der Lok ab. Dort steht, wie viele
 *     Geräte im Lok-WLAN sind. Sind es mehr als eins (wir selbst + jemand anderes), ist die Lok
 *     besetzt -> Status OCCUPIED.
 *  2. Im Feld "command" schickt der ESP immer den zuletzt empfangenen Befehl mit. Steht dort etwas,
 *     das wir nicht geschickt haben, hat ein anderes Gerät gesteuert -> foreignCommands.
 */
class EspLink(
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val host: String,
) : LocoLink {
    override val model = FirmwareModel(scope)
    private val _status = MutableStateFlow(LinkStatus.CONNECTING)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()
    private val _foreignCommands = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val foreignCommands: SharedFlow<Unit> = _foreignCommands.asSharedFlow()

    private val checkClient = client.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()

    private var socket: WebSocket? = null
    private var closedByUser = false
    private var reconnectJob: Job? = null

    private var lastSent: String? = null
    private var ownEchoSeen = false

    override fun open() {
        closedByUser = false
        model.start()
        connect()
    }

    private fun connect() {
        checkOccupied { occupied ->
            if (closedByUser) return@checkOccupied
            if (occupied) {
                _status.value = LinkStatus.OCCUPIED
            } else {
                openSocket()
            }
        }
    }

    /** Fragt die Anzahl der WLAN-Nutzer ab. Klappt die Abfrage nicht, wird nicht blockiert. */
    private fun checkOccupied(onResult: (occupied: Boolean) -> Unit) {
        val request = Request.Builder().url("http://$host/portal").build()
        checkClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                scope.launch { onResult(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.use { it.body?.string().orEmpty() }
                val stations = Regex("""(\d+)\s*WLAN-Nutzer""")
                    .find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                scope.launch { onResult(stations > 1) }
            }
        })
    }

    private fun openSocket() {
        val request = Request.Builder().url("ws://$host/ws").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            // OkHttp ruft diese Methoden im Hintergrund auf -> per scope.launch auf den Main-Thread
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (webSocket !== socket) return@launch
                    _status.value = LinkStatus.CONNECTED
                    ownEchoSeen = false
                    if (webSocket.send("?")) lastSent = "?" // Firmware antwortet mit dem Status
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { if (webSocket === socket) handleMessage(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { onDropped(webSocket) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { onDropped(webSocket) }
            }
        })
    }

    private fun onDropped(webSocket: WebSocket) {
        if (webSocket !== socket || closedByUser) return
        socket = null
        _status.value = LinkStatus.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2000) // wie die Original-Webseite: alle 2 s neu versuchen
            if (!closedByUser) connect()
        }
    }

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return

        // Fremde Befehle erkennen (erst, wenn wir unser eigenes Echo einmal gesehen haben)
        val echoed = json.optString("command")
        val mine = lastSent
        if (echoed.isNotEmpty() && mine != null) {
            if (echoed == mine) ownEchoSeen = true
            else if (ownEchoSeen) _foreignCommands.tryEmit(Unit)
        }

        val statusText = json.optString("status").replace("&nbsp;", " ").trim()
        val arrows = json.optString("speed").count { it == '>' || it == '<' }
        val forward = when {
            statusText.startsWith("Vorw", ignoreCase = true) -> true
            statusText.startsWith("Rück", ignoreCase = true) -> false
            statusText.startsWith("Rueck", ignoreCase = true) -> false
            else -> null
        }
        model.correctFromEsp(arrows, forward)
    }

    override fun send(command: String) {
        val ws = socket ?: return
        if (_status.value != LinkStatus.CONNECTED) return
        // Nur wenn der Befehl wirklich rausging, rechnet auch das Modell damit
        if (ws.send(command)) {
            lastSent = command
            model.submit(command)
        }
    }

    override fun close() {
        closedByUser = true
        reconnectJob?.cancel()
        socket?.close(1000, "App beendet")
        socket = null
        model.stop()
        _status.value = LinkStatus.CLOSED
    }
}