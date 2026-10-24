package com.fribchol.loksteuerung.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fribchol.loksteuerung.FirmwareModel
import com.fribchol.loksteuerung.LinkStatus
import com.fribchol.loksteuerung.UiState
import java.util.Locale
import kotlin.math.abs

@Composable
fun ControlScreen(
    ui: UiState,
    onTargetChange: (Int, Boolean) -> Unit,
    onHalt: () -> Unit,
    onStop: () -> Unit,
    onToggleLight: () -> Unit,
    onResetTrip: () -> Unit,
    onSaveSettings: (Int, Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val accent = if (ui.forward) LokColors.Forward else LokColors.Backward
    val targetKmh = abs(ui.target).toFloat() / FirmwareModel.MAX_LEVEL * ui.maxKmh

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151B24), LokColors.Background)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TopBar(ui, onDisconnect, onToggleLight, onOpenSettings = { showSettings = true })

            AnimatedVisibility(visible = ui.foreignControl) {
                ForeignControlBanner()
            }

            // Tacho
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SpeedGauge(
                        kmh = ui.scaleKmh,
                        targetKmh = targetKmh,
                        maxKmh = ui.maxKmh,
                        accent = accent,
                        modifier = Modifier.fillMaxWidth(0.82f),
                    )
                    DirectionPill(level = ui.level, forward = ui.forward)
                }
            }

            TripCard(ui, onResetTrip)

            // Fahrregler
            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fahrregler", color = LokColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(targetLabel(ui.target), color = LokColors.Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    DirectionSlider(
                        value = ui.target,
                        maxValue = FirmwareModel.MAX_LEVEL,
                        onValueChange = onTargetChange,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("◀  Rückwärts", color = LokColors.Backward, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("Vorwärts  ▶", color = LokColors.Forward, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Bremsen
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigActionButton(
                    title = "HALTEN",
                    subtitle = "langsam anhalten",
                    icon = Icons.Rounded.PanTool,
                    container = LokColors.Brass,
                    content = LokColors.OnBrass,
                    onClick = onHalt,
                )
                BigActionButton(
                    title = "STOPP",
                    subtitle = "Vollbremsung",
                    icon = Icons.Rounded.Stop,
                    container = LokColors.Stop,
                    content = Color.White,
                    strongHaptic = true,
                    onClick = onStop,
                )
            }

            Text(
                "Tempo und Strecke werden aus der Fahrstufe berechnet (keine Sensoren). " +
                        "Über ⚙ kannst du Maßstab und Höchsttempo anpassen.",
                color = LokColors.Muted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            scale = ui.scale,
            maxKmh = ui.maxKmh,
            onDismiss = { showSettings = false },
            onSave = { scale, maxKmh ->
                onSaveSettings(scale, maxKmh)
                showSettings = false
            },
        )
    }
}

// -------------------------------------------------------------------------------------------
// Bausteine
// -------------------------------------------------------------------------------------------

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = LokColors.Surface,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LokColors.Outline.copy(alpha = 0.6f), RoundedCornerShape(26.dp)),
        content = content,
    )
}

@Composable
private fun TopBar(
    ui: UiState,
    onDisconnect: () -> Unit,
    onToggleLight: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDisconnect) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Trennen", tint = LokColors.Text)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                ui.locoName,
                color = LokColors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLine(ui)
        }
        LightButton(on = ui.light, onClick = onToggleLight)
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen", tint = LokColors.Muted)
        }
    }
}

/** Hinweis, wenn ein anderes Gerät der Lok gerade einen Befehl geschickt hat. */
@Composable
private fun ForeignControlBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(LokColors.Stop.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .border(1.dp, LokColors.Stop.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = LokColors.Stop)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Jemand anderes steuert diese Lok!", color = LokColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Ein zweites Gerät hat gerade einen Fahrbefehl geschickt.",
                color = LokColors.Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatusLine(ui: UiState) {
    val (color, text) = when {
        ui.isDemo -> LokColors.Brass to "Demo-Modus – ohne Lok"
        ui.linkStatus == LinkStatus.CONNECTED -> LokColors.Forward to "Verbunden"
        ui.linkStatus == LinkStatus.RECONNECTING -> LokColors.Stop to "Verbindung verloren – versuche erneut …"
        ui.linkStatus == LinkStatus.CONNECTING -> LokColors.Muted to "Verbinde …"
        else -> LokColors.Muted to "Getrennt"
    }
    val pulsing = ui.linkStatus != LinkStatus.CONNECTED
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (pulsing) pulseAlpha else 1f)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LightButton(on: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (on) LokColors.LightOn.copy(alpha = 0.18f) else Color.Transparent, label = "lightBg"
    )
    val tint by animateColorAsState(if (on) LokColors.LightOn else LokColors.Muted, label = "lightTint")
    Box(
        Modifier
            .size(44.dp)
            .background(background, CircleShape)
            .border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Lightbulb, contentDescription = if (on) "Licht aus" else "Licht an", tint = tint)
    }
}

@Composable
private fun DirectionPill(level: Int, forward: Boolean) {
    val (color, text) = when {
        level == 0 -> LokColors.Muted to "STEHT"
        forward -> LokColors.Forward to "VORWÄRTS"
        else -> LokColors.Backward to "RÜCKWÄRTS"
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level > 0) {
            Icon(
                if (forward) Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null, tint = color, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("  ·  Stufe $level/${FirmwareModel.MAX_LEVEL}", color = LokColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun TripCard(ui: UiState, onReset: () -> Unit) {
    Card {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fahrtenschreiber", color = LokColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onReset) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Zurücksetzen", tint = LokColors.Muted)
                }
            }
            Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Strecke", formatDistance(ui.distanceM), "auf der Anlage")
                StatTile("Im Maßstab", formatScaleKm(ui.distanceM * ui.scale / 1000.0), "1:${ui.scale}")
                StatTile("Fahrzeit", formatDuration(ui.driveMillis), "in Bewegung")
            }
        }
    }
}

@Composable
private fun RowScope.StatTile(label: String, value: String, hint: String) {
    Column(
        Modifier
            .weight(1f)
            .background(LokColors.SurfaceHigh, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(label, color = LokColors.Muted, fontSize = 11.sp)
        Text(
            value,
            color = LokColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(hint, color = LokColors.Muted.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun RowScope.BigActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    strongHaptic: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            if (strongHaptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.weight(1f).height(88.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Text(subtitle, fontSize = 12.sp, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SettingsDialog(
    scale: Int,
    maxKmh: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var scaleText by remember { mutableStateOf(scale.toString()) }
    var maxText by remember { mutableStateOf(maxKmh.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LokColors.Surface,
        title = { Text("Tempo-Berechnung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ohne Sensoren schätzt die App Tempo und Strecke aus der Fahrstufe.\n\n" +
                            "Kalibrieren: Lass die Lok auf voller Stufe eine bekannte Strecke fahren und stopp die Zeit. " +
                            "Modell-km/h = Meter ÷ Sekunden × 3,6 × Maßstab.",
                    fontSize = 13.sp,
                    color = LokColors.Muted,
                )
                OutlinedTextField(
                    value = scaleText,
                    onValueChange = { scaleText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Maßstab 1 : …") },
                    supportingText = { Text("H0 = 87 · TT = 120 · N = 160 · Spur 1 = 32") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Modell-Tempo bei voller Stufe (km/h)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newScale = scaleText.toIntOrNull()?.coerceIn(1, 999) ?: scale
                val newMax = maxText.toIntOrNull()?.coerceIn(10, 999) ?: maxKmh
                onSave(newScale, newMax)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// -------------------------------------------------------------------------------------------
// Formatierung
// -------------------------------------------------------------------------------------------

private fun targetLabel(target: Int) = when {
    target == 0 -> "Halt"
    target > 0 -> "Stufe $target vorwärts"
    else -> "Stufe ${-target} rückwärts"
}

private fun formatDistance(meters: Double) =
    if (meters < 1000) String.format(Locale.GERMANY, "%.1f m", meters)
    else String.format(Locale.GERMANY, "%.2f km", meters / 1000)

private fun formatScaleKm(km: Double) = String.format(Locale.GERMANY, "%.2f km", km)

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.GERMANY, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.GERMANY, "%02d:%02d", minutes, seconds)
}