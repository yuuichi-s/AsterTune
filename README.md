# AsterTune

[![AsterTune app icon](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/astertune.png)](https://github.com/yuuichi-s/AsterTune/blob/dev/assets/astertune.png)


[![Latest release](https://img.shields.io/github/v/release/yuuichi-s/AsterTune?include_prereleases)](https://github.com/yuuichi-s/AsterTune/releases)
[![License](https://img.shields.io/github/license/yuuichi-s/AsterTune)](https://www.gnu.org/licenses/gpl-3.0)

[English](README.md) | [日本語](README_ja.md)

A Material 3 YouTube Music client & local music player for Android

> [!NOTE]
> AsterTune is currently in preparation. This README is a draft and will be rewritten once the migration is complete.

## About This Fork

AsterTune is a fork of [OuterTune/OuterTune](https://github.com/OuterTune/OuterTune).

To facilitate APK distribution, we have changed the application and repository names from OuterTune to AsterTune.

We will release the APK once maintenance work is complete.

If you want to use the app right away, please build it yourself.
For most users, we recommend the `core` build.
If you want to play ALAC (.m4a) files, we recommend the `full` build.

```bash
# core debug build
./gradlew assembleCoreDebug

# full debug build
./gradlew assembleFullDebug
```

For detailed instructions, please see [CONTRIBUTING.md](https://github.com/yuuichi-s/AsterTune/blob/dev/CONTRIBUTING.md).

### Migrating Data from OuterTune to AsterTune

Since this is now a separate app, data will not be migrated automatically.
Please manually back up and restore your data.

The backup includes the library database and app settings.
It does not include downloaded audio files, so you will need to download them again after restoration.

1. In OuterTune, open ** Settings → Backup and Restore **, tap ** Backup **, and save the backup file.
2. Install AsterTune.
3. In AsterTune, open ** Settings → Backup and Restore **, tap ** Restore **, and select the backup file you saved.

## What This Fork Improves

This fork builds on [OuterTune/OuterTune](https://github.com/OuterTune/OuterTune) with a focus on YouTube Music playback stability, lyrics, navigation, and local music playback.

### YouTube Music playback and display

- Fixed albums with missing tracks, crashes while opening playlists, and failed search result parsing
- Fixed the "Source error 2004" issue that could block YouTube Music playback
- Improved YouTube Music thumbnail resolution
- Fixed a crash that could occur when opening playlists or albums while their data was being updated
- Fixed m3u playlist import crashes and improved YouTube song matching

### Lyrics

- Uses LrcLib and caption tracks to improve lyrics matching and loading speed
- Adds a lyrics toggle button to the now-playing action bar
- Added SimpMusic and BetterLyrics as lyrics providers
- Queries enabled providers in parallel with timeouts
- Fetches lyrics in the playback service even while the lyrics panel is closed

### Navigation and menus

- Adjusted bottom navigation so tab switching and re-tapping the active tab behave more naturally
- Fixed issues with the search bar, sorting, and list refreshes on the Folders screen
- Replaced the persistent search bar on tab screens with a top icon row (search, history, stats, settings, and more)
- Added swipe-to-skip to the mini player

### Local music playback

- Improved tag reading, song linking, and gapless playback for local music
- Fixed the album song count shown on album screens
- Added a Local tab for browsing on-device songs, albums, artists, and playlists with filters and search

### Display and settings

- Improved the tablet UI
- Automatically detects the system contrast setting on Android 14 and later
- Added custom accent colors
- Added a "keep audio focus" player setting
- Added a home screen grid showing your recent YouTube Music activity when signed in
- Added the current queue's name to the player's queue handle
- Replaced the account icon with the signed-in account's profile image
- Reorganized the settings screens, merging Appearance and Interface into "Appearance and controls" and adding a top-level Privacy screen

### Playback and downloads

- Added a sleep timer that fades out and fully stops playback
- Added a Wi-Fi-only download toggle

### Internal libraries and build tooling

- Updated Kotlin, KSP, NewPipeExtractor, Ktor, Android Gradle Plugin, Gradle, and related tooling

## Attribution

Thanks to all our contributors! Check them out [here](https://github.com/OuterTune/OuterTune/graphs/contributors)

[z-huang/InnerTune](https://github.com/z-huang/InnerTune) for providing an awesome base for this fork, none of this
would have been possible without it.

[Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet) for inspiration of a local music player
experience done right.

[Gramophone](https://github.com/FoedusProgramme/Gramophone) for emotional support, and a legendary lyrics parser

## Disclaimer

This project and its contents are not affiliated with, funded, authorized, endorsed by, or in any
way associated with YouTube, Google LLC or any of its affiliates and subsidiaries.

Any trademark, service mark, trade name, or other intellectual property rights used in this project
are owned by the respective owners.