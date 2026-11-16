# NewPipeExtractor compatibility patch

Meoify uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
to resolve YouTube Music audio streams. Version `v0.27.1` is compiled against
JDK 11 and calls a few APIs that Android only added in API 33/34:

| Call | Added in | Replacement |
| --- | --- | --- |
| `Collectors.toUnmodifiableList()` | Android 13 (API 33) | `Collectors.toList()` |
| `URLEncoder.encode(String, Charset)` | Android 13 (API 33) | `encode(String, String)` |
| `URLDecoder.decode(String, Charset)` | Android 13 (API 33) | `decode(String, String)` |
| `Stream.toList()` | Android 14 (API 34) | `collect(Collectors.toList())` |

Android's D8 backports `List.of`, `Map.of`, `Optional.isEmpty` and friends,
but not these four. Without the patch, tapping a song on Android 8–12 throws
`NoSuchMethodError` inside the extractor and closes the app.

`app/libs/NewPipeExtractor-v0.27.1-patched.jar` is the official jar with those
call sites rewritten. Regenerate it with:

```bash
tools/patch-newpipe-extractor/patch.sh
```

The script takes the original jar as an optional first argument and writes to
`app/libs/` by default. It uses the ASM jar bundled with the local Gradle
distribution, so no network access is required.
