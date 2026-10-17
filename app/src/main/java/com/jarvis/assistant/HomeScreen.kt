package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun HomeScreen(prefs: Prefs, onNeedSetup: () -> Unit, onNeedSettings: () -> Unit) {
    val ctx = LocalContext.current
    val running = JarvisState.running

    val inf = rememberInfiniteTransition(label = "hud")
    val rot by inf.animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rot"
    )
    val rot2 by inf.animateFloat(
        360f, 0f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rot2"
    )
    val pulse by inf.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val accent = if (running) C.Neon else C.Dim

    val toggle: () -> Unit = {
        if (JarvisState.running) {
            prefs.enabled = false
            JarvisService.stop(ctx)
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(ctx, "Pehle Setup me mic permission do", Toast.LENGTH_LONG).show()
            onNeedSetup()
        } else if (prefs.porcupineKey.isBlank() || prefs.geminiKey.isBlank()) {
            Toast.makeText(ctx, "Settings me Gemini key aur Porcupine key daalo", Toast.LENGTH_LONG).show()
            onNeedSettings()
        } else {
            prefs.enabled = true
            JarvisService.start(ctx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .clickable { toggle() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension
                val glow = if (running) 0.15f + 0.25f * pulse else 0.07f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = glow), Color.Transparent),
                        radius = w / 2f
                    ),
                    radius = w / 2f
                )
                val thick = Stroke(width = 7f, cap = StrokeCap.Round)
                val thin = Stroke(width = 3f, cap = StrokeCap.Round)
                val a = if (running) rot else 20f
                val b = if (running) rot2 else 60f
                for (i in 0 until 4) {
                    drawArc(
                        accent, a + i * 90f, 52f, false,
                        Offset(12f, 12f), Size(w - 24f, w - 24f), style = thick
                    )
                }
                for (i in 0 until 3) {
                    drawArc(
                        C.Blue, b + i * 120f, 80f, false,
                        Offset(46f, 46f), Size(w - 92f, w - 92f), style = thin
                    )
                }
                drawCircle(accent.copy(alpha = 0.5f), radius = w * 0.3f, style = thin)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.05f)),
                        radius = w * 0.22f
                    ),
                    radius = w * 0.22f
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (running) "ON" else "OFF",
                    color = accent,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = 4.sp
                )
                Text(
                    if (running) "TAP TO STOP" else "TAP TO START",
                    color = C.Text,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        val statusText = if (running) JarvisState.status.uppercase() else "OFFLINE - TAP RING TO START"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, accent, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                statusText,
                color = accent,
                fontFamily = Mono,
                fontSize = 15.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("WAKE WORD", "JARVIS", Modifier.weight(1f))
            InfoCard("FOLLOW-UP", prefs.followUpSeconds.toString() + " SEC", Modifier.weight(1f))
            InfoCard("REPLY", "HINGLISH", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        HudCard(Modifier.fillMaxWidth()) {
            Text("HEARD", color = C.Dim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                if (JarvisState.lastHeard.isBlank()) "-" else JarvisState.lastHeard,
                color = C.Text, fontFamily = Mono, fontSize = 14.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("SAID", color = C.Dim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                if (JarvisState.lastSaid.isBlank()) "-" else JarvisState.lastSaid,
                color = C.Neon, fontFamily = Mono, fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier) {
    HudCard(modifier) {
        Text(title, color = C.Dim, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = C.Neon, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
