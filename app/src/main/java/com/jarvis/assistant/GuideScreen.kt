package com.jarvis.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tip(val title: String, val body: String)

private val tips = listOf(
    Tip("APP KHOLNA", "\"Jarvis\" ... \"WhatsApp kholo\""),
    Tip("CALL KARNA", "\"Jarvis\" ... \"Mummy ko call karo\""),
    Tip("VOLUME", "\"Volume badhao\", \"kam karo\", \"50 percent\", \"mute\""),
    Tip("TORCH", "\"Torch on karo\" / \"torch band karo\""),
    Tip("SCREEN", "\"Screen lock karo\", \"unlock karo\" (sirf Swipe/None lock par; PIN/pattern aap khud daaloge)"),
    Tip("NAVIGATION", "\"Home jao\", \"peeche jao\", \"recents kholo\""),
    Tip("INCOMING CALL", "Call aane par Jarvis naam bolega. Bolo \"uthao\" ya \"kaat do\". Call connect hote hi Android mic band kar deta hai, isliye voice sirf ringing ke time chalti hai."),
    Tip("SAWAAL", "Koi bhi sawaal pucho, Jarvis chhota Hinglish jawab dega. Jawab ke baad follow-up window me bina \"Jarvis\" bole bol sakte ho."),
    Tip("TIP", "Mic icon ya beep nahi aata. Agar status me \"Jarvis bolo...\" dikh raha hai to wake word sun raha hai.")
)

@Composable
fun GuideScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "COMMANDS", color = C.Neon, fontFamily = Mono, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, letterSpacing = 2.sp
            )
        }
        items(tips) { t ->
            HudCard(Modifier.fillMaxWidth()) {
                Text(t.title, color = C.Neon, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp)
                Text(t.body, color = C.Text, fontSize = 14.sp)
            }
        }
    }
}
