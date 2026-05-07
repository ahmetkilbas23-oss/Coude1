package com.example.oneDmBot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.oneDmBot.db.AppDatabase
import com.example.oneDmBot.db.CategoryEntity
import com.example.oneDmBot.work.CategorySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.categoryDao().observeAll().collectLatest { categories = it }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Kategoriler", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Görev Ekle")
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Henüz kategori eklenmemiş.\nSağ alttaki + ile bir kategori URL'si ekleyin.\n\nÖrnek:\nhttps://www.fullhdfilmizlesene.life/filmizle/animasyon-filmleri",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                items(categories, key = { it.id }) { cat ->
                    CategoryCard(
                        cat = cat,
                        onSync = {
                            scope.launch {
                                val added = withContext(Dispatchers.IO) { CategorySync.syncOne(ctx, cat) }
                                snackbar.showSnackbar("$added yeni film kuyruğa eklendi")
                            }
                        },
                        onDelete = {
                            scope.launch(Dispatchers.IO) { db.categoryDao().delete(cat.id) }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAdd) {
        AddCategoryDialog(
            onDismiss = { showAdd = false },
            onSave = { url, name ->
                scope.launch {
                    val cleanUrl = url.trim().trimEnd('/')
                    val cleanName = name.trim().ifBlank { cleanUrl.substringAfterLast('/') }
                    val id = withContext(Dispatchers.IO) {
                        db.categoryDao().insert(CategoryEntity(url = cleanUrl, displayName = cleanName))
                    }
                    if (id > 0) {
                        val added = withContext(Dispatchers.IO) {
                            CategorySync.syncOne(ctx, db.categoryDao().byId(id)!!)
                        }
                        snackbar.showSnackbar("Kategori eklendi, $added film kuyruğa girdi")
                    } else {
                        snackbar.showSnackbar("Bu URL zaten kayıtlı")
                    }
                    showAdd = false
                }
            }
        )
    }
}

@Composable
private fun CategoryCard(cat: CategoryEntity, onSync: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cat.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(cat.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text(
                text = if (cat.lastCheckedAt > 0)
                    "Son kontrol: ${java.text.DateFormat.getDateTimeInstance().format(cat.lastCheckedAt)}"
                else "Henüz kontrol edilmedi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSync) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Şimdi Kontrol Et")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil")
                }
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Görev Ekle") },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Kategori URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Görünen ad (opsiyonel)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onSave(url, name) }
            ) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
