<p align="center">
  <img src="docs/logo.svg" width="116" alt="Meoify logo" />
</p>

<h1 align="center">Meoify</h1>

<p align="center">
  <strong>YouTube Music, the way you want it.</strong><br/>
  A native, unofficial YouTube Music client for Android.<br/>
  Material 3 Expressive · Background playback · Real album artwork · Synced lyrics
</p>

<p align="center">
  <a href="https://github.com/popeye0013/Meoify/releases/latest">
    <img src="https://img.shields.io/github/v/release/popeye0013/Meoify?style=for-the-badge&color=FF4D67&labelColor=0D0D0F" alt="Latest release" />
  </a>
  <a href="https://github.com/popeye0013/Meoify/releases">
    <img src="https://img.shields.io/github/downloads/popeye0013/Meoify/total?style=for-the-badge&color=FF4D67&labelColor=0D0D0F" alt="Total downloads" />
  </a>
  <a href="https://github.com/popeye0013/Meoify/stargazers">
    <img src="https://img.shields.io/github/stars/popeye0013/Meoify?style=for-the-badge&color=7C3AED&labelColor=0D0D0F" alt="Stars" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/popeye0013/Meoify?style=for-the-badge&color=7C3AED&labelColor=0D0D0F" alt="GPL-3.0 license" />
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=0D0D0F" alt="Android 8.0+" />
</p>

<p align="center">
  <a href="https://github.com/popeye0013/Meoify/releases/latest">
    <img src="https://img.shields.io/badge/Download_the_latest_APK-FF4D67?style=for-the-badge&logo=android&logoColor=white&labelColor=0D0D0F" alt="Download the latest APK" />
  </a>
</p>

<p align="center">
  <a href="#download">Download</a> ·
  <a href="#features">Features</a> ·
  <a href="#audio-quality">Audio quality</a> ·
  <a href="#build-from-source">Build</a> ·
  <a href="#tech-stack">Stack</a> ·
  <a href="#faq">FAQ</a>
</p>

---

## Download

Meoify is distributed as a signed APK on GitHub Releases.

| | |
|---|---|
| **Latest release** | [github.com/popeye0013/Meoify/releases/latest](https://github.com/popeye0013/Meoify/releases/latest) |
| **Requirements** | Android 8.0 (API 26) or newer |
| **Price** | Free and open source (GPL-3.0) |
| **Play Store** | Not available — side-load the APK |

**Install**

1. Download `Meoify-<version>.apk` from the latest release.
2. Open the file on your phone.
3. If Android asks, allow installs from your browser or file manager.
4. Open Meoify. Updates install over the top and keep your data.

> Meoify also checks for updates from **Settings → About**.

---

## Features

### Discover

- **Personalised home** — greeting, quick picks, made-for-you mixes, trending songs, new releases, top artists and charts.
- **Search that stays out of the way** — pill search field, voice input, live suggestions, recent searches and genre shortcuts.
- **Filters that persist** — switch between Tracks, Albums, Artists and Playlists without losing your query.
- **Pull to refresh** everywhere the catalogue can change.

### Play

- **Full player** — square artwork, wavy seek bar, shuffle, repeat, mute, queue management and a floating mini player.
- **Background playback** — Media3 / ExoPlayer with lockscreen and notification controls.
- **Five audio modes** — Auto, Lossless (Hi-Res), Standard (320 kbps), YouTube Music and Data Saver. See [Audio quality](#audio-quality).
- **Sleep timer** — stop playback after 5, 10, 15, 30, 45 or 60 minutes.
- **Synced lyrics** — LRC from LRCLIB with auto-scroll and tap-to-seek, plus a plain-text fallback.
- **Real album artwork** — covers from YouTube Music, Spotify and Apple's catalogue. Catalogue art is preferred; the video thumbnail is shown only when no album cover can be found, so a row is never left blank.

### Library & accounts

- **Liked songs, playlists, saved albums and history** — with list and grid layouts.
- **YouTube Music playlists** — your account playlists appear in Library and open straight in the player.
- **Optional sign-in** — web sign-in for a personalised feed and account library; likes and playlists sync back.
- **Listening history sync** — real listens are reported with the same tracking ping YouTube's web player uses.
- **Local profile** — use Meoify without an account and pick a name and avatar shown across the app.
- **Backup & restore** — export playlists and your profile to a readable `.json` file.

### Design & details

- **Material 3 Expressive** — light, dark and pure-black themes, dynamic colour on Android 12+, rounded surfaces and Google Sans Flex.
- **Quality pill on Now Playing** — shows the active mode and opens the same picker used in Settings.
- **Sharing** — share songs or jump to album and artist pages from the track menu.
- **In-app updates** — release notes in Settings and an optional home-screen prompt.

---

## Audio quality

Pick a mode in **Settings → Playback**, or tap the quality pill on the Now Playing card.

| Mode | What it does |
|---|---|
| **Auto** | Adapts to your connection — Wi-Fi keeps the best stream, mobile data stays balanced. |
| **Lossless (Hi-Res)** | Requests up to 24-bit / 192 kHz lossless FLAC. |
| **Standard (320 kbps)** | Requests up to 320 kbps MP3 / AAC. |
| **YouTube Music** | 128–256 kbps AAC / Opus, the native YouTube Music stream. |
| **Data Saver** | Low-bitrate audio to reduce mobile data use. |

> YouTube Music tops out at 256 kbps AAC/Opus. The Hi-Res and 320 kbps modes therefore use the best stream the catalogue offers — Meoify does not upsample or fake lossless audio. A new mode applies from the next track.

---

## Build from source

**Requirements:** JDK 17 and Android SDK 36.

```bash
git clone https://github.com/popeye0013/Meoify.git
cd Meoify
gradle assembleRelease
```

The signed release APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

For a debug build:

```bash
gradle assembleDebug
```

### Patched NewPipeExtractor

Streaming uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) `v0.27.1`, patched for Android 8–12. The upstream jar calls APIs that only exist on Android 13+; without the patch, playback crashes with `NoSuchMethodError`.

The patched jar lives in `app/libs/` and can be regenerated offline:

```bash
tools/patch-newpipe-extractor/patch.sh
```

See [`tools/patch-newpipe-extractor/README.md`](tools/patch-newpipe-extractor/README.md) for the full list of rewrites.

### Tests

```bash
gradle testDebugUnitTest
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose 1.9, Material 3 1.4 |
| Playback | Media3 / ExoPlayer 1.8 |
| Networking | OkHttp 4.12, InnerTube (WEB_REMIX) |
| Streaming | NewPipeExtractor 0.27.1 (patched) |
| Images | Coil 3 |
| Storage | DataStore, kotlinx.serialization |
| Lyrics | LRCLIB |
| Artwork | YouTube Music, Spotify, Apple iTunes |

### Project layout

```text
app/src/main/java/com/meoify/music/
├── data/        # InnerTube, stream resolution, artwork, settings, backup
├── playback/    # Media3 service, queue and player state
├── ui/          # Compose screens, components, navigation and theme
└── util/        # Intents, formatting and small helpers
```

---

## FAQ

<details>
<summary><strong>Is Meoify affiliated with YouTube or Google?</strong></summary>

No. Meoify is an independent, unofficial project. It is not endorsed by or sponsored by YouTube, Google or any music catalogue provider.

</details>

<details>
<summary><strong>Do I need a YouTube Music account?</strong></summary>

No. Meoify works with a local profile and local library. Signing in is optional and only needed for a personalised home feed, account playlists and history sync.

</details>

<details>
<summary><strong>Does Lossless really play FLAC?</strong></summary>

Only if the catalogue serves a lossless stream. YouTube Music currently caps at 256 kbps AAC/Opus, so Hi-Res and 320 kbps modes play the best available stream. Meoify never upsamples audio.

</details>

<details>
<summary><strong>Where does the audio come from?</strong></summary>

Audio is streamed from YouTube Music through NewPipeExtractor. Meoify does not host, store or redistribute any audio.

</details>

<details>
<summary><strong>Is my data collected?</strong></summary>

No analytics or tracking SDKs. Settings, playlists and history live on your device. Optional YouTube Music sign-in talks directly to YouTube Music.

</details>

<details>
<summary><strong>Why is the APK not on the Play Store?</strong></summary>

A third-party YouTube Music client is not permitted there. Releases are published on GitHub instead.

</details>

<details>
<summary><strong>How do I update?</strong></summary>

Use **Settings → About → Check for updates**, or install the newer APK over the current one. Your data is kept.

</details>

---

## Contributing

Issues and pull requests are welcome.

- Keep changes focused and match the existing Kotlin / Compose style.
- Run `gradle testDebugUnitTest` before opening a PR.
- For a bug report, include your Android version, Meoify version and steps to reproduce.

---

## Legal disclaimer

Meoify is an **unofficial, independent project**. It is not affiliated with, endorsed by, or sponsored by YouTube, Google or any music catalogue provider.

- Meoify does **not** host, store or redistribute audio. Audio is streamed from YouTube Music.
- All trademarks, logos and content belong to their respective owners.

If you are a rights holder and have a concern, please open an issue or contact me
