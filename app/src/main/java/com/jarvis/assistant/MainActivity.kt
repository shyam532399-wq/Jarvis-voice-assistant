package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("HOME", Icons.Filled.Home),
    SETUP("SETUP", Icons.Filled.Build),
    GUIDE("GUIDE", Icons.Filled.Info),
    SETTINGS("SETTINGS", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        Notifier.clearRestartNotice(this)
        if (prefs.enabled && !JarvisState.running &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                JarvisService.start(this)
            } catch (_: Exception) {
            }
        }
        setContent {
            JarvisTheme {
                JarvisApp(prefs)
            }
        }
    }
}

@Composable
fun JarvisApp(prefs: Prefs) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val go: (Screen) -> Unit = { s ->
        screen = s
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = C.Navy) {
                DrawerHeader()
                Spacer(Modifier.height(12.dp))
                DrawerLabel("ASSISTANT")
                DrawerPill("Home", Screen.HOME.icon, screen == Screen.HOME) { go(Screen.HOME) }
                DrawerPill("Setup", Screen.SETUP.icon, screen == Screen.SETUP) { go(Screen.SETUP) }
                DrawerPill("Commands", Screen.GUIDE.icon, screen == Screen.GUIDE) { go(Screen.GUIDE) }
                Spacer(Modifier.height(10.dp))
                DrawerLabel("SYSTEM")
                DrawerPill("Settings", Screen.SETTINGS.icon, screen == Screen.SETTINGS) { go(Screen.SETTINGS) }
                Spacer(Modifier.weight(1f))
                Text(
                    "v1.0  ·  Jarvis Assistant",
                    color = Color(0xFF8E98B3),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = C.Bg,
            topBar = {
                HudTopBar(
                    onMenu = { scope.launch { drawerState.open() } },
                    onSettings = { go(Screen.SETTINGS) }
                )
            },
            bottomBar = { HudBottomBar(screen) { go(it) } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        prefs = prefs,
                        onNeedSetup = { screen = Screen.SETUP },
                        onNeedSettings = { screen = Screen.SETTINGS }
                    )
                    Screen.SETUP -> SetupScreen()
                    Screen.GUIDE -> GuideScreen()
                    Screen.SETTINGS -> SettingsScreen(prefs)
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0A1A33), Color(0xFF123A66))))
                .border(1.dp, C.Neon, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("J", color = C.Neon, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Jarvis", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Hinglish voice assistant", color = Color(0xFF8E98B3), fontSize = 14.sp)
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text,
        color = Color(0xFF8E98B3),
        fontSize = 14.sp,
        letterSpacing = 3.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, top = 6.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerPill(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    NavigationDrawerItem(
        label = { Text(label, fontSize = 18.sp) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        shape = shape,
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .border(1.dp, if (selected) C.Blue else Color(0xFF2A3350), shape),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFF2B3B73),
            unselectedContainerColor = C.NavyItem,
            selectedIconColor = Color(0xFF6C93FF),
            selectedTextColor = Color(0xFF6C93FF),
            unselectedIconColor = Color(0xFFB7C0D8),
            unselectedTextColor = Color.White
        )
    )
}

@Composable
private fun HudTopBar(onMenu: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = C.Neon)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .border(1.dp, C.Neon, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "JARVIS AI",
                color = C.Neon,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 5.sp
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = C.Neon)
        }
    }
}

@Composable
private fun HudBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Panel)
            .padding(vertical = 4.dp)
    ) {
        for (s in Screen.values()) {
            val color = if (s == current) C.Neon else C.Dim
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(s) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(s.icon, contentDescription = s.label, tint = color)
                Text(s.label, color = color, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
    }
}
