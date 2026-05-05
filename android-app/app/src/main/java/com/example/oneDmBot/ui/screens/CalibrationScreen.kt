package com.example.oneDmBot.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.oneDmBot.db.Settings as AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = remember { AppSettings(ctx) }
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current

    var lastX by remember { mutableStateOf(s.playButtonX) }
    var lastY by remember { mutableStateOf(s.playButtonY) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Kalibrasyon") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "1DM tarayıcısında bir film sayfası açıkken oradaki play (▶) butonunun ekran üzerindeki konumu " +
                    "her cihazda aynıdır. Aşağıdaki gri alan, telefonunuzun ekranını temsil eder. " +
                    "1DM'i bir kez açıp play butonunun yerini görün, sonra burada o noktaya dokunun. " +
                    "Bot artık her filmde aynı koordinata otomatik dokunacak."
            )
            Text("Mevcut: x=$lastX, y=$lastY")

            // a proportional surrogate of the screen — 9:19 ratio
            val screenW = cfg.screenWidthDp
            val screenH = cfg.screenHeightDp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenH.coerceAtMost(560)).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE0E0E0))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val xPx = offset.x
                            val yPx = offset.y
                            // map dp ratio back to actual screen pixels via density
                            val px = (xPx / size.width.toFloat() * with(density) { screenW.dp.toPx() }).toInt()
                            val py = (yPx / size.height.toFloat() * with(density) { screenH.dp.toPx() }).toInt()
                            s.playButtonX = px
                            s.playButtonY = py
                            lastX = px
                            lastY = py
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (lastX > 0) "Kayıtlı: x=$lastX, y=$lastY"
                    else "1DM'deki play butonunun olduğu yere buraya dokunun"
                )
            }

            OutlinedButton(
                onClick = { s.playButtonX = -1; s.playButtonY = -1; lastX = -1; lastY = -1 },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sıfırla") }
        }
    }
}
