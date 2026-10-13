package com.jarvis.assistant

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    var gem by remember { mutableStateOf(prefs.geminiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var pico by remember { mutableStateOf(prefs.porcupineKey) }
    var follow by remember { mutableFloatStateOf(prefs.followUpSeconds.toFloat()) }
    var show by remember { mutableStateOf(false) }
    val oldPico = remember { prefs.porcupineKey }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "SETTINGS", color = C.Neon, fontFamily = Mono, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, letterSpacing = 2.sp
        )

        HudField("Gemini API key", gem, !show) { gem = it }
        HudField("Gemini model", model, false) { model = it }
        HudField("Porcupine AccessKey", pico, !show) { pico = it }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = show,
                onCheckedChange = { show = it },
                colors = CheckboxDefaults.colors(checkedColor = C.Neon, checkmarkColor = C.Bg, uncheckedColor = C.Dim)
            )
            Text("Keys dikhao", color = C.Text, fontSize = 14.sp)
        }

        HudCard(Modifier.fillMaxWidth()) {
            Text(
                "FOLLOW-UP WINDOW: " + follow.toInt() + " SEC",
                color = C.Text, fontFamily = Mono, fontSize = 13.sp
            )
            Slider(
                value = follow,
                onValueChange = { follow = it },
                valueRange = 5f..120f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = C.Neon,
                    activeTrackColor = C.Neon,
                    inactiveTrackColor = C.Dim
                )
            )
        }

        Button(
            onClick = {
                prefs.geminiKey = gem
                prefs.model = model.trim().removePrefix("models/").ifBlank { Prefs.DEFAULT_MODEL }
                prefs.porcupineKey = pico
                prefs.followUpSeconds = follow.toInt()
                val msg = if (JarvisState.running && pico.trim() != oldPico) {
                    "Saved. Porcupine key ke liye Jarvis OFF karke phir ON karo."
                } else {
                    "Saved."
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Neon, contentColor = C.Bg)
        ) {
            Text("SAVE", fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }

        Text(
            "Keys sirf is phone me save hoti hain. Porcupine key picovoice.ai console se, Gemini key Google AI Studio se milti hai.",
            color = C.Dim, fontSize = 12.sp
        )
    }
}

@Composable
private fun HudField(label: String, value: String, secret: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Neon,
            unfocusedBorderColor = C.Dim,
            focusedTextColor = C.Text,
            unfocusedTextColor = C.Text,
            cursorColor = C.Neon,
            focusedLabelColor = C.Neon,
            unfocusedLabelColor = C.Dim
        )
    )
}
