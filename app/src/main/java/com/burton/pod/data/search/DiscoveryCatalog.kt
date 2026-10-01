package com.burton.pod.data.search

import com.burton.pod.data.parse.RssParser
import com.burton.pod.data.parse.TinyJson
import com.burton.pod.data.parse.TinyJson.label
import com.burton.pod.data.parse.TinyJson.obj
import com.burton.pod.data.parse.TinyJson.objList
import com.burton.pod.data.parse.TinyJson.str
import com.burton.pod.data.parse.Xml
import com.burton.pod.data.parse.attr
import com.burton.pod.data.parse.descendants
import com.burton.pod.data.parse.rootElement
import com.burton.pod.domain.SearchHit
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoveryCatalog @Inject constructor(
    private val client: OkHttpClient,
    private val itunes: ItunesSearch,
) {
    fun load(url: String): List<SearchHit> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, application/xml, text/xml, text/json, */*")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Discovery feed failed (${response.code})")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("Empty discovery feed")
            return resolveAppleIds(parse(body, url).take(MAX_HITS))
        }
    }

    fun resolveFeedUrl(hit: SearchHit): String {
        hit.feedUrl.trim().takeIf { it.isNotBlank() }?.let { return it }
        val appleId = hit.lookupId?.takeIf { id -> id.all(Char::isDigit) }
        if (appleId != null) {
            itunes.lookup(listOf(appleId)).firstOrNull()?.feedUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        val term = listOf(hit.title, hit.author).filter { it.isNotBlank() }.joinToString(" ")
        val matches = itunes.search(term)
        val match = matches.firstOrNull { it.title.equals(hit.title, ignoreCase = true) }
            ?: matches.firstOrNull()
        return match?.feedUrl?.takeIf { it.isNotBlank() }
            ?: error("Could not find an RSS feed for ${hit.title}")
    }

    private fun resolveAppleIds(hits: List<SearchHit>): List<SearchHit> {
        val ids = hits.mapNotNull { it.lookupId?.takeIf { id -> id.all(Char::isDigit) } }.distinct()
        if (ids.isEmpty()) return hits
        val looked = runCatching { itunes.lookup(ids) }.getOrDefault(emptyList())
        if (looked.isEmpty()) return hits
        val byId = looked.associateBy { it.lookupId }
        return hits.map { hit ->
            val resolved = byId[hit.lookupId] ?: return@map hit
            hit.copy(
                feedUrl = resolved.feedUrl.ifBlank { hit.feedUrl },
                artworkUrl = resolved.artworkUrl ?: hit.artworkUrl,
                author = hit.author.ifBlank { resolved.author },
            )
        }
    }

    companion object {
        private const val MAX_HITS = 50
        private const val USER_AGENT = "BurtonPod/1.0 (Android)"

        fun parse(body: String, sourceUrl: String = ""): List<SearchHit> {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (trimmed.startsWith("<")) return parseXml(trimmed, sourceUrl)
            return parseJson(trimmed)
        }

        private fun parseJson(body: String): List<SearchHit> {
            return when (val root = TinyJson.parse(body)) {
                is List<*> -> parseSpotifyList(root)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val obj = root as Map<String, Any?>
                    when {
                        obj["results"] != null -> ItunesSearch.parse(body)
                        obj["feed"] != null -> parseAppleFeed(obj.obj("feed"))
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        }

        private fun parseAppleFeed(feed: Map<String, Any?>): List<SearchHit> {
            val marketing = feed.objList("results").mapNotNull { row ->
                val title = row.str("name").ifBlank { row.str("title") }
                if (title.isBlank()) return@mapNotNull null
                SearchHit(
                    title = title,
                    author = row.str("artistName").ifBlank { row.str("artist") },
                    feedUrl = row.str("feedUrl"),
                    artworkUrl = largerArtwork(row.str("artworkUrl100").ifBlank { row.str("artworkUrl") }),
                    lookupId = row.str("id").ifBlank { null },
                )
            }
            if (marketing.isNotEmpty()) return marketing
            return feed.objList("entry").mapNotNull { parseItunesRssEntry(it) }
        }

        private fun parseItunesRssEntry(entry: Map<String, Any?>): SearchHit? {
            val title = entry.label("im:name").ifBlank { entry.label("title") }
            if (title.isBlank()) return null
            val appleId = entry.obj("id").obj("attributes").str("im:id")
                .ifBlank { entry.obj("id").str("im:id") }
            val images = entry.objList("im:image")
            val artwork = images.lastOrNull()?.str("label")?.ifBlank { null }
                ?: images.lastOrNull()?.label("label")?.ifBlank { null }
            return SearchHit(
                title = title,
                author = entry.label("im:artist").ifBlank { entry.label("artist") },
                feedUrl = "",
                artworkUrl = artwork,
                lookupId = appleId.ifBlank { null },
            )
        }

        private fun parseSpotifyList(rows: List<*>): List<SearchHit> {
            return rows.mapNotNull { row ->
                val obj = row as? Map<*, *> ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val item = obj as Map<String, Any?>
                val title = item.str("showName").ifBlank { item.str("name") }
                if (title.isBlank()) return@mapNotNull null
                SearchHit(
                    title = title,
                    author = item.str("showPublisher").ifBlank { item.str("publisher") },
                    feedUrl = "",
                    artworkUrl = item.str("showImageUrl").ifBlank { item.str("artworkUrl") }.ifBlank { null },
                    lookupId = item.str("showUri").ifBlank { item.str("showId") }.ifBlank { null },
                )
            }
        }

        private fun parseXml(body: String, sourceUrl: String): List<SearchHit> {
            if (body.contains("<opml", ignoreCase = true)) {
                val outlines = Xml.parse(body).rootElement().descendants("outline").mapNotNull { outline ->
                    val feedUrl = outline.attr("xmlUrl", "xmlurl")
                    if (feedUrl.isBlank()) return@mapNotNull null
                    SearchHit(
                        title = outline.attr("text", "title").ifBlank { feedUrl },
                        author = outline.attr("description"),
                        feedUrl = feedUrl,
                        artworkUrl = null,
                    )
                }
                if (outlines.isNotEmpty()) return outlines
            }
            val parsed = RssParser.parse(body, sourceUrl)
            return listOf(
                SearchHit(
                    title = parsed.podcast.title,
                    author = parsed.podcast.author,
                    feedUrl = parsed.podcast.feedUrl.ifBlank { sourceUrl },
                    artworkUrl = parsed.podcast.artworkUrl,
                ),
            )
        }

        private fun largerArtwork(url: String): String? {
            if (url.isBlank()) return null
            return url.replace("100x100bb", "600x600bb")
        }
    }
}
