# Using Burton Pod

Burton Pod is a podcast downloader and player on the phone. Subscribe to public RSS feeds, download episodes, and listen with or without a network.

## Permissions

On first launch (Android 13+) the app asks for notifications so playback can show a media session in the shade. You can refuse; audio still plays while the app is in the foreground.

| Permission | Why |
| --- | --- |
| Internet | Directory search, RSS refresh, streaming, downloads |
| Notifications (13+) | Lock-screen / shade transport via Media3 |
| Foreground service | Keep audio running when the app is backgrounded |

Some feeds are still HTTP. The app allows cleartext so those enclosures can download.

## Screens

Bottom navigation, left to right: **Library**, **Discover**, **Queue**, **Downloads**. A compact now-playing bar sits above the tabs on every screen except the full Now Playing page.

### Library

Lists subscribed shows from local cache. Each row is artwork, title, author, and episode count. Tap a row to open the show. Favorite episodes sit above the show list when you have any; long-press a favorite to remove it. The refresh icon reloads every feed. The gear opens **Settings**.

### Discover

Search the public Apple podcast directory (no Apple ID). Tap **Subscribe** to fetch that show’s RSS and add it to Library. You can also paste a feed URL and subscribe directly.

### Queue

Ordered list of episodes to play next. Drag the handle to change order. Tap a row to play it. Long-press for **Move to top**, **Move to bottom**, **Mark as played**, **Remove from queue**, **Delete download**, and **Add to favorites**. When the current episode ends, the next unplayed queued episode starts.

Add episodes from a show with the queue icon, or long-press a show episode.

### Downloads

Episodes queued, in progress, finished, or failed. Finished rows play from the local file. **Remove** deletes the file from app storage; the catalog row stays in the show list.

### Show

Episode list for one subscription, newest first. Tap a row to play (stream if not downloaded). The queue icon adds (or removes) the episode. The download icon saves the enclosure. Long-press for queue, play next, played, and favorites. **Unsubscribe** drops the show and its downloaded files.

### Now Playing

Full transport: artwork, title, author, date, duration, seek bar, skip back/forward (tap uses the saved amount; long-press picks 5, 10, 15, 30, 45, or 60 seconds), previous/next (queue first, then the show). Tap the speed (for example **1.25×**) to set playback speed, save a preset, and reuse it later. **Show Notes** opens the episode page when the feed includes a link. An **About** block shows the show and episode text. Phone volume keys use the system media stream.

### Settings

**Grayscale artwork** desaturates every cover image. Counts for library and downloads, plus **Burton Pod** / About with `VERSION_NAME`.

## What is stored on the phone

DataStore (`burton_pod`):

- Subscribed podcasts and their episode lists
- Download records (status, path)
- Last episode id and playback position
- Play queue, played ids, favorites
- Playback speed, saved speed presets, skip amounts
- Grayscale artwork

Audio files live under the app’s private `filesDir/episodes`. Clearing app data removes subscriptions and downloads.
