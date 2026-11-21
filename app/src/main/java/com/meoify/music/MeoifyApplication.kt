package com.meoify.music

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.meoify.music.data.CoverArtResolver
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.YtLibrarySync
import com.meoify.music.data.local.LibraryStore
import com.meoify.music.data.local.PlaybackStore
import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.remote.StreamResolver
import com.meoify.music.playback.HistorySync
import kotlinx.coroutines.flow.MutableStateFlow
import com.meoify.music.playback.ScrobbleSync
import com.meoify.music.playback.PlayerConnection
import coil3.disk.DiskCache
import coil3.disk.directory
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /**
     * True while the Meoify activity is visible. The playback service uses it
     * to decide whether an idle (paused) session should keep running.
     */
    @Volatile
    var inForeground: Boolean = false

    val settings = SettingsStore(appContext)
    val library = LibraryStore(appContext)
    val playback = PlaybackStore(appContext)
    val repository = MusicRepository(settings, library)
    val coverArt = CoverArtResolver(repository, appContext)
    val ytLibrary = YtLibrarySync(repository, library)
    val streamResolver = StreamResolver(appContext, settings)
    val player = PlayerConnection(appContext, streamResolver, library, coverArt, playback)
    // Broadcasts playlist ids whose contents changed, so open detail screens
    // can refresh instead of showing stale rows after an add/remove.
    val playlistChanges = MutableStateFlow<String?>(null)
    val historySync = HistorySync(player, repository, settings) { ytLibrary.notePlayed() }
    val scrobbleSync = ScrobbleSync(player, repository, settings)
}

class MeoifyApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Keep decoded artwork on disk so a cover seen once never has to be
        // downloaded again on mobile data or Wi-Fi.
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "artwork"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
