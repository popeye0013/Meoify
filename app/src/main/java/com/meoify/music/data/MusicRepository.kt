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
        val root = innerTube.browse(playlistId)
        return YTMusicParser.parsePlaylist(root, playlistId)
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
            val playlists = YTMusicParser.parseLibraryPlaylists(root)
            if (playlists.isNotEmpty()) return playlists
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
     * Per-play YouTube tracking URL for the signed-in account, or null when
     * not signed in. Used only to register listens in YouTube Music history.
     */
    suspend fun historyTrackingUrl(videoId: String): String? {
        if (!cookiesPresent) return null
        return runCatching { innerTube.historyTrackingUrl(videoId) }.getOrNull()
    }

    /** Sends the history ping. Returns the HTTP status, or 0 on an invalid URL. */
    suspend fun submitHistoryPlayback(trackingBaseUrl: String, cpn: String): Int =
        runCatching { innerTube.submitHistoryPlayback(trackingBaseUrl, cpn) }.getOrDefault(0)

    private val lyricsCache = mutableMapOf<String, String>()

    private companion object {
        const val HOME_TTL_MS = 5 * 60 * 1000L
        const val FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val FILTER_ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val FILTER_ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
        const val FILTER_PLAYLISTS = "EgWKAQIoAWoKEAkQChAFEAMQBA%3D%3D"
    }
}
