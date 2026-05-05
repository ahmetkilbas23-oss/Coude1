package com.example.oneDmBot.work

import android.content.Context
import com.example.oneDmBot.db.AppDatabase
import com.example.oneDmBot.db.CategoryEntity
import com.example.oneDmBot.db.FilmEntity
import com.example.oneDmBot.scrape.SiteScraper

object CategorySync {

    /**
     * Scrapes the category page and inserts any film URLs that are not yet in the DB.
     * Returns the number of new films added to the queue.
     */
    suspend fun syncOne(context: Context, category: CategoryEntity): Int {
        val db = AppDatabase.get(context)
        val films = runCatching { SiteScraper.fetchFilmsOnFirstPage(category.url) }.getOrElse {
            return 0
        }
        var added = 0
        for (link in films) {
            if (db.filmDao().existsByUrl(link.url) > 0) continue
            val rowId = db.filmDao().insert(
                FilmEntity(
                    filmUrl = link.url,
                    title = link.title.ifBlank { link.url },
                    categoryId = category.id,
                    status = FilmEntity.STATUS_PENDING
                )
            )
            if (rowId > 0) added++
        }
        db.categoryDao().touchChecked(category.id, System.currentTimeMillis())
        return added
    }

    suspend fun syncAll(context: Context): Int {
        val db = AppDatabase.get(context)
        var total = 0
        for (cat in db.categoryDao().listAll()) {
            total += syncOne(context, cat)
        }
        return total
    }
}
