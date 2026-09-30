package com.burton.pod.data.search

import com.burton.pod.data.parse.TinyJson
import com.burton.pod.data.parse.TinyJson.objList
import com.burton.pod.data.parse.TinyJson.str
import com.burton.pod.domain.SearchHit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItunesSearch @Inject constructor(
    private val client: OkHttpClient,
) {
    fun search(term: String): List<SearchHit> {
        val query = term.trim()
        if (query.isEmpty()) return emptyList()
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .addQueryParameter("media", "podcast")
            .addQueryParameter("limit", "25")
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Podcast search failed (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            return parse(body)
        }
    }

    companion object {
        fun parse(json: String): List<SearchHit> {
            val root = TinyJson.parseObject(json)
            return root.objList("results").mapNotNull { row ->
                val feedUrl = row.str("feedUrl")
                if (feedUrl.isBlank()) return@mapNotNull null
                SearchHit(
                    title = row.str("collectionName").ifBlank { row.str("trackName") },
                    author = row.str("artistName"),
                    feedUrl = feedUrl,
                    artworkUrl = row.str("artworkUrl600").ifBlank { row.str("artworkUrl100") }.ifBlank { null },
                )
            }
        }
    }
}
