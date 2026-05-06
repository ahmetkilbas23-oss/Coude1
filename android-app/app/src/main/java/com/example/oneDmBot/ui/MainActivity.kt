package com.example.oneDmBot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
            MaterialTheme { App() }
        }
    }
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
