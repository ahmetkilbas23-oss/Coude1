package com.example.oneDmBot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.oneDmBot.ui.screens.CalibrationScreen
import com.example.oneDmBot.ui.screens.CategoriesScreen
import com.example.oneDmBot.ui.screens.QueueScreen
import com.example.oneDmBot.ui.screens.SettingsScreen
import com.example.oneDmBot.work.DailyCheckWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyCheckWorker.schedule(applicationContext)
        setContent {
            OneDmBotTheme { App() }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF06224A),
    secondary = Color(0xFF1976D2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE3FF),
    onSecondaryContainer = Color(0xFF062547),
    tertiary = Color(0xFF7B5BC2),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF101418),
    surface = Color.White,
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFEBEEF3),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF74777F),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AB0FF),
    onPrimary = Color(0xFF002B61),
    primaryContainer = Color(0xFF12407F),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFF9CC2FF),
    secondaryContainer = Color(0xFF1F4170),
    onSecondaryContainer = Color(0xFFD7E3FF),
    tertiary = Color(0xFFCEBDFF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE2E3E7),
    surface = Color(0xFF161A20),
    onSurface = Color(0xFFE2E3E7),
    surfaceVariant = Color(0xFF22262D),
    onSurfaceVariant = Color(0xFFC4C7CE),
    outline = Color(0xFF8C9099)
)

@Composable
private fun OneDmBotTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Tab(val title: String) {
    Categories("Kategoriler"),
    Queue("Kuyruk"),
    Calibration("Kalibrasyon"),
    Settings("Ayarlar")
}

@Composable
private fun App() {
    var current by remember { mutableStateOf(Tab.Categories) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { current = tab },
                        icon = {
                            val icon = when (tab) {
                                Tab.Categories -> Icons.Filled.List
                                Tab.Queue -> Icons.Filled.PlayArrow
                                Tab.Calibration -> Icons.Filled.Tune
                                Tab.Settings -> Icons.Filled.Settings
                            }
                            Icon(icon, contentDescription = tab.title)
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (current) {
            Tab.Categories -> CategoriesScreen(mod)
            Tab.Queue -> QueueScreen(mod)
            Tab.Calibration -> CalibrationScreen(mod)
            Tab.Settings -> SettingsScreen(mod)
        }
    }
}
