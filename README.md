# Meoify

A native, unofficial YouTube Music client for Android with a Material 3 Expressive interface, built with Kotlin and Jetpack Compose.

Inspired by the layout and feature set of [LastWave](https://github.com/Clash-Projects/LastWave-native), focused on a clean phone-sized experience and a small, hackable codebase.

## Download

Grab the latest APK from the [Releases page](https://github.com/popeye0013/Meoify/releases/latest). The release build installs like any other app and upgrades in place over older builds.

| Version | Package | Min Android |
|---|---|---|
| 1.4.0 | `com.meoify.music` | 8.0 (API 26) |

## Features

- **Home feed** — personalised greeting, quick picks, made-for-you mixes, trending songs, new releases, top artists and charts, with pull to refresh.
- **Search** — songs, albums, artists and playlists, live suggestions, recent searches and genre browsing.
- **Library** — liked songs, local playlists (create / rename / delete) with list or grid layout, saved albums and recently played tracks.
- **Your YouTube Music playlists** — when signed in, the Library tab lists the playlists saved to your own YouTube Music account and opens them in the player.
- **Player** — rounded animated album canvas with a gradient blend, tap for full-screen edge-to-edge artwork, thin seek bar with a large tinted thumb, circular close/overflow header buttons, heart and lyrics circles, an Audio pill that switches quality, a wide accent play/pause pill, and bottom shuffle / mute + video / repeat controls, plus synced lyrics with a rotating artwork backdrop, queue management and a mini player.
- **Navigation** — floating pill navigation bar that expands the active tab into a labelled pill.
- **Synced lyrics** — LRC lyrics from LRCLIB with auto-scroll and tap-to-seek, plus a plain-text fallback.
- **Playback** — ExoPlayer/Media3 background playback with lockscreen and notification controls.
- **Accounts** — optional YouTube Music web sign-in for a personalised home feed.
- **Profile & settings** — your avatar in the home header opens a single page with your YouTube Music account, name/email, library stats, sign in/out and every setting in one place.
- **Local profile** — people who don't want to sign in can set a name and profile picture that is used across the home header and settings page.
- **Backup & restore** — export your local playlists and local profile to a human-readable `.json` file and restore them later, with a confirmation before a restore replaces anything.
- **Developer card** — the settings screen credits the developer with a live GitHub avatar and links to Telegram and GitHub.
- **Support** — a donation card with a UPI ID, copy button and a one-tap "Donate with UPI" intent that opens Google Pay, PhonePe, Paytm or any other UPI app.
- **Material 3** — light/dark/system theme, pure black, dynamic colour on Android 12+, expressive rounded surfaces and Google Sans Flex typography.
- **Sharing** — share songs to other apps and open album/artist pages from the track menu.

## Credits

Built by **Akash** ([@popeye0013](https://github.com/popeye0013)).

- GitHub: https://github.com/popeye0013
- Telegram: https://t.me/popeye0013

Donations are optional and go straight to the developer via UPI: `slalakash123@oksbi`.

Audio is streamed from YouTube Music. The app does not host, download or redistribute audio. It is not affiliated with YouTube or Google.

## Build

Requirements: JDK 17 and Android SDK 36.

```bash
gradle assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Media3 / ExoPlayer
- OkHttp + InnerTube (WEB_REMIX)
- NewPipeExtractor for stream resolution
- Coil, DataStore, kotlinx.serialization
- LRCLIB for synced lyrics
