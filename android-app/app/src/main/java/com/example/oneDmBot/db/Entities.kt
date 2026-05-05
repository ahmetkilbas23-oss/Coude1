package com.example.oneDmBot.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index(value = ["url"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val displayName: String,
    val lastCheckedAt: Long = 0L
)

@Entity(tableName = "films", indices = [Index(value = ["filmUrl"], unique = true)])
data class FilmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filmUrl: String,
    val title: String,
    val categoryId: Long,
    val status: String,
    val retries: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
    }
}
