package com.example.oneDmBot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.oneDmBot.db.Settings as AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = remember { AppSettings(ctx) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Kalibrasyon", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Aşağıdaki gri alanlar telefon ekranınızı temsil eder. 1DM'i ayrıca açıp ilgili butonun yerini görün, " +
                    "sonra burada tam o noktaya dokunun. Bot her filmde aynı koordinata otomatik dokunacak.",
                style = MaterialTheme.typography.bodyMedium
            )

            CalibrationZone(slot = AppSettings.Slot.PLAY, settings = s)
            CalibrationZone(slot = AppSettings.Slot.DOWNLOAD, settings = s)
        }
    }
}

@Composable
private fun CalibrationZone(slot: AppSettings.Slot, settings: AppSettings) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val (initialX, initialY) = settings.getCoord(slot)
    var lastX by remember { mutableStateOf(initialX) }
    var lastY by remember { mutableStateOf(initialY) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(slot.title, style = MaterialTheme.typography.titleMedium)
        Text(slot.hint, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (lastX > 0) "Kayıtlı: x=$lastX, y=$lastY" else "Henüz kalibre edilmedi",
            style = MaterialTheme.typography.bodySmall
        )

        val screenW = cfg.screenWidthDp
        val screenH = cfg.screenHeightDp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((screenH.coerceAtMost(360)).dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE0E0E0))
                .pointerInput(slot) {
                    detectTapGestures { offset ->
                        val px = (offset.x / size.width.toFloat() * with(density) { screenW.dp.toPx() }).toInt()
                        val py = (offset.y / size.height.toFloat() * with(density) { screenH.dp.toPx() }).toInt()
                        settings.setCoord(slot, px, py)
                        lastX = px
                        lastY = py
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (lastX > 0) "✓ x=$lastX, y=$lastY" else "Buraya dokunun",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(
            onClick = {
                settings.clearCoord(slot)
                lastX = -1
                lastY = -1
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sıfırla") }
    }
}
