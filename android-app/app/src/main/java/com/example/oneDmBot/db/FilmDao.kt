package com.example.oneDmBot.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmDao {
    @Query("SELECT * FROM films ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FilmEntity>>

    @Query("SELECT * FROM films WHERE status = :status ORDER BY id ASC LIMIT 1")
    suspend fun nextWithStatus(status: String): FilmEntity?

    @Query("SELECT filmUrl FROM films")
    suspend fun allFilmUrls(): List<String>

    @Query("SELECT COUNT(*) FROM films WHERE filmUrl = :url")
    suspend fun existsByUrl(url: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(film: FilmEntity): Long

    @Query("UPDATE films SET status = :status, updatedAt = :ts WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE films SET retries = retries + 1, updatedAt = :ts WHERE id = :id")
    suspend fun bumpRetry(id: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE films SET status = :status, updatedAt = :ts WHERE status = 'downloading'")
    suspend fun resetInflight(status: String = FilmEntity.STATUS_PENDING, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM films WHERE id = :id")
    suspend fun delete(id: Long)
}
