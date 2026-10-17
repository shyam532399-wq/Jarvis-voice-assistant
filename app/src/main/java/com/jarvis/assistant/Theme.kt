package com.jarvis.assistant

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val Mono = FontFamily.Monospace

object C {
    val Bg = Color(0xFF050B14)
    val Panel = Color(0xFF0C1626)
    val Navy = Color(0xFF0A1220)
    val NavyItem = Color(0xFF121E32)
    val Neon = Color(0xFF00E5FF)
    val Blue = Color(0xFF3D8BFF)
    val Text = Color(0xFFEAF3FF)
    val Dim = Color(0xFF8E98B3)
}

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Neon,
            background = C.Bg,
            surface = C.Panel,
            onPrimary = C.Bg,
            onBackground = C.Text,
            onSurface = C.Text
        ),
        content = {
            Surface(color = C.Bg, content = content)
        }
    )
}

@Composable
fun HudCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .background(C.Panel, RoundedCornerShape(14.dp))
            .border(1.dp, C.Blue.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SetupScreen() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(C.Panel, RoundedCornerShape(18.dp))
            .border(1.dp, C.Neon.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            androidx.compose.material3.Text("SETUP", color = C.Neon, fontFamily = Mono, fontSize = 16.sp)
            androidx.compose.material3.Text(
                "1. Allow microphone and phone permissions.\n2. Add Gemini API key in Settings.\n3. Add Picovoice access key.\n4. Enable accessibility service and launch the app.",
                color = C.Text,
                fontSize = 14.sp
            )
        }
    }
}
