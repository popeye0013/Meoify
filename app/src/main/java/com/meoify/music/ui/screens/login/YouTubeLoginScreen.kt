package com.meoify.music.ui.screens.login

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meoify.music.MeoifyApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(onBack: () -> Unit, onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeoifyApplication
    var status by remember { mutableStateOf("Sign in to your YouTube Music account") }
    val scope = rememberCoroutineScope()

    fun applyCookies(cookies: String) {
        val container = app.container
        container.repository.applyCookies(cookies)
        container.settings.setYtCookies(cookies)
        container.settings.setAccountName("YouTube Music account")
        scope.launch {
            val account = container.repository.account()
            if (account != null) container.settings.setAccountName(account.displayName)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("YouTube Music login") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val cookies = CookieManager.getInstance().getCookie("https://music.youtube.com")
                                if (cookies != null && (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID="))) {
                                    applyCookies(cookies)
                                    status = "Signed in. You can go back."
                                    onSignedIn()
                                }
                            }
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F&hl=en")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val cookies = CookieManager.getInstance().getCookie("https://music.youtube.com")
                        if (cookies != null && (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID="))) {
                            applyCookies(cookies)
                            status = "Signed in. You can go back."
                            onSignedIn()
                        } else {
                            status = "No login cookies found yet. Finish signing in first."
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Use current session")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Done")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
