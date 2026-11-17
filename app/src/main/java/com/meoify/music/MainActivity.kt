package com.meoify.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meoify.music.ui.MeoifyApp
import com.meoify.music.ui.theme.MeoifyTheme

class MainActivity : ComponentActivity() {

    /** music.youtube.com link that opened the app, consumed by the UI once. */
    private var pendingLink by mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingLink = intent?.dataString

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val app = application as MeoifyApplication
            val settings by app.container.settings.settings.collectAsStateWithLifecycle()
            MeoifyTheme(settings = settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MeoifyApp(
                        container = app.container,
                        initialLink = pendingLink,
                        onLinkConsumed = { pendingLink = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = intent.dataString
    }

    override fun onStart() {
        super.onStart()
        val app = application as MeoifyApplication
        app.container.inForeground = true
        // The controller was released in onStop so a paused service could shut
        // down; reconnect if playback kept running in the background.
        app.container.player.onAppForegrounded()
    }

    override fun onStop() {
        val app = application as MeoifyApplication
        app.container.inForeground = false
        // A paused player must not keep a background service (and its
        // "active app" chip) alive once Meoify leaves the screen.
        app.container.player.onAppBackgrounded()
        super.onStop()
    }
}
