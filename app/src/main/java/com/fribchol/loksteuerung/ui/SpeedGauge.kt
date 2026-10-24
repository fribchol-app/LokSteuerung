package com.fribchol.loksteuerung.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f // unten links
private const val SWEEP_ANGLE = 270f // bis unten rechts

/**
 * Runder Tacho: farbiger Bogen = aktuelles Tempo, Messing-Punkt = eingestelltes Ziel-Tempo.
 */
@Composable
fun SpeedGauge(
    kmh: Float,
    targetKmh: Float,
    maxKmh: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val animatedKmh by animateFloatAsState(kmh, tween(300), label = "kmh")
    val animatedTarget by animateFloatAsState(targetKmh, tween(200), label = "targetKmh")
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = LokColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val max = maxKmh.coerceAtLeast(10)
            val stroke = size.minDimension * 0.055f
            val radius = size.minDimension / 2f - stroke * 1.6f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            fun angleOf(value: Float) = START_ANGLE + SWEEP_ANGLE * (value / max).coerceIn(0f, 1f)
            fun pointAt(angleDeg: Float, r: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return Offset(center.x + (cos(rad) * r).toFloat(), center.y + (sin(rad) * r).toFloat())
            }

            // Hintergrund-Bogen
            drawArc(
                color = LokColors.SurfaceHigh, startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            // Tempo-Bogen mit leichtem Leuchten
            val sweep = SWEEP_ANGLE * (animatedKmh / max).coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                drawArc(
                    color = accent.copy(alpha = 0.18f), startAngle = START_ANGLE, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke * 2.4f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent, startAngle = START_ANGLE, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawCircle(Color.White.copy(alpha = 0.9f), stroke * 0.28f, pointAt(START_ANGLE + sweep, radius))
            }

            // Skala
            val majorStep = when {
                max <= 60 -> 10
                max <= 200 -> 20
                else -> 50
            }
            val minorStep = majorStep / 2
            for (value in 0..max step minorStep) {
                val isMajor = value % majorStep == 0
                val angle = angleOf(value.toFloat())
                val outer = radius - stroke * 1.3f
                val inner = outer - if (isMajor) stroke * 1.0f else stroke * 0.5f
                drawLine(
                    color = if (isMajor) LokColors.Text.copy(alpha = 0.65f) else LokColors.Muted.copy(alpha = 0.45f),
                    start = pointAt(angle, inner), end = pointAt(angle, outer),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round,
                )
                if (isMajor) {
                    val layout = textMeasurer.measure(value.toString(), labelStyle)
                    val p = pointAt(angle, inner - stroke * 1.3f)
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                }
            }

            // Ziel-Markierung (Messing)
            if (animatedTarget > 0.5f) {
                val p = pointAt(angleOf(animatedTarget), radius)
                drawCircle(LokColors.Background, stroke * 0.62f, p)
                drawCircle(LokColors.Brass, stroke * 0.45f, p)
            }
        }

        // Digitale Anzeige in der Mitte
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedKmh.roundToInt().toString(),
                color = LokColors.Text,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text("km/h", color = LokColors.Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Modell-Tempo", color = LokColors.Muted.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}
