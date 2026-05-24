# Amply Mobile

Native Android MVP for Amply, an offline-first local music player with background playback, smart playlists, cached lyrics, genre enrichment, and a dark graphite UI with orange accents.

For a deeper developer-oriented walkthrough of the architecture, data flow, and key files, see [README_DEVELOPER.md](README_DEVELOPER.md).

## Build

Install Android Studio with:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0

Then either set `ANDROID_HOME` or create `local.properties`:

```properties
sdk.dir=C\:\\Users\\Adi\\AppData\\Local\\Android\\Sdk
```

Run:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Current MVP

- Local music scan through `MediaStore`
- Media3 `MediaSessionService` background playback
- Compose UI with ZT Nature fonts and Amply orange accents
- Room-backed library, playlists, lyrics, metadata cache, settings, and queue state
- LRCLIB lyrics lookup with local edit/cache support
- MusicBrainz genre enrichment with local caching
- Manual and generated smart playlists
