package com.burton.pod.data.parse

import com.burton.pod.data.repository.CatalogCodec
import com.burton.pod.data.repository.DiscoveryPrefs
import com.burton.pod.data.search.DiscoveryCatalog
import com.burton.pod.data.search.ItunesSearch
import com.burton.pod.domain.DefaultDiscoveryFeeds
import com.burton.pod.domain.DiscoveryFeed
import com.burton.pod.domain.Download
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.domain.Episode
import com.burton.pod.domain.Ids
import com.burton.pod.domain.Podcast
import com.burton.pod.domain.SearchHit
import com.burton.pod.domain.extractHttpUrl
import com.burton.pod.domain.formatSpeed
import com.burton.pod.domain.moved
import com.burton.pod.domain.roundSpeed
import com.burton.pod.domain.showNotesUrl
import com.burton.pod.domain.stripHtml
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
                  <link>https://example.com/one</link>
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
        assertEquals("https://example.com/one", parsed.episodes[0].linkUrl)
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
            queueIds = listOf(episode.id),
            playedIds = setOf(episode.id),
            favoriteIds = setOf(episode.id),
            playbackSpeed = 1.25f,
            savedSpeeds = listOf(1.25f, 1.5f),
            skipBackSeconds = 15,
            skipForwardSeconds = 45,
            grayscaleArtwork = true,
        )
        val cache = CatalogCodec.decode(json)
        assertEquals(podcast.title, cache.podcasts.single().title)
        assertEquals(episode.title, cache.episodes.getValue(podcast.id).single().title)
        assertEquals(DownloadStatus.Done, cache.downloads.getValue(episode.id).status)
        assertEquals(episode.id, cache.lastEpisodeId)
        assertEquals(12_000L, cache.lastPositionMs)
        assertEquals(listOf(episode.id), cache.queueIds)
        assertTrue(episode.id in cache.playedIds)
        assertTrue(episode.id in cache.favoriteIds)
        assertEquals(1.25f, cache.playbackSpeed, 0.001f)
        assertEquals(listOf(1.25f, 1.5f), cache.savedSpeeds)
        assertEquals(15, cache.skipBackSeconds)
        assertEquals(45, cache.skipForwardSeconds)
        assertTrue(cache.grayscaleArtwork)
        assertTrue(json.contains("Night Drive"))
    }

    @Test
    fun missingPlaybackFieldsUseDefaults() {
        val json = """{"podcasts":[],"episodes":[],"downloads":[],"lastEpisodeId":null,"lastPositionMs":0}"""
        val cache = CatalogCodec.decode(json)
        assertEquals(1f, cache.playbackSpeed, 0.001f)
        assertEquals(10, cache.skipBackSeconds)
        assertEquals(30, cache.skipForwardSeconds)
        assertTrue(cache.queueIds.isEmpty())
        assertTrue(!cache.grayscaleArtwork)
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

class DiscoveryCatalogTest {
    @Test
    fun parsesSpotifyChart() {
        val json = """
            [
              {"showUri":"spotify:show:abc","showName":"Night Drive","showPublisher":"Analog Heart","showImageUrl":"https://example.com/art.jpg"},
              {"showName":""}
            ]
        """.trimIndent()
        val hits = DiscoveryCatalog.parse(json)
        assertEquals(1, hits.size)
        assertEquals("Night Drive", hits[0].title)
        assertEquals("Analog Heart", hits[0].author)
        assertEquals("spotify:show:abc", hits[0].lookupId)
        assertEquals("https://example.com/art.jpg", hits[0].artworkUrl)
        assertEquals("", hits[0].feedUrl)
    }

    @Test
    fun parsesAppleMarketingTools() {
        val json = """
            {"feed":{"results":[
              {"artistName":"Analog Heart","id":"1200361736","name":"Night Drive","artworkUrl100":"https://example.com/100x100bb.png"}
            ]}}
        """.trimIndent()
        val hits = DiscoveryCatalog.parse(json)
        assertEquals(1, hits.size)
        assertEquals("Night Drive", hits[0].title)
        assertEquals("Analog Heart", hits[0].author)
        assertEquals("1200361736", hits[0].lookupId)
        assertEquals("https://example.com/600x600bb.png", hits[0].artworkUrl)
    }

    @Test
    fun parsesItunesRssObjectAndArray() {
        val single = """
            {"feed":{"entry":{"im:name":{"label":"Night Drive"},"im:artist":{"label":"Analog Heart"},"id":{"attributes":{"im:id":"1200361736"}},"im:image":[{"label":"https://example.com/art.jpg"}]}}}
        """.trimIndent()
        val one = DiscoveryCatalog.parse(single).single()
        assertEquals("Night Drive", one.title)
        assertEquals("Analog Heart", one.author)
        assertEquals("1200361736", one.lookupId)

        val many = """
            {"feed":{"entry":[
              {"im:name":{"label":"A"},"im:artist":{"label":"One"},"id":{"attributes":{"im:id":"1"}}},
              {"im:name":{"label":"B"},"im:artist":{"label":"Two"},"id":{"attributes":{"im:id":"2"}}}
            ]}}
        """.trimIndent()
        val hits = DiscoveryCatalog.parse(many)
        assertEquals(listOf("A", "B"), hits.map { it.title })
    }

    @Test
    fun parsesOpmlOutlines() {
        val xml = """
            <opml version="2.0">
              <body>
                <outline text="Night Drive" xmlUrl="https://example.com/feed.xml"/>
                <outline text="Group">
                  <outline title="Coastal" xmlUrl="https://example.com/coastal.xml" description="Lee"/>
                </outline>
              </body>
            </opml>
        """.trimIndent()
        val hits = DiscoveryCatalog.parse(xml)
        assertEquals(2, hits.size)
        assertEquals("Night Drive", hits[0].title)
        assertEquals("https://example.com/feed.xml", hits[0].feedUrl)
        assertEquals("Coastal", hits[1].title)
        assertEquals("Lee", hits[1].author)
    }
}

class DiscoverSubscribeMatchTest {
    private val library = listOf(
        Podcast(
            id = "night",
            feedUrl = "https://example.com/feed.xml",
            title = "Night Drive",
            author = "Analog Heart",
            description = "",
            artworkUrl = null,
            siteUrl = null,
        ),
    )

    @Test
    fun chartHitWithoutFeedUrlMatchesLibraryTitle() {
        val hit = SearchHit(
            title = "Night Drive",
            author = "Spotify Charts",
            feedUrl = "",
            artworkUrl = null,
            lookupId = "spotify:show:abc",
        )
        assertTrue(hit.matchesLibrary(library))
        assertTrue(!hit.matchesLibrary(emptyList()))
    }

    @Test
    fun resolvedFeedUrlMatchesLibrary() {
        val hit = SearchHit(
            title = "Other Title",
            author = "Other",
            feedUrl = "https://example.com/feed.xml",
            artworkUrl = null,
        )
        assertTrue(hit.matchesLibrary(library))
    }

    @Test
    fun sameDiscoverHitUsesLookupId() {
        val chart = SearchHit("Night Drive", "Charts", "", null, "spotify:show:abc")
        val resolved = chart.copy(feedUrl = "https://example.com/feed.xml")
        assertTrue(chart.sameDiscoverHit(resolved))
        assertEquals(chart.discoverIdentity(), resolved.discoverIdentity())
    }
}

class DiscoveryPrefsTest {
    @Test
    fun roundTripsCustomFeedsAndDefaultsToSpotify() {
        val custom = DiscoveryFeed(
            id = "custom-1",
            name = "My Chart",
            url = "https://example.com/chart.json",
        )
        val json = DiscoveryPrefs(selectedId = custom.id, custom = listOf(custom)).encode()
        val decoded = DiscoveryPrefs.decode(json)
        assertEquals("custom-1", decoded.selectedId)
        assertEquals("My Chart", decoded.custom.single().name)
        val (feeds, selectedId) = DiscoveryPrefs.merge(decoded)
        assertEquals("custom-1", selectedId)
        assertTrue(feeds.any { it.id == DefaultDiscoveryFeeds.SPOTIFY_TOP_ID })
        assertTrue(feeds.any { it.id == "custom-1" })
        assertEquals(DefaultDiscoveryFeeds.SPOTIFY_TOP_ID, DiscoveryPrefs.merge(DiscoveryPrefs()).second)
    }

    @Test
    fun dropsCustomDuplicatesOfBuiltInUrls() {
        val duplicate = DiscoveryFeed(
            id = "dup",
            name = "Copy",
            url = DefaultDiscoveryFeeds.all.first().url + "/",
        )
        val (feeds, selectedId) = DiscoveryPrefs.merge(
            DiscoveryPrefs(selectedId = "missing", custom = listOf(duplicate)),
        )
        assertEquals(DefaultDiscoveryFeeds.SPOTIFY_TOP_ID, selectedId)
        assertTrue(feeds.none { it.id == "dup" })
    }
}

class PlaybackHelpersTest {
    @Test
    fun formatsSpeedAndRounds() {
        assertEquals("1×", formatSpeed(1f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("1.5×", formatSpeed(1.5f))
        assertEquals(1.25f, roundSpeed(1.24f), 0.001f)
    }

    @Test
    fun movesQueueItems() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
    }

    @Test
    fun stripsHtmlAndFindsShowNotes() {
        assertEquals("Hello\nworld", stripHtml("<p>Hello<br/>world</p>"))
        val episode = Episode(
            id = "ep",
            podcastId = "show",
            title = "One",
            description = "Notes at https://example.com/notes extra",
            publishedAt = 0L,
            durationSeconds = null,
            enclosureUrl = "https://example.com/one.mp3",
            enclosureType = null,
            artworkUrl = null,
            linkUrl = "https://example.com/episode",
        )
        assertEquals("https://example.com/episode", episode.showNotesUrl())
        assertEquals("https://example.com/notes", extractHttpUrl(episode.description))
    }
}
