package com.example.oneDmBot.scrape

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class FilmLink(val url: String, val title: String)

object SiteScraper {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun fetchFilmsOnFirstPage(categoryUrl: String): List<FilmLink> {
        val request = Request.Builder()
            .url(categoryUrl)
            .header("User-Agent", UA)
            .header("Accept-Language", "tr,en;q=0.8")
            .build()

        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $categoryUrl")
            resp.body?.string().orEmpty()
        }

        val doc = Jsoup.parse(html, categoryUrl)
        val seen = LinkedHashMap<String, String>()

        // fullhdfilmizlesene listing pages render film cards as <a> wrapping a poster.
        // Several selectors are tried in order of specificity; first match wins per anchor.
        val candidates = listOf(
            "article a[href]",
            "div.list-series a[href]",
            "ul#list a[href]",
            "a.poster[href]",
            "a[href*='/film/']",
            "a[href*='-izle']"
        )

        for (sel in candidates) {
            for (a in doc.select(sel)) {
                val href = a.absUrl("href").ifBlank { continue }
                if (!isFilmUrl(href, categoryUrl)) continue
                val title = (a.attr("title").ifBlank {
                    a.selectFirst("img")?.attr("alt").orEmpty()
                }.ifBlank { a.text() }).trim()
                if (title.isNotEmpty()) {
                    seen.putIfAbsent(stripFragment(href), title)
                }
            }
            if (seen.isNotEmpty()) break
        }

        return seen.entries.map { (url, title) -> FilmLink(url, title) }
    }

    private fun isFilmUrl(href: String, categoryUrl: String): Boolean {
        if (!href.startsWith("http")) return false
        if (href == categoryUrl || href.startsWith("$categoryUrl/page/")) return false
        // film detail pages commonly contain "-izle" or "/film/"
        if (href.contains("/film/")) return true
        if (Regex("-izle(/|\\?|$)").containsMatchIn(href)) return true
        return false
    }

    private fun stripFragment(url: String): String {
        val hash = url.indexOf('#')
        return if (hash > 0) url.substring(0, hash) else url
    }
}
