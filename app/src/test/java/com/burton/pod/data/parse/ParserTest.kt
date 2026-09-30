package com.burton.pod.data.parse

import com.burton.pod.data.repository.CatalogCodec
import com.burton.pod.data.search.ItunesSearch
import com.burton.pod.domain.Download
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.domain.Episode
import com.burton.pod.domain.Ids
import com.burton.pod.domain.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {
    @Test
    fun parsesRssChannelAndEpisodes() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Night Drive</title>
                <link>https://example.com</link>
                <description>After hours radio</description>
                <itunes:author>Analog Heart</itunes:author>
                <itunes:image href="https://example.com/art.jpg"/>
                <item>
                  <title>Episode One</title>
                  <guid>ep-1</guid>
                  <pubDate>Tue, 01 Apr 2025 12:00:00 GMT</pubDate>
                  <itunes:duration>1:02:03</itunes:duration>
                  <enclosure url="https://example.com/one.mp3" type="audio/mpeg" length="100"/>
                </item>
                <item>
                  <title>Episode Two</title>
                  <guid>ep-2</guid>
                  <enclosure url="https://example.com/two.mp3" type="audio/mpeg"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val parsed = RssParser.parse(xml, "https://example.com/feed.xml")
        assertEquals("Night Drive", parsed.podcast.title)
        assertEquals("Analog Heart", parsed.podcast.author)
        assertEquals("https://example.com/art.jpg", parsed.podcast.artworkUrl)
        assertEquals(Ids.podcast("https://example.com/feed.xml"), parsed.podcast.id)
        assertEquals(2, parsed.episodes.size)
        assertEquals("ep-1", parsed.episodes[0].id)
        assertEquals("https://example.com/one.mp3", parsed.episodes[0].enclosureUrl)
        assertEquals(3723L, parsed.episodes[0].durationSeconds)
        assertEquals("ep-2", parsed.episodes[1].id)
    }

    @Test
    fun parsesAtomFeed() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Coastal</title>
              <author><name>Lee</name></author>
              <entry>
                <id>atom-1</id>
                <title>Tide</title>
                <link rel="enclosure" href="https://example.com/tide.mp3"/>
              </entry>
            </feed>
        """.trimIndent()
        val parsed = RssParser.parse(xml, "https://example.com/atom.xml")
        assertEquals("Coastal", parsed.podcast.title)
        assertEquals("Lee", parsed.podcast.author)
        assertEquals(1, parsed.episodes.size)
        assertEquals("atom-1", parsed.episodes[0].id)
        assertEquals("https://example.com/tide.mp3", parsed.episodes[0].enclosureUrl)
    }

    @Test
    fun parseDurationAcceptsClockAndSeconds() {
        assertEquals(90L, RssParser.parseDuration("90"))
        assertEquals(125L, RssParser.parseDuration("2:05"))
        assertEquals(3723L, RssParser.parseDuration("1:02:03"))
        assertEquals(null, RssParser.parseDuration(""))
    }
}

class ItunesSearchTest {
    @Test
    fun parsesDirectoryResults() {
        val json = """
            {"results":[
              {"collectionName":"Night Drive","artistName":"Analog Heart","feedUrl":"https://example.com/feed.xml","artworkUrl600":"https://example.com/art.jpg"},
              {"collectionName":"No Feed","artistName":"X"}
            ]}
        """.trimIndent()
        val hits = ItunesSearch.parse(json)
        assertEquals(1, hits.size)
        assertEquals("Night Drive", hits[0].title)
        assertEquals("Analog Heart", hits[0].author)
        assertEquals("https://example.com/feed.xml", hits[0].feedUrl)
    }
}

class CatalogCodecTest {
    @Test
    fun roundTripsLibrary() {
        val podcast = Podcast(
            id = "https://example.com/feed.xml",
            feedUrl = "https://example.com/feed.xml",
            title = "Night Drive",
            author = "Analog Heart",
            description = "After hours",
            artworkUrl = "https://example.com/art.jpg",
            siteUrl = "https://example.com",
        )
        val episode = Episode(
            id = "ep-1",
            podcastId = podcast.id,
            title = "Episode One",
            description = "First",
            publishedAt = 1_700_000_000_000L,
            durationSeconds = 3600,
            enclosureUrl = "https://example.com/one.mp3",
            enclosureType = "audio/mpeg",
            artworkUrl = podcast.artworkUrl,
        )
        val download = Download(
            episodeId = episode.id,
            status = DownloadStatus.Done,
            progress = 1f,
            path = "/data/episodes/one.audio",
        )
        val json = CatalogCodec.encode(
            podcasts = listOf(podcast),
            episodes = mapOf(podcast.id to listOf(episode)),
            downloads = mapOf(episode.id to download),
            lastEpisodeId = episode.id,
            lastPositionMs = 12_000,
        )
        val cache = CatalogCodec.decode(json)
        assertEquals(podcast.title, cache.podcasts.single().title)
        assertEquals(episode.title, cache.episodes.getValue(podcast.id).single().title)
        assertEquals(DownloadStatus.Done, cache.downloads.getValue(episode.id).status)
        assertEquals(episode.id, cache.lastEpisodeId)
        assertEquals(12_000L, cache.lastPositionMs)
        assertTrue(json.contains("Night Drive"))
    }
}

class TinyJsonTest {
    @Test
    fun roundTripsObject() {
        val json = TinyJson.stringify(mapOf("name" to "Burton", "count" to 3, "ok" to true))
        val obj = TinyJson.parseObject(json)
        assertEquals("Burton", obj["name"])
        assertEquals(3L, obj["count"])
        assertEquals(true, obj["ok"])
    }
}
