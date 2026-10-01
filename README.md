# Burton Pod

An Android podcast client. Subscribe to RSS feeds, search the public podcast directory, download episodes for offline listening, and play them on the phone. There is no cloud account and no proprietary directory login.

Signed APKs are published on [GitHub Releases](https://github.com/Burton-Workspaces/burton-pod/releases). Droidify / F-Droid: [burton-sonos-fdroid](https://github.com/Burton-Workspaces/burton-sonos-fdroid) (`https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo`).

## What it does

- **Library** — subscribed shows, episode counts, refresh, settings, favorite episodes
- **Discover** — search Apple’s public podcast directory, or paste an RSS URL
- **Queue** — drag to reorder upcoming episodes; long-press for move, played, remove, delete, favorites
- **Downloads** — in-progress and finished episode files on this phone
- **Show** — episode list, play, queue, download, unsubscribe
- **Now Playing** — speed presets, skip back/forward (long-press to pick 5–60s), show notes, episode detail; the mini bar hides on this screen

First launch hydrates the last catalog from local cache, then refreshes subscribed feeds.

## Requirements

- Android 8.0+ (API 26)
- Internet access for search, feed refresh, streaming, and first-time downloads
- Notification permission on Android 13+ (playback controls in the shade)

## Docs

| Doc | Contents |
| --- | --- |
| [Using the app](docs/using.md) | Screens, permissions, and what lives on-device |
| [Architecture](docs/architecture.md) | Packages, RSS, downloads, playback, caching |
| [Development](docs/development.md) | Build, run, test, project layout |
| [Build automation](docs/build-automation.md) | GitHub Actions, workflow permissions, signing secrets |
| [Releases](docs/releases.md) | SemVer, local build + publish walkthrough, GitHub Releases |
| [F-Droid / Droidify](docs/fdroid.md) | Self-hosted repo, Fingerprint, Pages publish script |
| [Contributing](CONTRIBUTING.md) | Conventional Commits (required) |

## Quick start (debug)

```bash
./gradlew :app:installDebug
```

Debug builds use application id `com.burton.pod.debug`. Release builds need a keystore; see [docs/releases.md](docs/releases.md).

```bash
./gradlew testDebugUnitTest
```

## License and scope

This is a household podcast downloader. It does not replace a hosted podcast CMS, and it does not sign in to Apple, Spotify, or other music accounts.
