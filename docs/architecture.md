# Architecture

The app is a single Gradle module (`:app`), Kotlin, Jetpack Compose, Hilt, OkHttp, and Media3. UI never talks HTTP directly; screens collect `PodcastRepository` state.

```
ui/          Compose screens and ViewModels (Hilt)
domain/      Podcast, Episode, Download, PlaybackState, SearchHit
data/
  parse      RSS/Atom, tiny JSON cache
  search     Apple iTunes Search API (no login)
  download   OkHttp episode files into app storage
  playback   ExoPlayer + MediaSessionService
  repository PodcastRepository + LocalPrefs (DataStore)
di/          OkHttp client (longer timeouts for feeds and files)
```

## Feeds and directory

`ItunesSearch` queries `https://itunes.apple.com/search?media=podcast`. Results that include a `feedUrl` become subscribe targets. Pasting an RSS or Atom URL skips the directory.

`RssParser` reads RSS 2.0 channels (including iTunes tags) and Atom feeds. Enclosure URL is required for an item to become an episode. Duration accepts seconds or `H:MM:SS`.

## Downloads

`EpisodeDownloader` streams the enclosure into `filesDir/episodes/<sha256>.audio`. Progress updates the in-memory catalog so the Downloads tab and episode rows can show a bar. Finished files are local; play prefers the file when `DownloadStatus.Done`.

## Playback

`PlayerHolder` owns a process-singleton ExoPlayer. `PlaybackService` is a `MediaSessionService` so playback continues with a notification. Skip amounts default to 10 seconds back and 30 seconds forward and are adjustable (5–60s). Playback speed is persisted. Previous/next prefer the Queue, then walk the current show’s episode list (newest first). When an episode ends, the next unplayed queued item starts.

## Snapshot and cache

`PodcastRepository` is a process singleton. `start()`:

1. Hydrates DataStore (`burton_pod`) so Library is not an empty spinner
2. Reconciles download records with files still on disk
3. Refreshes every subscribed feed

Position is kept in memory from the player and written to DataStore every few seconds.

## UI shell

`MainActivity` hosts a `NavHost` and a persistent bottom bar. Tab order is Library → Discover → Queue → Downloads. Show detail and Now Playing are nested destinations. Settings is a **FullScreenModal** from the Library gear. The mini now-playing bar hides on the full player. **Grayscale artwork** is a CompositionLocal over `AlbumArt`.
