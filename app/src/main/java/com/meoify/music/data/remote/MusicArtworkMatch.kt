package com.meoify.music.data.remote

import com.meoify.music.data.model.Track
import java.text.Normalizer

/** One search result from a music catalogue, reduced to what artwork needs. */
data class ArtworkCandidate(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String,
)

/**
 * Picks the catalogue track that really is the song being played.
 *
 * Search results often contain karaoke versions, tributes and live edits next
 * to the studio recording. Title, artist, album and duration are scored so the
 * cover belongs to the same song, and cover versions are rejected outright.
 */
object MusicArtworkMatch {

    private const val MIN_SCORE = 55

    private val BRACKETED = Regex("""[\(\[\{][^)\]\}]*[)\]\}]""")
    private val FEATURING = Regex("""\b(feat|ft|featuring)\b.*$""")
    private val VERSION_SUFFIX = Regex(
        """\s[-–—]\s.*(remaster|version|edit|live|mono|stereo|deluxe|acoustic|radio).*$""",
    )
    private val DIACRITICS = Regex("""\p{M}+""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")

    private val COVER_VERSION_WORDS = listOf(
        "karaoke",
        "tribute",
        "cover band",
        "cover by",
        "cover version",
        "made famous",
        "originally performed",
        "in the style of",
    )

    private val VIDEO_DESCRIPTOR = listOf(
        "official", "lyric", "lyrics", "music video", "video", "audio",
        "visualizer", "visualiser", "full song", "full video", "jukebox",
        "live", "performance", "concert", "reaction", "podcast", "interview",
        "remix", "version", "edit", "slowed", "reverb",
    )

    /** The best candidate for [track], or null when nothing matches closely. */
    fun best(track: Track, candidates: List<ArtworkCandidate>): ArtworkCandidate? =
        candidates
            .filterNot { isCoverVersion(track, it) }
            .map { it to score(track, it) }
            .filter { it.second >= MIN_SCORE }
            .maxByOrNull { it.second }
            ?.first

    /** 0–110 score; exact title and artist comfortably clear [MIN_SCORE]. */
    fun score(track: Track, candidate: ArtworkCandidate): Int {
        val trackTitle = normalize(track.title)
        val candidateTitle = normalize(candidate.title)
        if (trackTitle.isBlank() || candidateTitle.isBlank()) return 0

        val title = similarity(trackTitle, candidateTitle)
        if (title < 0.45f) return 0

        val trackArtist = normalize(track.artist)
        val candidateArtist = normalize(candidate.artist)
        val artist = if (trackArtist.isBlank() || candidateArtist.isBlank()) {
            0.5f
        } else {
            similarity(trackArtist, candidateArtist)
        }
        // Same title by another artist is usually a different recording.
        if (trackArtist.isNotBlank() && candidateArtist.isNotBlank() && artist < 0.34f) return 0

        val album = if (track.album.isNullOrBlank() || candidate.album.isNullOrBlank()) {
            0f
        } else {
            similarity(normalize(track.album), normalize(candidate.album))
        }

        val duration = when {
            track.durationMs <= 0L || candidate.durationMs <= 0L -> 0
            kotlin.math.abs(track.durationMs - candidate.durationMs) <= 2_500L -> 10
            kotlin.math.abs(track.durationMs - candidate.durationMs) <= 10_000L -> 5
            else -> 0
        }

        return (title * 60).toInt() + (artist * 30).toInt() + (album * 10).toInt() + duration
    }

    /** Lower-case, accent-free title with featuring credits and version tags removed. */
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace(BRACKETED, " ")
            .replace(FEATURING, " ")
            .replace(VERSION_SUFFIX, " ")
            .replace(NON_WORD, " ")
            .trim()
            .replace(SPACES, " ")

    /**
     * Every word in [value], lower-cased and punctuation-free.
     *
     * Unlike [normalize] this keeps version and live descriptors, which makes
     * it suitable for finding an artist name inside a full video title.
     */
    fun words(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace(NON_WORD, " ")
            .trim()
            .replace(SPACES, " ")

    /**
     * The song name from a YouTube video title, with the video noise removed.
     *
     * Search and library rows sometimes carry full video titles such as
     * `Kesariya - Arijit Singh Live | NSCI Dome`. Catalogue search needs just
     * `Kesariya`, otherwise no result matches. Only text after a separator is
     * dropped when it looks like a video descriptor, so real song titles keep
     * their own words.
     */
    fun coreTitle(title: String): String {
        var value = title.substringBefore('|')
        value = value.replace(BRACKETED, " ")
        val parts = value.split(Regex("""\s+[-–—]\s+"""))
        val noisy = parts.withIndex()
            .lastOrNull { (index, part) ->
                index > 0 && VIDEO_DESCRIPTOR.any { part.lowercase().contains(it) }
            }
            ?.index ?: -1
        value = when {
            // "Kesariya - Arijit Singh Live" and
            // "Arijit Singh Concert - Kesariya - ... LIVE" both give Kesariya.
            noisy > 0 -> parts[noisy - 1]
            else -> value
        }
        return value.replace(SPACES, " ").trim().trim('-', '–', '—', '.', ',')
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isBlank() || b.isBlank()) return 0f
        if (a.contains(b) || b.contains(a)) return 0.85f

        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0f
        val shared = left.intersect(right).size
        return (2f * shared / (left.size + right.size)).coerceIn(0f, 1f)
    }

    private fun isCoverVersion(track: Track, candidate: ArtworkCandidate): Boolean {
        val candidateText = normalize(candidate.artist) + " " + normalize(candidate.title)
        val trackText = normalize(track.artist) + " " + normalize(track.title)
        return COVER_VERSION_WORDS.any { word ->
            candidateText.contains(word) && !trackText.contains(word)
        }
    }
}
