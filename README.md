# Meoify

A native, unofficial YouTube Music client for Android with a Material 3 Expressive interface, built with Kotlin and Jetpack Compose.

Inspired by the layout and feature set of [LastWave](https://github.com/Clash-Projects/LastWave-native), focused on a clean phone-sized experience and a small, hackable codebase.

## Download

Grab the latest APK from the [Releases page](https://github.com/popeye0013/Meoify/releases/latest). The release build installs like any other app and upgrades in place over older builds.

| Version | Package | Min Android |
|---|---|---|
| 1.7.1 | `com.meoify.music` | 8.0 (API 26) |

## Features

- **Home feed** — personalised greeting, quick picks, made-for-you mixes, trending songs, new releases, top artists and charts, with pull to refresh.
- **Search** — a back circle and pill search field with voice input, always-visible Tracks / Albums / Artists / Playlists filters, live suggestions, recent searches and an "Explore genres & moods" grid of genre pills.
- **Library** — liked songs, local playlists (create / rename / delete) with list or grid layout, saved albums and recently played tracks.
- **Your YouTube Music playlists** — when signed in, the Library tab lists the playlists saved to your own YouTube Music account and opens them in the player.
- **Player** — square album canvas shown at its natural aspect ratio, circular close/overflow header buttons, heart and lyrics circles, a wavy seek bar with a large tinted thumb, an Audio pill that switches quality, a wide accent play/pause pill, and shuffle / mute / repeat pills, plus queue management and a mini player.
- **Lyrics** — a single `LYRICS` header with the track title, a large glowing active line that scrolls itself, a wavy seek bar and previous / play / next circles flanked by the elapsed and remaining times.
- **Responsive layout** — the player lays out side by side in landscape, artwork stays square, and no screen takes over the display or hides the system bars.
- **YouTube Music history sync** — songs you actually listen to in Meoify are reported to your signed-in YouTube Music account history, including lossless streams and downloaded files, using the same per-play tracking ping YouTube's own web player sends, with the account's visitor id so YouTube credits the listen. A listen counts after 30 seconds of real playback; pauses, buffering and seeks don't count.
- **Album & playlist pages** — circular back button, oversized rounded artwork with soft shadow, centred title and "source • N tracks" meta, Play and Shuffle pills, a wide "Save to library" button and rounded track cards with artwork, artist • album, duration and a menu.
- **Navigation** — floating pill navigation bar that expands the active tab into a labelled pill.
- **Synced lyrics** — LRC lyrics from LRCLIB with auto-scroll and tap-to-seek, plus a plain-text fallback.
- **Playback** — ExoPlayer/Media3 background playback with lockscreen and notification controls.
- **Accounts** — optional YouTube Music web sign-in for a personalised home feed.
- **Profile & settings** — your avatar in the home header opens a single page with your YouTube Music account, name/email, library stats, search in the top bar, coloured section headings, rounded cards with icon tiles and chevrons, sign in/out and every setting in one place.
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
