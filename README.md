<p align="center">
  <img src="docs/logo.svg" width="128" alt="Meoify logo" />
</p>

<h1 align="center">Meoify</h1>

<p align="center">
  <strong>A native, unofficial YouTube Music client for Android.</strong><br/>
  Material 3 Expressive · Background playback · Real album artwork · Open source
</p>

<p align="center">
  <a href="https://github.com/popeye0013/Meoify/releases/latest">
    <img src="https://img.shields.io/github/v/release/popeye0013/Meoify?style=for-the-badge&color=FF4D67&labelColor=0D0D0F" alt="Latest release" />
  </a>
  <a href="https://github.com/popeye0013/Meoify/releases">
    <img src="https://img.shields.io/github/downloads/popeye0013/Meoify/total?style=for-the-badge&color=FF4D67&labelColor=0D0D0F" alt="Total downloads" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/popeye0013/Meoify?style=for-the-badge&color=7C3AED&labelColor=0D0D0F" alt="License" />
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=0D0D0F" alt="Android 8.0+" />
</p>

---

## Download

Get the latest APK from the [Releases page](https://github.com/popeye0013/Meoify/releases/latest) and install it like any other Android app.

> Android 8.0 (API 26) or newer is required. Meoify is not on the Play Store.

## Features

- **Home** — a personalised greeting, quick picks, made-for-you mixes, trending songs, new releases, top artists and charts, with pull to refresh.
- **Search** — a clean pill search field with voice input, live suggestions, recent searches, genre shortcuts and always-visible Tracks / Albums / Artists / Playlists filters.
- **Library** — liked songs, local playlists, saved albums and recently played music, with list and grid layouts.
- **YouTube Music playlists** — when signed in, your YouTube Music playlists appear in the Library tab and open directly in the player.
- **Player** — square album artwork, a wavy seek bar, audio-quality switching, shuffle, mute, repeat, queue management and a floating mini player.
- **Real album artwork** — covers come from YouTube Music, Spotify's catalogue and Apple's catalogue, with a YouTube Music cover fallback. A music-video frame is never shown as a song's cover.
- **Synced lyrics** — LRC lyrics from LRCLIB with auto-scroll and tap-to-seek, plus a plain-text fallback.
- **Background playback** — ExoPlayer / Media3 playback with lockscreen and notification controls.
- **YouTube Music accounts** — optional web sign-in for a personalised home feed and account library. Likes and playlists sync back to the account.
- **YouTube Music history** — real listens are reported to your signed-in account using the same tracking ping as YouTube's web player.
- **Local profile** — use Meoify without an account and set a name and profile picture that appear across the app.
- **Backup & restore** — export local playlists and your local profile to a readable `.json` file and restore them later.
- **In-app updates** — Settings → About checks for a newer release and opens the APK download.
- **Material 3 Expressive** — light, dark and pure-black themes, dynamic colour on Android 12+, expressive rounded surfaces and Google Sans Flex typography.
- **Sharing** — share songs and open album or artist pages from the track menu.

## Build

Requirements: **JDK 17** and **Android SDK 36**.

```bash
git clone https://github.com/popeye0013/Meoify.git
cd Meoify
gradle assembleRelease
```

The APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

For a debug build:

```bash
gradle assembleDebug
```

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3 |
| Playback | Media3 / ExoPlayer |
| Networking | OkHttp, InnerTube (WEB_REMIX) |
| Streaming | NewPipeExtractor |
| Images | Coil 3 |
| Storage | DataStore, kotlinx.serialization |
| Lyrics | LRCLIB |
| Artwork | YouTube Music, Spotify, Apple iTunes |

## Legal disclaimer

Meoify is an **unofficial, independent project**. It is not affiliated with, endorsed by, or sponsored by YouTube, Google or any music catalogue provider.

- Meoify does **not** host, store or redistribute audio. Audio is streamed from YouTube Music.
- All trademarks, logos and content belong to their respective owners.
- You are responsible for how you use this app and for complying with the terms of service of any service you access.
- The app is provided **as is**, without warranty of any kind. Use it at your own risk.

If you are a rights holder and have a concern, please open an issue or contact the developer.

## Credits

Built by **Akash** ([@popeye0013](https://github.com/popeye0013)).

- GitHub: https://github.com/popeye0013
- Telegram: https://t.me/popeye0013

Inspired by the layout and feature set of [LastWave](https://github.com/Clash-Projects/LastWave-native).

Released under the [GNU General Public License v3.0](LICENSE).
