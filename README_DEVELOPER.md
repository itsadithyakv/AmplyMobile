# Amply Mobile Developer README

This document is for developers who need to understand the app quickly. The short version: Amply Mobile is a native Android local music player built with Kotlin, Jetpack Compose, Room, WorkManager, and Media3/ExoPlayer. It scans local audio, stores the library offline, generates smart playlists, plays music in the background, fetches lyrics and metadata when online, and exposes playback controls through a home-screen widget.

## What The App Does

- Scans audio from Android `MediaStore` and user-selected folders.
- Stores songs, playlists, lyrics, metadata, settings, and queue state in Room.
- Plays local audio through a Media3 `MediaSessionService`.
- Provides Home, Search, Library, Now Playing, Playlist Detail, and Settings screens in Compose.
- Builds smart playlists from local listening stats, genres, artists, favorites, and recency.
- Fetches lyrics from LRCLIB and caches them locally.
- Fetches genre data from MusicBrainz and artist summaries from Wikipedia.
- Supports favorites, shuffle, repeat, seek, crossfade, and a basic equalizer.
- Provides a resizable Android app widget for playback controls.

## Project Shape

```text
app/src/main/java/com/amply/mobile/
  AmplyApplication.kt          Application entry point.
  AppContainer.kt              Manual dependency container.
  MainActivity.kt              Compose UI, screens, and reusable UI pieces.
  MainViewModel.kt             UI state and app orchestration.
  domain/Models.kt             Domain models shared across layers.
  data/local/                  Room entities, DAOs, database, mappers.
  data/repository/             Library, playlist, and settings repositories.
  scan/MusicScanner.kt         MediaStore and folder scanning.
  playback/                    Media3 service and controller connection.
  playlist/PlaylistEngine.kt   Smart playlist generation.
  lyrics/                      LRCLIB client, LRC parser, lyric validation.
  metadata/                    MusicBrainz/Wikipedia metadata enrichment.
  worker/                      WorkManager background jobs.
  widget/                      Android app widget provider.
  ui/theme/Theme.kt            Colors, fonts, Material theme.
```

The app currently uses a simple manual container instead of Hilt/Dagger. `AmplyApplication` creates `AppContainer`, and the `MainViewModel` retrieves repositories and playback through that container.

## First Files To Read

1. `MainViewModel.kt`
   - Best high-level map of the app. It wires UI events to repositories, playback, lyrics, metadata, and settings.

2. `MainActivity.kt`
   - Contains the Compose UI. It is large and screen-driven: permission gate, app shell, Home, Search, Library, Now Playing, Settings, and shared UI components.

3. `AppContainer.kt`
   - Shows how dependencies are created: Room database, scanner, repositories, playlist engine, and playback connection.

4. `AmplyDatabase.kt` and `Entities.kt`
   - Define the persistent model: songs, playlists, lyrics, genre cache, artist info, queue state, and settings.

5. `PlaybackConnection.kt` and `AmplyPlaybackService.kt`
   - Explain how UI playback calls reach Media3/ExoPlayer and how playback state is persisted.

6. `PlaylistEngine.kt`
   - Defines the generated smart playlists and ranking logic.

## Runtime Flow

### App startup

```text
AmplyApplication
  -> AppContainer
      -> Room database
      -> repositories
      -> PlaybackConnection
  -> MetadataEnrichmentWorker.enqueuePeriodic()

MainActivity
  -> AmplyTheme
  -> MainViewModel
  -> permission gate
  -> main Compose app
```

`AmplyApplication` also calls `LibraryScanWorker.cancel(this)`, so periodic library scanning is currently disabled at startup. Metadata enrichment is scheduled periodically.

### Library scan

```text
UI action
  -> MainViewModel.scanLibrary() or addMusicFolder()
  -> LibraryRepository
  -> MusicScanner
  -> Room songs table
  -> PlaylistRepository.regenerateSmartPlaylists()
  -> Compose state updates from Room Flow
```

`MusicScanner.scan()` reads public audio through `MediaStore`. `MusicScanner.scanTree()` reads a user-selected folder through `DocumentFile` and `MediaMetadataRetriever`.

Supported folder-scan extensions are:

```text
mp3, m4a, aac, flac, wav, ogg, opus
```

### Playback

```text
UI play action
  -> MainViewModel
  -> PlaybackConnection
  -> MediaController
  -> AmplyPlaybackService
  -> ExoPlayer
  -> MediaSession
```

`PlaybackConnection` owns the controller-side state flow used by the UI. It also persists queue state into Room through `QueueDao`.

`AmplyPlaybackService` owns ExoPlayer and the MediaSession. It handles:

- audio focus attributes
- noisy-audio handling
- media session activity
- equalizer setup
- widget refreshes when playback changes

### Lyrics

```text
Current song changes
  -> MainViewModel.loadCachedLyrics()
  -> if missing, MainViewModel.autoFetchLyrics()
  -> LyricsRepository
  -> LRCLIB search API
  -> lyrics table
  -> Now Playing lyric preview/full lyric screen
```

`LrcParser.kt` parses synced `.lrc` timestamps. Unsynced plain lyrics are also supported, but synced lyrics are preferred when available.

### Metadata

```text
MetadataEnrichmentWorker or manual settings action
  -> LyricsRepository.loadOrFetch()
  -> MetadataRepository.enrichGenre()
  -> MetadataRepository.artistInfo()
  -> Room caches
```

Genre enrichment uses MusicBrainz. Artist summaries use Wikipedia search plus the Wikipedia summary API.

## UI Map

The main screens are in `MainActivity.kt`:

- `AmplyPermissionGate` requests audio and notification permissions.
- `AmplyApp` owns the scaffold, bottom nav, mini-player, and screen switching.
- `HomeScreen` shows playlist exploration, recent songs, artists, genre mixes, and daily picks.
- `SearchScreen` searches songs by title, artist, album, genre, or manual genre.
- `LibraryScreen` shows all songs, playlists, liked songs, and genres.
- `PlaylistDetailScreen` lists songs in one playlist.
- `NowPlayingScreen` shows artwork, vinyl-style controls, progress, playback modes, and lyrics.
- `SettingsScreen` controls metadata fetching, smart playlist tuning, crossfade, and equalizer settings.

The bottom navigation currently exposes:

```text
Home, Search, Library, Now
```

Settings is reached from Home's overflow menu.

## Data Model

Room database version: `3`

Tables:

- `songs`
  - Local audio library plus playback stats and favorite/manual genre fields.
- `playlists`
  - Smart and custom playlist metadata.
- `playlist_songs`
  - Ordered playlist membership.
- `lyrics`
  - Cached LRCLIB or manually edited lyrics.
- `genre_cache`
  - Cached genre enrichment results.
- `artist_info`
  - Cached Wikipedia artist summaries.
- `queue_state`
  - Last playback queue, position, shuffle, and repeat mode.
- `settings`
  - App settings mirrored partly into SharedPreferences for playback service access.

Important: `AppContainer` currently uses `fallbackToDestructiveMigration(dropAllTables = true)`. Schema changes can wipe local app data during migration.

## Smart Playlists

`PlaylistEngine.generateSmartPlaylists()` returns generated playlists such as:

- Daily Mix
- Daily Discovery
- Today's Vibe
- On Repeat
- Road Mix
- Recently Added
- Favorites
- Rediscover
- Quick Hits
- Genre mixes
- Mood mixes: Happy, Sad, Chill, Focus, Energy
- Artist radio mixes

Inputs include play count, skip count, favorite status, last played time, total play time, genre, artist, duration, discovery intensity, randomness intensity, and a daily seed.

## Background Work

`MetadataEnrichmentWorker`

- Periodic job, every 24 hours.
- Requires network.
- Fetches lyrics, genre enrichment, and artist info for songs.
- Can also be triggered manually from Settings with "Fetch all metadata".

`LibraryScanWorker`

- Defines a 12-hour periodic library scan.
- Currently canceled on app startup by `AmplyApplication`, so it is not active unless startup behavior changes.

## Widget

`AmplyWidgetProvider` implements a home-screen widget with:

- current title and artist
- artwork
- play/pause
- previous
- next
- compact and larger layouts based on widget height

The widget connects to the Media3 session through `MediaController`. It falls back to the stored queue state when no active controller media item is available.

## Settings And Playback Effects

Settings are stored in Room. Playback-facing settings are also mirrored to SharedPreferences because `AmplyPlaybackService` needs them outside the ViewModel/repository flow.

Currently implemented playback effects:

- Crossfade: `PlaybackConnection` fades volume down near the end of a track, skips to the next item, then fades volume back up.
- Equalizer: `AmplyPlaybackService` uses Android `audiofx.Equalizer` and maps bass/mid/treble UI values across available bands.

Known setting caveat:

- `metadataFetchPaused` is persisted and shown in Settings, but the metadata worker/repository code does not currently check it before fetching.

## Permissions

Declared permissions include:

- `INTERNET`
- `READ_MEDIA_AUDIO`
- `READ_EXTERNAL_STORAGE` for older Android versions
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `WAKE_LOCK`

Runtime permission logic is in `MainActivity.kt` through `requiredPermissions()` and `hasAudioPermissions()`.

## Build And Test

Requirements from the main README:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0

Common commands:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Current unit tests cover:

- playlist generation
- LRC parsing
- genre normalization

## Development Notes

- The UI is currently concentrated in one large `MainActivity.kt`; screen extraction would make future UI work easier.
- Repository APIs are coroutine/Flow based and generally map Room entities into domain models.
- The app is offline-first for playback and library management. Network calls are enrichment only.
- Custom playlist creation and edited lyrics support exist in repository/ViewModel code, but not all of that is exposed in the current visible UI.
- `recordPlay()` is called when playback starts, but detailed listened-duration tracking is limited.
- `recordSkip()` exists in the repository but is not currently wired into playback transitions.
- Online metadata calls should remain throttled and cache-backed to avoid excessive API usage.

## Safe Places To Extend

- Add a new smart playlist: start in `PlaylistEngine.kt`, then add/adjust tests in `PlaylistEngineTest.kt`.
- Add a new setting: update `AppSettings`, `SettingsRepository`, `SettingsScreen`, and any playback/worker consumer.
- Add a new persisted field: update `Entities.kt`, `Mappers.kt`, DAOs, database version/schema, and migration strategy.
- Add a new playback control: update `PlaybackConnection`, `NowPlayingScreen`, and possibly `AmplyWidgetProvider`.
- Improve lyrics UX: use existing `lyricsCandidates`, `selectLyrics()`, and `saveEditedLyrics()` paths in `MainViewModel`.

