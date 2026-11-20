package com.meoify.music.data.remote

import com.meoify.music.data.model.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Pure catalogue matching shared by the hifi-api/Tidal and Saavn/320 kbps
 * resolvers. Kept separate so same-title/different-song regressions can be
 * unit-tested without a network or an Android context.
 */
internal object ServerTrackMatcher {

    /** Positive score means the search result is the requested recording. */
    fun score(item: JsonObject, track: Track): Int {
        val title = item.string("title").orEmpty()
        if (title.isBlank()) return 0

        val wantedTitle = normalize(track.title)
        val foundTitle = normalize(title)
        val titleHits = tokens(track.title).count { it in tokens(title) }
        if (titleHits == 0 && wantedTitle != foundTitle) return 0
        val exactTitle = wantedTitle == foundTitle

        // A different artist with the same title is a different song. This is
        // the exact case that used to play the wrong cut after a quality switch.
        val wantedArtist = normalize(track.artist)
        val foundArtist = normalize(artistOf(item))
        if (wantedArtist.isNotBlank() && foundArtist.isNotBlank()) {
            val artistOk = wantedArtist == foundArtist ||
                tokens(track.artist).any { it in tokens(artistOf(item)) }
            if (!artistOk) return 0
        }

        // A live/remix/cover of the same song is not the same recording unless
        // the requested title is already marked as that version.
        val itemAlternate = isAlternateVersion(title) || isAlternateVersion(item.string("version").orEmpty())
        val wantedAlternate = isAlternateVersion(track.title)
        if (itemAlternate != wantedAlternate) return 0

        var result = 25 * titleHits
        if (exactTitle) result += 80
        if (wantedArtist.isNotBlank()) {
            if (foundArtist.isNotBlank()) result += 60 else result -= 20
        }

        // Album agreement breaks ties between several editions of one song.
        val wantedAlbum = normalize(track.album.orEmpty())
        val foundAlbum = normalize(albumOf(item))
        if (wantedAlbum.isNotBlank() && foundAlbum.isNotBlank() &&
            (wantedAlbum == foundAlbum || wantedAlbum in foundAlbum || foundAlbum in wantedAlbum)
        ) {
            result += 20
        }

        val wantedSeconds = track.durationMs / 1000
        val foundSeconds = durationOf(item)
        if (wantedSeconds > 0 && foundSeconds > 0) {
            when (val delta = kotlin.math.abs(foundSeconds - wantedSeconds)) {
                in 0..2 -> result += 30
                in 3..10 -> result += 15
                in 11..30 -> result += 5
                else -> if (delta > 60) result -= 25
            }
        }
        return result
    }

    fun normalize(value: String): String = java.text.Normalizer
        .normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun artistOf(item: JsonObject): String {
        item.string("artist")?.let { return it }
        item["artist"]?.jsonObject?.string("name")?.let { return it }
        val names = item["artists"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { it.string("name") }
        if (!names.isNullOrEmpty()) return names.joinToString(" ")
        return item.string("primaryArtist").orEmpty()
    }

    private fun albumOf(item: JsonObject): String =
        item["album"]?.jsonObject?.string("title") ?: item.string("album").orEmpty()

    private fun durationOf(item: JsonObject): Long {
        val raw = (item["duration"] as? JsonPrimitive)?.contentOrNull ?: return 0
        raw.toLongOrNull()?.let { value ->
            // hifi-api reports seconds; some workers report milliseconds.
            return if (value > 10_000) value / 1000 else value
        }
        val parts = raw.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun isAlternateVersion(value: String): Boolean {
        val lower = value.lowercase()
        if (Regex("\\((live|remix|cover|acoustic|instrumental|karaoke|extended|radio edit|sped up|slowed|reverb)([^)]*)\\)")
                .containsMatchIn(lower)
        ) {
            return true
        }
        val clean = " ${normalize(value)} "
        return listOf(
            "remix", "cover", "karaoke", "instrumental", "acoustic",
            "remastered", "extended", "radio edit", "sped up", "slowed", "reverb",
            "live at", "live from", "live in", "live version",
        ).any { clean.contains(" $it ") }
    }

    private fun tokens(value: String): Set<String> =
        normalize(value).split(' ').filter { it.isNotBlank() }.toSet()

    // hifi-api returns artist/album as JSON objects, not strings. Reading those
    // with jsonPrimitive used to throw and silently fall back to YouTube Music.
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
