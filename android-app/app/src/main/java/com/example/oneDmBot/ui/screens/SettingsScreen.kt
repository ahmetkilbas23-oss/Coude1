package com.example.oneDmBot.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.oneDmBot.db.Settings as AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = remember { AppSettings(ctx) }
    var resolution by remember { mutableStateOf(s.preferredResolution) }
    var language by remember { mutableStateOf(s.preferredLanguage) }
    var pkg by remember { mutableStateOf(s.oneDmPackage) }
    var hour by remember { mutableStateOf(s.dailyCheckHour.toString()) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Ayarlar") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tercih edilen indirme seçeneği", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = resolution, onValueChange = { resolution = it; s.preferredResolution = it },
                label = { Text("Çözünürlük (örn. 1280)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = language, onValueChange = { language = it; s.preferredLanguage = it },
                label = { Text("Dil (örn. Türkçe)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pkg, onValueChange = { pkg = it; s.oneDmPackage = it },
                label = { Text("1DM paket adı (com.dv.adm veya com.dv.adm.pay)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = hour, onValueChange = {
                    hour = it
                    it.toIntOrNull()?.let { h -> if (h in 0..23) s.dailyCheckHour = h }
                },
                label = { Text("Günlük kontrol saati (0-23)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("İzinler", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Erişilebilirlik Hizmetini Aç") }
            Button(
                onClick = {
                    ctx.startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bildirim Erişimini Aç") }
        }
    }
}
