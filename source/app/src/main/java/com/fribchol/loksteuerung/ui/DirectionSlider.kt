package com.fribchol.loksteuerung.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fahrregler mit Mittelstellung: links = rückwärts, Mitte = 0, rechts = vorwärts.
 *
 * Sicherheits-Detail: Der Regler springt beim Antippen NICHT an die Fingerposition,
 * sondern wird relativ verschoben. Ein Tipp links/rechts vom Knopf ändert um genau eine Stufe.
 * Jede Stufe gibt ein leichtes Vibrieren, die Null-Stellung ein deutliches.
 */
@Composable
fun DirectionSlider(
    value: Int,
    maxValue: Int,
    onValueChange: (value: Int, final: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "slider",
    )
    val textMeasurer = rememberTextMeasurer()
    val thumbRadiusDp = 26.dp

    fun vibrateFor(newValue: Int, oldValue: Int) {
        val crossedZero = newValue == 0 || (oldValue != 0 && (newValue > 0) != (oldValue > 0))
        haptic.performHapticFeedback(
            if (crossedZero) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        )
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .pointerInput(maxValue) {
                var startValue = 0
                var accumulated = 0f
                var lastEmitted = 0
                detectHorizontalDragGestures(
                    onDragStart = {
                        startValue = currentValue
                        lastEmitted = currentValue
                        accumulated = 0f
                    },
                    onDragEnd = { currentOnChange(lastEmitted, true) },
                    onDragCancel = { currentOnChange(lastEmitted, true) },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        val usableWidth = (size.width - 2 * thumbRadiusDp.toPx()).coerceAtLeast(1f)
                        val pixelsPerStep = usableWidth / (2 * maxValue)
                        val newValue = (startValue + accumulated / pixelsPerStep)
                            .roundToInt()
                            .coerceIn(-maxValue, maxValue)
                        if (newValue != lastEmitted) {
                            vibrateFor(newValue, lastEmitted)
                            lastEmitted = newValue
                            currentOnChange(newValue, false)
                        }
                    },
                )
            }
            .pointerInput(maxValue) {
                detectTapGestures { tap ->
                    val radius = thumbRadiusDp.toPx()
                    val left = radius
                    val right = size.width - radius
                    val centerX = (left + right) / 2f
                    val thumbX = centerX + currentValue.toFloat() / maxValue * (right - left) / 2f
                    if (abs(tap.x - thumbX) < radius) return@detectTapGestures // Tipp auf den Knopf selbst
                    val step = if (tap.x > thumbX) 1 else -1
                    val newValue = (currentValue + step).coerceIn(-maxValue, maxValue)
                    if (newValue != currentValue) {
                        vibrateFor(newValue, currentValue)
                        currentOnChange(newValue, true)
                    }
                }
            }
    ) {
        val thumbRadius = thumbRadiusDp.toPx()
        val left = thumbRadius
        val right = size.width - thumbRadius
        val centerY = size.height / 2f
        val centerX = (left + right) / 2f
        val trackHeight = 14.dp.toPx()
        fun xOf(v: Float) = centerX + v / maxValue * (right - left) / 2f

        // Schiene
        drawRoundRect(
            color = LokColors.SurfaceHigh,
            topLeft = Offset(left - trackHeight / 2f, centerY - trackHeight / 2f),
            size = Size(right - left + trackHeight, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )

        // Rastermarken alle 4 Stufen
        val tickTop = centerY + trackHeight / 2f + 6.dp.toPx()
        for (i in -maxValue..maxValue step 4) {
            val x = xOf(i.toFloat())
            val length = if (i == 0) 10.dp.toPx() else 5.dp.toPx()
            drawLine(
                color = LokColors.Muted.copy(alpha = if (i == 0) 0.8f else 0.4f),
                start = Offset(x, tickTop), end = Offset(x, tickTop + length),
                strokeWidth = if (i == 0) 2.dp.toPx() else 1.dp.toPx(),
            )
        }

        // Füllung von der Mitte zum Knopf
        val thumbX = xOf(animatedValue)
        val accent = if (animatedValue >= 0f) LokColors.Forward else LokColors.Backward
        if (abs(thumbX - centerX) > 0.5f) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(min(centerX, thumbX), centerY - trackHeight / 2f),
                size = Size(abs(thumbX - centerX), trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
        }

        // Mittel-Kerbe
        drawLine(
            color = LokColors.Text.copy(alpha = 0.55f),
            start = Offset(centerX, centerY - trackHeight), end = Offset(centerX, centerY + trackHeight),
            strokeWidth = 2.dp.toPx(),
        )

        // Knopf
        val ringColor = if (value == 0) LokColors.Muted else accent
        if (value != 0) drawCircle(accent.copy(alpha = 0.22f), thumbRadius * 1.35f, Offset(thumbX, centerY))
        drawCircle(LokColors.Knob, thumbRadius, Offset(thumbX, centerY))
        drawCircle(ringColor, thumbRadius, Offset(thumbX, centerY), style = Stroke(3.dp.toPx()))

        val layout = textMeasurer.measure(
            abs(value).toString(),
            TextStyle(color = LokColors.KnobText, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        drawText(
            layout,
            topLeft = Offset(thumbX - layout.size.width / 2f, centerY - layout.size.height / 2f),
        )
    }
}
