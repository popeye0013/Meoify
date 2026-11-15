package com.meoify.music.data

import com.meoify.music.data.model.DetailPage
import com.meoify.music.data.model.HomeFeed
import com.meoify.music.data.model.MediaCard
import com.meoify.music.data.model.SearchResults
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.InnerTube
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
    private val artworkMutex = Mutex()
    private val albumArtCache = mutableMapOf<String, String>()

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
            val charts = extras.getOrNull(1)?.let { YTMusicParser.parseCharts(it) }
            HomeFeed(
                quickPicks = quickPicks,
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
    suspend fun albumArtwork(albumId: String): String? = artworkMutex.withLock {
        albumArtCache[albumId]?.let { return@withLock it }
        val url = runCatching { album(albumId).thumbnailUrl }.getOrNull()
            ?.let(com.meoify.music.data.remote.Artwork::highRes)
        if (!url.isNullOrBlank()) {
            if (albumArtCache.size > 400) albumArtCache.clear()
            albumArtCache[albumId] = url
        }
        url
    }

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
