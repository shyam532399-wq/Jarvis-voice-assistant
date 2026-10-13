package com.jarvis.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object JarvisState {
    var running by mutableStateOf(false)
    var status by mutableStateOf("Offline")
    var lastHeard by mutableStateOf("")
    var lastSaid by mutableStateOf("")
}
