package com.example.oneDmBot.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.example.oneDmBot.db.Settings as AppSettings
import com.example.oneDmBot.template.TemplateStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = remember { AppSettings(ctx) }
    val store = remember { TemplateStore(ctx) }
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current

    var lastX by remember { mutableStateOf(s.playButtonX) }
    var lastY by remember { mutableStateOf(s.playButtonY) }
    var templateRev by remember { mutableStateOf(0) }
    val templateBitmap = remember(templateRev) { store.load(TemplateStore.SLOT_DOWNLOAD) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (store.saveFromUri(TemplateStore.SLOT_DOWNLOAD, uri)) {
                templateRev++
            }
        }
    }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Kalibrasyon") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("1) Play butonu konumu", style = MaterialTheme.typography.titleMedium)
            Text(
                "1DM tarayıcısında bir film sayfası açıkken oradaki play (▶) butonunun ekran üzerindeki konumu " +
                    "her cihazda aynıdır. Aşağıdaki gri alan, telefonunuzun ekranını temsil eder. " +
                    "1DM'i bir kez açıp play butonunun yerini görün, sonra burada o noktaya dokunun."
            )
            Text("Mevcut: x=$lastX, y=$lastY")

            val screenW = cfg.screenWidthDp
            val screenH = cfg.screenHeightDp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenH.coerceAtMost(420)).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE0E0E0))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val xPx = offset.x
                            val yPx = offset.y
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
            ) { Text("Play konumunu sıfırla") }

            Text("2) İndirme butonu görseli", style = MaterialTheme.typography.titleMedium)
            Text(
                "1DM'in sağ üstündeki indirme sayacı her cihazda farklı yerdedir, ama görüntüsü aynıdır. " +
                    "Telefon ekran görüntüsü alıp sadece o butonu kırpın (Galeri'de düzenle veya Samsung Smart Select), " +
                    "sonra aşağıdaki düğmeyle o görüntüyü bota tanıtın. Bot ekranda o görüntüyü bulup tıklayacak."
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (templateBitmap != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                bitmap = templateBitmap.asImageBitmap(),
                                contentDescription = "Kayıtlı indirme butonu görseli",
                                modifier = Modifier.size(72.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text("Görsel kayıtlı (${templateBitmap.width}×${templateBitmap.height} px)")
                        }
                    } else {
                        Text("Henüz görsel tanıtılmadı.")
                    }
                    Button(
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (templateBitmap == null) "Galeriden Görsel Seç" else "Görseli Değiştir")
                    }
                    if (templateBitmap != null) {
                        OutlinedButton(
                            onClick = { store.delete(TemplateStore.SLOT_DOWNLOAD); templateRev++ },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Görseli Sil") }
                    }
                }
            }
        }
    }
}
