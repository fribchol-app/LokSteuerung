package com.fribchol.loksteuerung.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fribchol.loksteuerung.UiState

@Composable
fun StartScreen(
    ui: UiState,
    onScan: () -> Unit,
    onDemo: () -> Unit,
    onDirect: () -> Unit,
    onCancelConnecting: () -> Unit,
    onDismissError: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E2633), LokColors.Background),
                    radius = 1400f,
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Lok-Symbol mit Messing-Schein
            Box(
                Modifier
                    .size(150.dp)
                    .background(
                        Brush.radialGradient(listOf(LokColors.Brass.copy(alpha = 0.30f), Color.Transparent)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Train, contentDescription = null, tint = LokColors.Brass, modifier = Modifier.size(80.dp))
            }

            Spacer(Modifier.height(20.dp))
            AppTitle()
            Spacer(Modifier.height(10.dp))
            Text(
                "Scanne den QR-Code deiner Lok –\ndie App verbindet sich dann mit ihrem WLAN.",
                color = LokColors.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(40.dp))

            if (ui.connectingText != null) {
                CircularProgressIndicator(color = LokColors.Brass)
                Spacer(Modifier.height(16.dp))
                Text(ui.connectingText, color = LokColors.Text, textAlign = TextAlign.Center)
                Text(
                    "Bestätige gleich den Android-Dialog „Mit Gerät verbinden“.",
                    color = LokColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancelConnecting) { Text("Abbrechen", color = LokColors.Muted) }
            } else {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LokColors.Brass, contentColor = LokColors.OnBrass),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("QR-Code scannen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDemo,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LokColors.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LokColors.Text),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ohne Lok ausprobieren", fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDirect) {
                    Text("Schon im Lok-WLAN? Direkt verbinden", color = LokColors.Muted, fontSize = 13.sp)
                }
            }

            ui.error?.let { message ->
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(LokColors.Stop.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = LokColors.Stop)
                    Spacer(Modifier.width(10.dp))
                    Text(message, color = LokColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissError) {
                        Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = LokColors.Muted)
                    }
                }
            }
        }
    }
}

/** App-Name zweifarbig: "Lok" in Messing, "steuerung" in Weiß – ein Wort, ohne Bindestrich. */
@Composable
private fun AppTitle() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = LokColors.Brass, fontWeight = FontWeight.ExtraBold)) {
                append("Lok")
            }
            withStyle(SpanStyle(color = LokColors.Text, fontWeight = FontWeight.Bold)) {
                append("steuerung")
            }
        },
        fontSize = 34.sp,
        letterSpacing = 0.5.sp,
    )
}