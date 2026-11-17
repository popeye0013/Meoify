package com.meoify.music.data

import com.meoify.music.data.model.DetailPage
import com.meoify.music.data.model.HomeFeed
import com.meoify.music.data.model.MediaCard
import com.meoify.music.data.model.SearchResults
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork
import com.meoify.music.data.remote.ArtworkCandidate
import com.meoify.music.data.remote.InnerTube
import com.meoify.music.data.remote.MusicArtworkMatch
import com.meoify.music.data.remote.MusicApiException
import com.meoify.music.data.remote.YTMusicParser
import com.meoify.music.data.remote.YTMusicParser.SearchFilter
import com.meoify.music.data.remote.YTMusicParser.findAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.meoify.music.data.local.SettingsStore
import java.util.concurrent.TimeUnit

class MusicRepository(private val settings: com.meoify.music.data.local.SettingsStore? = null) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private val innerTube = InnerTube(http)
    private val lyricsApi = com.meoify.music.data.remote.LyricsApi(http)
    private val spotifyArtwork = com.meoify.music.data.remote.SpotifyArtwork(http)
    private val appleArtwork = com.meoify.music.data.remote.AppleArtwork(http)
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cookiesPresent = false

    init {
        settings?.let { store ->
            settingsScope.launch {
                store.settings.collectLatest { s ->
                    applyCookies(s.ytCookies)
                }
            }
        }
    }

    /** Lets the login flow hand fresh cookies over without waiting for DataStore. */
    fun applyCookies(raw: String?) {
        cookiesPresent = !raw.isNullOrBlank()
        innerTube.setCookies(raw)
    }
    private val homeMutex = Mutex()
    private val albumArtCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val albumArtLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var cachedHome: Pair<HomeFeed, Long>? = null

    suspend fun home(force: Boolean = false): HomeFeed = homeMutex.withLock {
        cachedHome?.takeIf { !force && System.currentTimeMillis() - it.second < HOME_TTL_MS }?.let {
            return it.first
        }

        val feed = coroutineScope {
            val homeDeferred = async { innerTube.browse("FEmusic_home") }
            val extras = supervisorScope {
                listOf(
                    async { runCatching { innerTube.browse("FEmusic_new_releases") }.getOrNull() },
                    async { runCatching { innerTube.browse("FEmusic_charts") }.getOrNull() },
                ).map { it.await() }
            }

            val homeRoot = homeDeferred.await()
            val (quickPicks, communityPlaylists) = YTMusicParser.parseHome(homeRoot)
            val newReleases = extras.getOrNull(0)?.let { YTMusicParser.parseNewReleases(it) }.orEmpty()
            val chartRoot = extras.getOrNull(1)
            val charts = chartRoot?.let { YTMusicParser.parseCharts(it) }
            // Some accounts never receive YouTube Music's Quick picks shelf.
            // The charts page always has its top songs, so top the list up
            // with those rather than hiding Quick picks and Trending now.
            val chartTracks = chartRoot?.let { YTMusicParser.parseTracks(it) }.orEmpty()
            val resolvedQuickPicks = (quickPicks + chartTracks)
                .distinctBy { it.id }
                .take(30)
            HomeFeed(
                quickPicks = resolvedQuickPicks,
                communityPlaylists = communityPlaylists,
                newReleases = newReleases,
                topArtists = charts?.first.orEmpty(),
                chartPlaylists = charts?.second.orEmpty(),
            )
        }

        if (feed.quickPicks.isEmpty() && feed.communityPlaylists.isEmpty() &&
            feed.newReleases.isEmpty() && feed.topArtists.isEmpty()
        ) {
            throw MusicApiException("YouTube Music didn't return any content. Pull down to try again.")
        }

        cachedHome = feed to System.currentTimeMillis()
        return feed
    }

    suspend fun search(query: String, filter: SearchFilter): SearchResults {
        val params = when (filter) {
            SearchFilter.SONGS -> FILTER_SONGS
            SearchFilter.ALBUMS -> FILTER_ALBUMS
            SearchFilter.ARTISTS -> FILTER_ARTISTS
            SearchFilter.PLAYLISTS -> FILTER_PLAYLISTS
        }
        val root = innerTube.search(query, params)
        val parsed = YTMusicParser.parseSearch(root, filter)
        return SearchResults(
            query = query,
            tracks = parsed.tracks,
            albums = parsed.albums,
            artists = parsed.artists,
            playlists = parsed.playlists,
        )
    }

    suspend fun suggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return runCatching { YTMusicParser.parseSuggestions(innerTube.suggestions(query)) }
            .getOrDefault(emptyList())
    }

    suspend fun album(albumId: String): DetailPage {
        val root = innerTube.browse(albumId)
        return YTMusicParser.parseAlbum(root, albumId)
    }

    suspend fun playlist(playlistId: String): DetailPage {
        val bare = playlistId.removePrefix("VL")
        val candidates = listOf("VL$bare", bare).distinct()
        var fallback: DetailPage? = null
        for (browseId in candidates) {
            val root = runCatching { innerTube.browse(browseId) }.getOrNull() ?: continue
            val page = runCatching { YTMusicParser.parsePlaylist(root, playlistId) }.getOrNull() ?: continue
            if (page.tracks.isNotEmpty()) {
                // Long playlists arrive in pages of ~100; keep asking for the
                // next batch so the whole playlist is visible.
                return page.copy(tracks = withContinuations(root, page.tracks))
            }
            fallback = page
        }
        return fallback ?: throw MusicApiException("Couldn't load this playlist")
    }

    /** Appends every continuation row to [initial], up to a sane cap. */
    private suspend fun withContinuations(
        firstPage: JsonObject,
        initial: List<Track>,
        max: Int = 1500,
    ): List<Track> {
        val tracks = initial.toMutableList()
        var token = YTMusicParser.nextContinuation(firstPage)
        var rounds = 0
        while (token != null && tracks.size < max && rounds < 40) {
            val next = runCatching { innerTube.browseContinuation(token) }.getOrNull() ?: break
            val more = YTMusicParser.parseTracks(next)
            if (more.isEmpty()) break
            tracks += more
            token = YTMusicParser.nextContinuation(next)
            rounds++
        }
        return tracks.distinctBy { it.id }
    }

    /** High-resolution square cover art for an album, or null when unavailable. */
    suspend fun albumArtwork(albumId: String): String? {
        albumArtCache[albumId]?.let { return it }
        // Lock per album instead of around the whole cache: resolving covers
        // for a screen full of tracks then happens in parallel.
        val mutex = albumArtLocks.getOrPut(albumId) { Mutex() }
        return mutex.withLock {
            albumArtCache[albumId]?.let { return@withLock it }
            val url = runCatching { album(albumId).thumbnailUrl }.getOrNull()
                ?.let(com.meoify.music.data.remote.Artwork::songArt)
            if (!url.isNullOrBlank()) {
                if (albumArtCache.size > 400) albumArtCache.clear()
                if (albumArtLocks.size > 400) albumArtLocks.clear()
                albumArtCache[albumId] = url
            }
            url
        }
    }

    /**
     * Square album artwork for a song from a music catalogue.
     *
     * Spotify is tried first; Apple's public catalogue is the keyless backup
     * when Spotify search is unavailable or rate limited. If neither has a
     * match, YouTube Music itself is searched for the song and only a result
     * carrying real album art is used. A music-video frame is never returned.
     */
    suspend fun musicArtwork(track: Track): String? =
        spotifyArtwork.cover(track)
            ?: appleArtwork.cover(track)
            ?: youTubeArtwork(track)

    /**
     * Finds album art from a YouTube Music search result.
     *
     * Search and library rows can be video uploads whose thumbnail is a video
     * still. Their title and uploader still name the song and artist, so the
     * catalogue is searched again and only results with square cover art are
     * kept. The relaxed match below lets a video title such as
     * `Kesariya - Arijit Singh Live` use the studio cover, while reaction
     * videos, podcasts and long compilations are still rejected.
     */
    private suspend fun youTubeArtwork(track: Track): String? = runCatching {
        if (track.durationMs > 20 * 60_000L) return@runCatching null
        val core = MusicArtworkMatch.coreTitle(track.title)
            .ifBlank { track.title }
        if (core.isBlank()) return@runCatching null

        val sameTitle = MusicArtworkMatch.normalize(core) ==
            MusicArtworkMatch.normalize(track.title)
        val withArtist = listOf(core, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val queries = buildList {
            if (!sameTitle) add(core)
            add(withArtist)
            if (sameTitle) add(core)
        }.filter { it.isNotBlank() }
            .distinctBy { MusicArtworkMatch.normalize(it) }

        val candidates = mutableListOf<ArtworkCandidate>()
        for (query in queries) {
            val results = runCatching { search(query, SearchFilter.SONGS) }.getOrNull() ?: continue
            candidates += results.tracks.mapNotNull { result ->
                val cover = Artwork.songArt(result.thumbnailUrl)
                    ?: return@mapNotNull null
                ArtworkCandidate(
                    title = result.title,
                    artist = result.artist,
                    album = result.album,
                    durationMs = result.durationMs,
                    artworkUrl = cover,
                )
            }
            if (candidates.isNotEmpty()) break
        }
        if (candidates.isEmpty()) return@runCatching null

        MusicArtworkMatch.best(track, candidates)?.let {
            return@runCatching it.artworkUrl
        }

        val titleText = MusicArtworkMatch.words(track.title)
        val fullText = MusicArtworkMatch.words(
            track.title + " " + track.artist,
        )
        candidates.firstOrNull { candidate ->
            val candidateTitle = MusicArtworkMatch.normalize(candidate.title)
            val candidateArtist = MusicArtworkMatch.normalize(candidate.artist)
            candidateTitle.isNotBlank() &&
                candidateArtist.isNotBlank() &&
                titleText.containsPhrase(candidateTitle) &&
                fullText.containsArtistPhrase(candidateArtist) &&
                durationsFit(track.durationMs, candidate.durationMs)
        }?.artworkUrl
    }.getOrNull()

    private fun String.containsPhrase(phrase: String): Boolean =
        " $this ".contains(" $phrase ")

    /** True when an artist's name (or a two-word part of it) appears in [text]. */
    private fun String.containsArtistPhrase(artist: String): Boolean {
        val tokens = artist.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        if (tokens.size == 1) return " $this ".contains(" ${tokens[0]} ")
        return tokens.windowed(2).any { (first, second) ->
            " $this ".contains(" $first $second ")
        }
    }

    private fun durationsFit(first: Long, second: Long): Boolean =
        first <= 0L || second <= 0L || kotlin.math.abs(first - second) <= 300_000L

    /**
     * The playlists saved in the signed-in person's YouTube Music library.
     * `FEmusic_liked_playlists` is the dedicated page; `FEmusic_library_landing`
     * is the fallback for accounts where the first page is empty or prompts for
     * sign-in. Returns an empty list when nobody is signed in.
     */
    suspend fun libraryPlaylists(): List<MediaCard> {
        if (!cookiesPresent) return emptyList()
        val sources = listOf("FEmusic_liked_playlists", "FEmusic_library_landing")
        for (source in sources) {
            val root = runCatching { innerTube.browse(source) }.getOrNull() ?: continue
            val playlists = YTMusicParser.parseLibraryPlaylists(root).toMutableList()
            if (playlists.isEmpty()) continue

            var token = YTMusicParser.nextContinuation(root)
            var rounds = 0
            while (token != null && playlists.size < 200 && rounds < 10) {
                val next = runCatching { innerTube.browseContinuation(token) }.getOrNull() ?: break
                val more = YTMusicParser.parseLibraryPlaylists(next)
                if (more.isEmpty()) break
                playlists += more
                token = YTMusicParser.nextContinuation(next)
                rounds++
            }
            return playlists.distinctBy { it.id }
        }
        return emptyList()
    }

    suspend fun artist(artistId: String): DetailPage {
        val root = innerTube.browse(artistId)
        return YTMusicParser.parseArtist(root, artistId)
    }

    suspend fun lyrics(videoId: String): String? {
        return lyricsCache[videoId]?.let { return it }
        val next = innerTube.next(videoId)
        val lyricsId = next.findAll("browseId").mapNotNull { it.toString().trim('"') }
            .firstOrNull { it.startsWith("MPLYt") }
            ?: return null
        val root = innerTube.browse(lyricsId)
        val text = YTMusicParser.parseLyrics(root) ?: return null
        if (lyricsCache.size > 30) lyricsCache.clear()
        lyricsCache[videoId] = text
        return text
    }

    suspend fun trackLyrics(track: Track): com.meoify.music.data.remote.TrackLyrics {
        val lrc = runCatching { lyricsApi.load(track) }.getOrDefault(com.meoify.music.data.remote.TrackLyrics())
        if (!lrc.isEmpty) return lrc
        val fallback = runCatching { lyrics(track.id) }.getOrNull()
        return com.meoify.music.data.remote.TrackLyrics(plain = fallback)
    }

    suspend fun relatedTracks(track: Track): List<Track> = runCatching {
        val results = search("${track.artist} ${track.title}", SearchFilter.SONGS)
        results.tracks.filterNot { it.id == track.id }.take(20)
    }.getOrDefault(emptyList())

    /**
     * Reads the signed-in account from YouTube Music. Returns null when we
     * are not signed in or the response did not carry an account section.
     */
    suspend fun account(): com.meoify.music.data.model.AccountInfo? {
        if (!cookiesPresent) return null
        return runCatching {
            com.meoify.music.data.remote.YTMusicParser.parseAccount(innerTube.accountMenu())
        }.getOrNull()
    }

    /** True when the app currently holds YouTube Music cookies. */
    val signedIn: Boolean get() = cookiesPresent

    /**
     * How many songs the account has played recently. YouTube Music exposes
     * this as the History page; we only need the number for the profile card.
     */
    suspend fun playedCount(limit: Int = 500): Int {
        if (!cookiesPresent) return 0
        return runCatching {
            val root = innerTube.browse("FEmusic_history")
            val tracks = YTMusicParser.parseTracks(root).toMutableList()
            var token = YTMusicParser.nextContinuation(root)
            var rounds = 0
            while (token != null && tracks.size < limit && rounds < 5) {
                val next = innerTube.browseContinuation(token)
                val more = YTMusicParser.parseTracks(next)
                if (more.isEmpty()) break
                tracks += more
                token = YTMusicParser.nextContinuation(next)
                rounds++
            }
            tracks.distinctBy { it.id }.size
        }.getOrDefault(0)
    }

    /**
     * Songs in the account's Liked music playlist, newest first. Returns null
     * when the request failed so callers can avoid overwriting a good mirror
     * with an empty list.
     */
    suspend fun likedSongs(limit: Int = 300): List<Track>? {
        if (!cookiesPresent) return null
        return runCatching {
            val root = innerTube.browse("VLLM")
            val tracks = YTMusicParser.parseTracks(root).toMutableList()
            var token = YTMusicParser.nextContinuation(root)
            var rounds = 0
            while (token != null && tracks.size < limit && rounds < 10) {
                val next = innerTube.browseContinuation(token)
                val more = YTMusicParser.parseTracks(next)
                if (more.isEmpty()) break
                tracks += more
                token = YTMusicParser.nextContinuation(next)
                rounds++
            }
            tracks.distinctBy { it.id }.take(limit)
        }.getOrNull()
    }

    suspend fun setSongLiked(videoId: String, liked: Boolean): Boolean {
        if (!cookiesPresent) return false
        return runCatching { innerTube.rateSong(videoId, liked) }.getOrDefault(false)
    }

    suspend fun createPlaylist(
        title: String,
        videoIds: List<String> = emptyList(),
    ): String? {
        if (!cookiesPresent) return null
        return runCatching { innerTube.createPlaylist(title, videoIds) }.getOrNull()
    }

    suspend fun deletePlaylist(playlistId: String): Boolean {
        if (!cookiesPresent) return false
        return runCatching { innerTube.deletePlaylist(playlistId) }.getOrDefault(false)
    }

    suspend fun renamePlaylist(playlistId: String, title: String): Boolean {
        if (!cookiesPresent) return false
        return runCatching { innerTube.renamePlaylist(playlistId, title) }.getOrDefault(false)
    }

    suspend fun addSongToPlaylist(playlistId: String, videoId: String): Boolean {
        if (!cookiesPresent) return false
        return runCatching { innerTube.addToPlaylist(playlistId, videoId) }.getOrDefault(false)
    }

    /**
     * Per-play YouTube tracking URL for the signed-in account, or null when
     * not signed in. Used only to register listens in YouTube Music history.
     */
    suspend fun historyPlayback(videoId: String): InnerTube.HistoryPlayback? {
        if (!cookiesPresent) return null
        return runCatching { innerTube.historyPlayback(videoId) }.getOrNull()
    }

    /** Sends the history ping. Returns the HTTP status, or 0 on an invalid URL. */
    suspend fun submitHistoryPlayback(
        trackingBaseUrl: String,
        cpn: String,
        visitorData: String? = null,
    ): Int = runCatching {
        innerTube.submitHistoryPlayback(trackingBaseUrl, cpn, visitorData)
    }.getOrDefault(0)

    private val lyricsCache = mutableMapOf<String, String>()

    private companion object {
        const val HOME_TTL_MS = 5 * 60 * 1000L
        const val FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val FILTER_ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val FILTER_ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
        const val FILTER_PLAYLISTS = "EgWKAQIoAWoKEAkQChAFEAMQBA%3D%3D"
    }
}
