package com.example.oneDmBot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.oneDmBot.db.AppDatabase
import com.example.oneDmBot.db.FilmEntity
import com.example.oneDmBot.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val settings = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()

    var films by remember { mutableStateOf<List<FilmEntity>>(emptyList()) }
    var paused by remember { mutableStateOf(settings.isPaused) }
    var filter by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { db.filmDao().observeAll().collectLatest { films = it } }

    val filtered = remember(films, filter) {
        if (filter == null) films else films.filter { it.status == filter }
    }
    val totalPages = ((filtered.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, totalPages - 1)
    val visible = filtered.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)

    val pending = films.count { it.status == FilmEntity.STATUS_PENDING }
    val downloading = films.count { it.status == FilmEntity.STATUS_DOWNLOADING }
    val done = films.count { it.status == FilmEntity.STATUS_DONE }
    val skipped = films.count { it.status == FilmEntity.STATUS_SKIPPED }
    val failed = films.count { it.status == FilmEntity.STATUS_FAILED }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Kuyruk", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = {
                        paused = !paused
                        settings.isPaused = paused
                    }) {
                        Icon(
                            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "Devam et" else "Duraklat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            StatusSummary(
                pending = pending,
                downloading = downloading,
                done = done,
                skipped = skipped,
                failed = failed,
                paused = paused
            )

            FilterRow(
                current = filter,
                onSelect = { filter = it; page = 0 }
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (films.isEmpty())
                            "Kuyrukta film yok. Kategoriler sekmesinden bir kategori ekleyin."
                        else "Bu filtrede film yok.",
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(visible, key = { it.id }) { film ->
                        FilmCard(
                            film = film,
                            onRetry = {
                                scope.launch(Dispatchers.IO) {
                                    db.filmDao().setStatus(film.id, FilmEntity.STATUS_PENDING)
                                }
                            },
                            onDelete = {
                                scope.launch(Dispatchers.IO) { db.filmDao().delete(film.id) }
                            }
                        )
                    }
                }
                if (totalPages > 1) {
                    PaginationBar(
                        page = currentPage,
                        totalPages = totalPages,
                        onPrev = { if (currentPage > 0) page = currentPage - 1 },
                        onNext = { if (currentPage < totalPages - 1) page = currentPage + 1 }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSummary(
    pending: Int, downloading: Int, done: Int, skipped: Int, failed: Int, paused: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (paused) Color(0xFFFFA000) else Color(0xFF34C759))
                )
                Text(
                    if (paused) "Duraklatıldı" else "Çalışıyor",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell("Bekliyor", pending)
                StatCell("İniyor", downloading)
                StatCell("Tamam", done)
                StatCell("Atlandı", skipped)
                StatCell("Hata", failed)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(current: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(selected = current == null, onClick = { onSelect(null) }, label = { Text("Tümü") })
        FilterChip(selected = current == FilmEntity.STATUS_PENDING, onClick = { onSelect(FilmEntity.STATUS_PENDING) }, label = { Text("Bekliyor") })
        FilterChip(selected = current == FilmEntity.STATUS_DONE, onClick = { onSelect(FilmEntity.STATUS_DONE) }, label = { Text("Tamam") })
        FilterChip(selected = current == FilmEntity.STATUS_FAILED, onClick = { onSelect(FilmEntity.STATUS_FAILED) }, label = { Text("Hata") })
        FilterChip(selected = current == FilmEntity.STATUS_SKIPPED, onClick = { onSelect(FilmEntity.STATUS_SKIPPED) }, label = { Text("Atlandı") })
    }
}

@Composable
private fun FilmCard(film: FilmEntity, onRetry: () -> Unit, onDelete: () -> Unit) {
    val (chipBg, chipFg, chipText) = statusVisual(film.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(film.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(film.filmUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(chipText, color = chipFg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                if (film.retries > 0) {
                    Text("deneme ${film.retries}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Box(modifier = Modifier.weight(1f))
                if (film.status == FilmEntity.STATUS_FAILED || film.status == FilmEntity.STATUS_SKIPPED) {
                    IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "Tekrar dene") }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Sil") }
            }
        }
    }
}

@Composable
private fun PaginationBar(page: Int, totalPages: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPrev, enabled = page > 0) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Önceki")
            }
            Text(
                "Sayfa ${page + 1} / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(onClick = onNext, enabled = page < totalPages - 1) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Sonraki")
            }
        }
    }
}

private data class StatusVisual(val bg: Color, val fg: Color, val text: String)

@Composable
private fun statusVisual(status: String): StatusVisual = when (status) {
    FilmEntity.STATUS_PENDING -> StatusVisual(Color(0xFFE3F2FD), Color(0xFF0D47A1), "Bekliyor")
    FilmEntity.STATUS_DOWNLOADING -> StatusVisual(Color(0xFFFFF3E0), Color(0xFFE65100), "İniyor")
    FilmEntity.STATUS_DONE -> StatusVisual(Color(0xFFE8F5E9), Color(0xFF1B5E20), "Tamamlandı")
    FilmEntity.STATUS_SKIPPED -> StatusVisual(Color(0xFFEDE7F6), Color(0xFF311B92), "Atlandı")
    FilmEntity.STATUS_FAILED -> StatusVisual(Color(0xFFFFEBEE), Color(0xFFB71C1C), "Başarısız")
    else -> StatusVisual(Color(0xFFEEEEEE), Color(0xFF424242), status)
}
