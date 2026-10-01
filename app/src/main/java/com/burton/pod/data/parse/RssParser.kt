package com.burton.pod.data.parse

import com.burton.pod.domain.Episode
import com.burton.pod.domain.Ids
import com.burton.pod.domain.ParsedFeed
import com.burton.pod.domain.Podcast
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object RssParser {
    fun parse(xml: String, feedUrl: String): ParsedFeed {
        val root = Xml.parse(xml).rootElement()
        if (root.matchesName("feed")) {
            return parseAtom(root, feedUrl)
        }
        val channel = root.descendants("channel").firstOrNull()
            ?: error("RSS feed is missing a channel")
        return parseRss(channel, feedUrl)
    }

    private fun parseRss(channel: Element, feedUrl: String): ParsedFeed {
        val title = channel.childText("title").ifBlank { feedUrl }
        val artwork = channel.itunesImage()
            ?: channel.child("image")?.childText("url")?.ifBlank { null }
        val podcast = Podcast(
            id = Ids.podcast(feedUrl),
            feedUrl = feedUrl,
            title = title,
            author = channel.childText("author").ifBlank { channel.childText("managingEditor") },
            description = channel.childText("description").ifBlank { channel.childText("summary") },
            artworkUrl = artwork,
            siteUrl = channel.childText("link").ifBlank { null },
        )
        val episodes = channel.children().filter { it.matchesName("item") }.mapNotNull { item ->
            parseItem(item, podcast)
        }
        return ParsedFeed(podcast, episodes)
    }

    private fun parseItem(item: Element, podcast: Podcast): Episode? {
        val enclosure = item.child("enclosure")
        val enclosureUrl = enclosure?.attr("url")?.ifBlank { null }
            ?: item.childText("link").takeIf { it.startsWith("http") }
            ?: return null
        val guid = item.childText("guid").ifBlank { enclosureUrl }
        val artwork = item.itunesImage() ?: podcast.artworkUrl
        val pageLink = item.childText("link").takeIf { it.startsWith("http") && it != enclosureUrl }
        return Episode(
            id = guid,
            podcastId = podcast.id,
            title = item.childText("title").ifBlank { "Episode" },
            description = item.childText("description").ifBlank {
                item.childText("summary").ifBlank { item.childText("encoded") }
            },
            publishedAt = parseDate(item.childText("pubDate").ifBlank { item.childText("published") }),
            durationSeconds = parseDuration(item.childText("duration")),
            enclosureUrl = enclosureUrl,
            enclosureType = enclosure?.attr("type")?.ifBlank { null },
            artworkUrl = artwork,
            linkUrl = pageLink,
        )
    }

    private fun parseAtom(feed: Element, feedUrl: String): ParsedFeed {
        val title = feed.childText("title").ifBlank { feedUrl }
        val artwork = feed.itunesImage()
        val podcast = Podcast(
            id = Ids.podcast(feedUrl),
            feedUrl = feedUrl,
            title = title,
            author = feed.child("author")?.childText("name").orEmpty(),
            description = feed.childText("subtitle").ifBlank { feed.childText("summary") },
            artworkUrl = artwork,
            siteUrl = feed.atomLink("alternate") ?: feed.atomLink("self"),
        )
        val episodes = feed.children().filter { it.matchesName("entry") }.mapNotNull { entry ->
            val enclosureUrl = entry.atomLink("enclosure")
                ?: entry.atomLink("alternate")
                ?: return@mapNotNull null
            val guid = entry.childText("id").ifBlank { enclosureUrl }
            val pageLink = entry.atomLink("alternate")?.takeIf { it != enclosureUrl }
            Episode(
                id = guid,
                podcastId = podcast.id,
                title = entry.childText("title").ifBlank { "Episode" },
                description = entry.childText("summary").ifBlank { entry.childText("content") },
                publishedAt = parseDate(entry.childText("published").ifBlank { entry.childText("updated") }),
                durationSeconds = parseDuration(entry.childText("duration")),
                enclosureUrl = enclosureUrl,
                enclosureType = null,
                artworkUrl = podcast.artworkUrl,
                linkUrl = pageLink,
            )
        }
        return ParsedFeed(podcast, episodes)
    }

    private fun Element.itunesImage(): String? {
        descendants("image").forEach { image ->
            val href = image.attr("href")
            if (href.isNotBlank()) return href
        }
        return null
    }

    private fun Element.atomLink(rel: String): String? {
        children().filter { it.matchesName("link") }.forEach { link ->
            val linkRel = link.attr("rel").ifBlank { "alternate" }
            if (linkRel.equals(rel, ignoreCase = true)) {
                val href = link.attr("href")
                if (href.isNotBlank()) return href
            }
        }
        return null
    }

    fun parseDuration(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { return it }
        val parts = value.split(':')
        if (parts.any { it.toLongOrNull() == null }) return null
        val numbers = parts.map { it.toLong() }
        return when (numbers.size) {
            3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            2 -> numbers[0] * 60 + numbers[1]
            1 -> numbers[0]
            else -> null
        }
    }

    fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
        )
        patterns.forEach { pattern ->
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = true
                }
                format.parse(raw.replace("Z", "+0000").let { if (pattern.contains("'Z'")) raw else it })
                    ?.time
                    ?.let { return it }
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}
