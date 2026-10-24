package com.fribchol.loksteuerung

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bildet die Befehlsverarbeitung der PiedPiperS-Firmware (ESP32) in der App nach.
 *
 * So funktioniert die Firmware:
 *  - Jede WebSocket-Nachricht ERSETZT die aktuelle Befehlskette ("speed_command").
 *  - Die Kette wird Zeichen für Zeichen abgearbeitet, ca. alle 0,25 s eines:
 *      '+' = eine Fahrstufe schneller, '-' = eine Stufe langsamer,
 *      '0' = sofort Stufe 0 (Vollbremsung), '<' = Fahrtrichtung umdrehen, 'L' = Licht an/aus
 *  - "=N" ist ein Sonderfall: die Firmware rechnet daraus die passende Anzahl '+' oder '-'.
 *
 * Wozu das Ganze?
 *  - Im Demo-Modus IST dieses Modell die Lok.
 *  - Mit echter Lok läuft es parallel mit, damit die App jederzeit die aktuelle Fahrstufe kennt
 *    (der ESP meldet sie nur grob zurück). Daraus werden auch Tempo und Strecke berechnet.
 */
class FirmwareModel(
    private val scope: CoroutineScope,
    private val stepMillis: Long = 250L,
) {
    companion object {
        /** Höchste Fahrstufe der Firmware (max_speed_level). */
        const val MAX_LEVEL = 32

        /** Erste Zeichen, die die Firmware als Fahrbefehl akzeptiert. */
        private const val ACCEPTED_FIRST_CHARS = "=+-0?<L"

        // Die Firmware blinkt bei diesen Befehlen mit LEDs und wartet dabei (delay)
        private const val EXTRA_DELAY_FULL_BRAKE = 1000L
        private const val EXTRA_DELAY_REVERSE = 1600L
    }

    data class State(
        val level: Int = 0,          // 0 .. 32
        val forward: Boolean = true, // Fahrtrichtung
        val light: Boolean = false,
        val busy: Boolean = false,   // true, solange noch Befehle abgearbeitet werden
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // Alles läuft auf dem Main-Thread (viewModelScope), deshalb braucht die Queue keine Sperre.
    private val queue = StringBuilder()
    private var job: Job? = null

    /** Befehle, die noch nicht abgearbeitet sind. */
    val pending: String get() = queue.toString()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { processLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        queue.clear()
    }

    /** Nimmt einen Befehl genauso entgegen wie die Firmware (CLIENT_COMMAND). */
    fun submit(command: String) {
        if (command.isEmpty() || command[0] !in ACCEPTED_FIRST_CHARS) return

        queue.clear()
        if (command[0] == '=') {
            val target = command.substring(1).toIntOrNull()?.coerceIn(0, MAX_LEVEL) ?: return
            val difference = target - _state.value.level
            repeat(abs(difference)) { queue.append(if (difference > 0) '+' else '-') }
        } else {
            queue.append(command)
        }
        if (queue.isNotEmpty()) _state.value = _state.value.copy(busy = true)
    }

    /**
     * Abgleich mit der Rückmeldung des ESP.
     * Der ESP schickt die Fahrstufe nur als Pfeile (ein Pfeil = 2 Stufen), deshalb wird nur
     * korrigiert, wenn unser Wert sichtbar danebenliegt.
     */
    fun correctFromEsp(arrowCount: Int, forward: Boolean?) {
        val current = _state.value
        if (current.busy) return

        val level = if (current.level / 2 == arrowCount) current.level
        else (arrowCount * 2).coerceIn(0, MAX_LEVEL)
        val direction = forward ?: current.forward

        if (level != current.level || direction != current.forward) {
            _state.value = current.copy(level = level, forward = direction)
        }
    }

    private suspend fun processLoop() {
        while (scope.isActive) {
            if (queue.isEmpty()) {
                if (_state.value.busy) _state.value = _state.value.copy(busy = false)
                delay(30)
                continue
            }

            val command = queue[0]
            queue.deleteCharAt(0)

            val s = _state.value
            var extraDelay = 0L
            _state.value = when (command) {
                '0' -> { extraDelay = EXTRA_DELAY_FULL_BRAKE; s.copy(level = 0) }
                '-' -> s.copy(level = (s.level - 1).coerceAtLeast(0))
                '+' -> s.copy(level = (s.level + 1).coerceAtMost(MAX_LEVEL))
                '<' -> { extraDelay = EXTRA_DELAY_REVERSE; s.copy(forward = !s.forward) }
                'L' -> s.copy(light = !s.light)
                else -> s // z. B. '?' = nur Status abfragen
            }
            delay(stepMillis + extraDelay)
        }
    }
}
