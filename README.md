# AsterTune

**A Material Design 3 YouTube Music client and local media player for Android**

<img src="https://github.com/yuuichi-s/AsterTune/raw/dev/assets/astertune.png" width="100" />


[![Latest release](https://img.shields.io/github/v/release/yuuichi-s/AsterTune?include_prereleases)](https://github.com/yuuichi-s/AsterTune/releases)
[![License](https://img.shields.io/github/license/yuuichi-s/AsterTune)](https://www.gnu.org/licenses/gpl-3.0)

[<img src="assets/badge_github.png" alt="Get it on GitHub" height="40">](https://github.com/yuuichi-s/AsterTune/releases/latest)
<!--
[<img src="assets/badge_obtainium.png" alt="Get it on Obtainium" height="40">](https://github.com/yuuichi-s/AsterTune/releases/latest)
[<img src="assets/badge_fdroid.svg" alt="Get it on F-Droid" height="40">](https://f-droid.org/packages/io.github.yuuichi_s.astertune/)
[<img src="assets/IzzyOnDroidButtonGreyBorder.svg" alt="Get it on IzzyOnDroid" height="40">](https://apt.izzysoft.de/fdroid/index/apk/io.github.yuuichi_s.astertune)
-->

[English](README.md) | [日本語](README_ja.md)

## Overview

AsterTune is an Android application based on [OuterTune](https://github.com/OuterTune/OuterTune).<br />
Built with Material Design 3, it combines the functionality of a local media player and a YouTube Music client.

To transfer your data from OuterTune, see [Migrating from OuterTune to AsterTune](#migrating-from-outertune-to-astertune).

## Features

- YouTube Music
    - Seamless playback of songs from YouTube Music, with no ads and support for background playback
    - Synchronization with the library linked to your YouTube Music account
    - Song downloads for offline playback

- Local music playback
    - Playback of audio files stored on your device, including MP3, OGG, and FLAC
    - A custom metadata extraction process instead of MediaStore, allowing multi-value and specialized tags to be read correctly
    - Local and YouTube Music songs in the same queue
    - Android Auto support

- Lyrics
    - Synced lyrics, including word-level formats such as LRC and TTML
    - Lyrics from LrcLib, KuGou, SimpMusic, BetterLyrics, and YouTube caption tracks
    - Selectable lyrics sources, with enabled sources queried in parallel
    - Lyrics import and editing


## Migrating from OuterTune to AsterTune

You can transfer your data using the app's built-in backup feature.

> [!NOTE]
> Backups include the library database and app settings.
> Downloaded audio files are not included, so you will need to download them again after restoring the backup.

1. In OuterTune, open **Settings → Backup and Restore**, tap **Backup**, and save the backup file.
2. In AsterTune, open **Settings → Backup and Restore**, tap **Restore**, and select the backup file you saved.


## Improvements in This Fork

<details>
<summary>Based on OuterTune v0.10.1, this fork focuses on improving YouTube Music playback stability, lyrics, usability, and local music playback.</summary>

### YouTube Music Playback and Display

- Fixed issues with missing album tracks, crashes when viewing playlists, and failures when retrieving search results
- Resolved the “Source error 2004” issue that prevented YouTube Music playback
- Improved thumbnail image resolution
- Fixed a crash that could occur when opening a playlist or album while its data was being updated
- Fixed crashes when importing m3u playlists and improved matching with YouTube songs

### Lyrics

- Improved lyrics retrieval accuracy and display speed using LrcLib and caption tracks
- Added a lyrics toggle button to the playback screen's action bar
- Added SimpMusic and BetterLyrics as lyrics providers
- Queries enabled providers in parallel with timeouts
- Retrieves lyrics through the playback service even while the lyrics panel is closed

### App Navigation and Menus

- Adjusted bottom navigation to make switching tabs and re-tapping the active tab feel more natural
- Fixed issues with the search bar, sorting, and list refreshes on the Folders screen
- Replaced the persistent search bar at the top of tab screens with a row of icons for search, history, statistics, settings, and other actions
- Added the ability to swipe left or right on the mini player to move to the previous or next song

### Local Music Playback

- Improved metadata reading, link handling, and gapless playback for local songs
- Fixed an incorrect song count on album screens
- Added a Local tab for browsing on-device songs, albums, artists, and playlists with filtering and search

### Display and Settings

- Improved the tablet UI
- Automatically detects the system contrast setting on Android 14 and later
- Added custom accent colors
- Added a player setting to keep audio focus
- Added cards showing recent YouTube Music activity on the Home screen when signed in
- Added the current queue name to the handle used to open the player's queue
- Replaced the account icon with the signed-in account's profile image
- Reorganized the Settings screen by merging Appearance and Interface into “Appearance and Controls” and adding Privacy as a top-level section

### Playback and Downloads

- Added a sleep timer that fades out and stops playback completely
- Added a toggle to download only when connected to Wi-Fi

</details>

## Screenshots

### Smartphone

| | | | |
|---|---|---|---|
| <img src="assets/gallery/homepage.png" height="480" /> | <img src="assets/gallery/player.png" height="480" /> | <img src="assets/gallery/lyrics.png" height="480" /> | <img src="assets/gallery/queue_expanded.png" height="480" /> |
| Home | Player | Lyrics | Queue |

### Tablet

| | |
|---|---|
| <img src="assets/gallery/tablet_home.png" height="240" /> | <img src="assets/gallery/tablet_lyrics_library_dark.png" height="240" /> |
| Home | Lyrics dark |
| <img src="assets/gallery/tablet_lyrics_library.png" height="240" /> | <img src="assets/gallery/tablet_queue_home_light.png" height="240" /> |
| Lyrics light | Queue |

## Submitting Translations

We use Weblate to translate AsterTune. For more details or to submit translations, visit our [Weblate page](https://hosted.weblate.org/projects/yuuichi-s-astertune/).

<a href="https://hosted.weblate.org/projects/yuuichi-s-astertune/">
<img src="https://hosted.weblate.org/widget/yuuichi-s-astertune/multi-auto.svg" alt="Translation status" />
</a>

## Credits

| Project | Description |
|---|---|
| [OuterTune](https://github.com/OuterTune/OuterTune/) | Upstream project |
| [InnerTune](https://github.com/z-huang/InnerTune) | Original foundation of OuterTune |

## Special Thanks

| Project | Description |
|---|---|
| [Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet) | Inspiration for the local media player experience |
| [Gramophone](https://github.com/FoedusProgramme/Gramophone) | Lyrics parser |

## Building from Source

```bash
# Core (.m4a files cannot be played)
./gradlew assembleCoreDebug

# Full
./gradlew assembleFullDebug
```

For detailed instructions, see [CONTRIBUTING.md](CONTRIBUTING.md).

> [!NOTE]
> The Markdown documentation will be updated progressively.

## Disclaimer

This project and its contents are not affiliated with, funded, authorized, or endorsed by YouTube, Google LLC, or any of their affiliates or subsidiaries.

Any trademarks, service marks, trade names, or other intellectual property rights used in this project belong to their respective owners.
