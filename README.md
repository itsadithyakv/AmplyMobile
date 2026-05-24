# Amply Mobile

Amply is a native Android music player for local audio libraries. It is built for offline-first playback, fast browsing, smart playlists, cached lyrics, home-screen widgets, and a dark graphite/orange interface.

The app scans music already on the device, stores the library locally, and plays through a Media3 background playback service so music can keep running outside the app.

## Features

- Local audio scanning from Android `MediaStore` and user-selected folders.
- Background playback with Media3, ExoPlayer, media session controls, and queue restore.
- Home-screen widgets for playback controls and quick playlist starts.
- Smart playlists such as Daily Mix, Daily Discovery, On Repeat, mood mixes, genre mixes, and artist radio.
- Search by title, artist, album, genre, and manually assigned genre.
- Library views for songs, playlists, liked songs, and genres.
- Now Playing screen with vinyl-style controls, progress scrubbing, lyrics, shuffle, repeat, crossfade, and equalizer controls.
- Cached synced or plain lyrics from LRCLIB, with local editing support.
- Optional metadata enrichment using MusicBrainz and Wikipedia.
- Room-backed offline storage for songs, playlists, lyrics, metadata, settings, and queue state.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Media3 / ExoPlayer
- Room
- WorkManager
- OkHttp
- Kotlin Coroutines and Flow

## Project Structure

```text
app/src/main/java/com/amply/mobile/
  MainActivity.kt              Android activity entry point.
  AppShell.kt                  App scaffold, permissions, navigation, mini-player.
  HomeScreen.kt                Home screen and playlist discovery UI.
  LibraryScreens.kt            Search, Library, and Playlist Detail screens.
  NowPlayingScreen.kt          Player UI, lyrics, progress, and controls.
  SettingsScreen.kt            Metadata, crossfade, and equalizer settings.
  SharedComponents.kt          Shared Compose rows, artwork, chips, and helpers.
  MainViewModel.kt             UI state and app actions.
  playback/                    Media3 service and controller connection.
  data/local/                  Room entities, DAOs, migrations, and mappers.
  data/repository/             Library, playlist, and settings repositories.
  scan/                        Music scanning.
  playlist/                    Smart playlist generation.
  lyrics/                      Lyrics fetching, parsing, and caching.
  metadata/                    Genre and artist metadata enrichment.
  worker/                      Periodic library and metadata work.
  widget/                      Android app widget provider.
```

For a deeper architecture and data-flow guide, see [README_DEVELOPER.md](README_DEVELOPER.md).

## Requirements

- Android Studio
- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- Android device or emulator running API 26+

## Setup

Clone the repository and open it in Android Studio.

If Gradle cannot find your Android SDK, create `local.properties` in the project root:

```properties
sdk.dir=C\:/Users/YourName/AppData/Local/Android/Sdk
```

On Windows, Android Studio's bundled JDK is usually enough. If needed:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
```

## Build And Test

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Run Android lint:

```powershell
.\gradlew.bat :app:lintDebug
```

Build a release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Install on a connected emulator or device:

```powershell
.\gradlew.bat :app:installDebug
```

## Permissions

Amply requests the permissions needed for a local music player:

- Audio library access for local songs.
- Notification access for media playback controls on modern Android versions.
- Foreground media playback service support.
- Network access for optional lyrics and metadata enrichment.

Playback itself is local-first. Network calls are used only for optional enrichment and caching.

## Status

Amply is under active development. The current codebase includes production hardening for background playback, queue restoration, Room migrations, bounded metadata work, release builds, and lint-clean blocking errors.

The app is not currently published on the Play Store.

## License

No license has been added yet. Treat the code as all rights reserved unless a license is added.
