# Development

## Tooling

- JDK **17**
- Android SDK compile/target **35**, min **26**
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01
- Hilt 2.53.1 (KSP)
- Media3 1.5.1

Point Gradle at the SDK with `local.properties` (`sdk.dir=…`). That file is gitignored.

## Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew testDebugUnitTest
```

Release assemble is blocked unless `keystore.properties` exists and `storeFile` points at a real keystore. Copy [`keystore.properties.example`](../keystore.properties.example) and keep `keystore.properties`, `*.jks`, and `*.keystore` out of git (see `.gitignore`). GitHub Actions signing is [build automation](build-automation.md).

Debug application id is `com.burton.pod.debug` so it can sit next to a signed install.

## Layout

```
app/src/main/java/com/burton/pod/
  MainActivity.kt              nav, mini player, notification permission
  data/parse/                  RSS/Atom, XML, JSON
  data/search/                 iTunes directory
  data/download/               episode files
  data/playback/               ExoPlayer + MediaSessionService
  data/repository/             PodcastRepository, DataStore
  domain/                      models
  ui/library, discover, downloads, show, player, settings, components, theme
app/src/test/java/…/data/parse Parser and cache tests (no device)
```

Parser tests cover RSS, Atom, iTunes duration, directory JSON, and catalog round-trip. Run those before changing feed parsing.

## Network while debugging

Search and subscribe need the internet. The emulator is fine. Some feeds are still HTTP; the app allows cleartext.

If a subscribe fails: open the feed URL in a browser, confirm it is RSS/Atom, and that items have `<enclosure>` (or Atom `rel="enclosure"`).

## Versioning while developing

Do not hand-edit `CHANGELOG.md` or `version.txt` on feature branches. Those are owned by [release-please](releases.md) from Conventional Commits on `master`.

Commit subjects must follow Conventional Commits. Install the hook once:

```bash
./scripts/install-git-hooks.sh
```

See [CONTRIBUTING.md](../CONTRIBUTING.md).
