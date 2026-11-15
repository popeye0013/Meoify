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
import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.remote.StreamResolver
import com.meoify.music.playback.HistorySync
import com.meoify.music.playback.PlayerConnection

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val library = LibraryStore(appContext)
    val repository = MusicRepository(settings)
    val coverArt = CoverArtResolver(repository)
    val ytLibrary = YtLibrarySync(repository, library)
    val streamResolver = StreamResolver(appContext, settings)
    val player = PlayerConnection(appContext, streamResolver, library, coverArt)
    val historySync = HistorySync(player, repository, settings)
}

class MeoifyApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
}
