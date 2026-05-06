package com.example.oneDmBot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.oneDmBot.db.AppDatabase
import com.example.oneDmBot.db.FilmEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    var films by remember { mutableStateOf<List<FilmEntity>>(emptyList()) }

    LaunchedEffect(Unit) { db.filmDao().observeAll().collectLatest { films = it } }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Kuyruk") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (films.isEmpty()) {
                item {
                    Text(
                        "Kuyrukta film yok. Kategoriler sekmesinden bir kategori eklediğinizde " +
                            "filmler burada görünür.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(films, key = { it.id }) { f ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall)
                        Text(f.filmUrl, style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(statusLabel(f.status)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = statusColor(f.status)
                                )
                            )
                            if (f.retries > 0) AssistChip(onClick = {}, label = { Text("deneme ${f.retries}") })
                            if (f.status == FilmEntity.STATUS_FAILED) {
                                IconButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.filmDao().setStatus(f.id, FilmEntity.STATUS_PENDING)
                                    }
                                }) { Icon(Icons.Filled.Refresh, contentDescription = "Tekrar dene") }
                            }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) { db.filmDao().delete(f.id) }
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Kaldır") }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    FilmEntity.STATUS_PENDING -> "Bekliyor"
    FilmEntity.STATUS_DOWNLOADING -> "İndiriliyor"
    FilmEntity.STATUS_DONE -> "Tamamlandı"
    FilmEntity.STATUS_FAILED -> "Başarısız"
    else -> status
}

@Composable
private fun statusColor(status: String) = when (status) {
    FilmEntity.STATUS_DONE -> MaterialTheme.colorScheme.primaryContainer
    FilmEntity.STATUS_FAILED -> MaterialTheme.colorScheme.errorContainer
    FilmEntity.STATUS_DOWNLOADING -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}
