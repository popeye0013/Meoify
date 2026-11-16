package com.meoify.music.ui.screens.settings

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meoify.music.MeoifyApplication
import com.meoify.music.R
import com.meoify.music.data.UpdateChecker
import com.meoify.music.data.backup.BackupManager
import com.meoify.music.data.model.AudioQuality
import com.meoify.music.data.model.DeveloperInfo
import com.meoify.music.data.model.ThemeMode
import com.meoify.music.ui.components.ProfileAvatar
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.theme.ArtworkColorCache
import com.meoify.music.ui.viewmodel.ProfileViewModel
import com.meoify.music.ui.viewmodel.ProfileViewModelFactory
import com.meoify.music.util.copyText
import com.meoify.music.util.openUpiPayment
import com.meoify.music.util.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = (context.applicationContext as MeoifyApplication)
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    val actions = LocalAppActions.current
    val library = LocalLibrary.current
    val ytLibrary by app.container.ytLibrary.state.collectAsStateWithLifecycle()
    val signedIn = settings.ytCookies != null
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(app.container.repository),
    )
    val account by profileViewModel.account.collectAsStateWithLifecycle()
    val profileLoading by profileViewModel.loading.collectAsStateWithLifecycle()
    val historyStatus by app.container.historySync.status.collectAsStateWithLifecycle()

    // In Settings the search icon searches the settings themselves, not the
    // music catalogue.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val searchListState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    val backupManager = remember(app) {
        BackupManager(app, app.container.settings, app.container.library)
    }

    var editProfile by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.7.2" }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.filesDir, "profile").apply { mkdirs() }
                        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Couldn't read the selected image")
                        dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
                        file.absolutePath
                    }.getOrNull()
                }
                if (saved != null) {
                    app.container.settings.setLocalAvatarPath(saved)
                    actions.showSnackbar("Profile photo updated")
                } else {
                    actions.showSnackbar("Couldn't read that image")
                }
            }
        }
    }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                scope.launch {
                    val count = runCatching { backupManager.exportTo(uri) }.getOrNull()
                    actions.showSnackbar(
                        if (count != null) {
                            "Backed up $count playlist${if (count == 1) "" else "s"}"
                        } else {
                            "Backup failed — try another location"
                        },
                    )
                }
            }
        }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) restoreUri = uri
    }

    LaunchedEffect(settings.ytCookies) {
        if (settings.ytCookies != null) profileViewModel.refresh(force = true)
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            kotlinx.coroutines.delay(80)
            runCatching { searchFocus.requestFocus() }
        } else {
            searchQuery = ""
        }
    }
    LaunchedEffect(searchQuery) {
        runCatching { searchListState.scrollToItem(0) }
    }

    fun matches(vararg keys: String): Boolean {
        val query = searchQuery.trim()
        if (query.isBlank()) return true
        return keys.any { it.contains(query, ignoreCase = true) }
    }

    val accountKeys = arrayOf(
        "Account", "profile", "avatar", "email", "name", "sign in", "sign out",
        "YouTube Music", "local", "liked", "playlists", "played", "stats",
    )
    val appearanceKeys = arrayOf(
        "Appearance", "theme", "dark", "light", "system", "dynamic color",
        "colours", "colors", "wallpaper", "pure black", "OLED",
    )
    val playbackKeys = arrayOf(
        "Playback", "audio", "quality", "high", "normal", "saver", "data",
        "kbps", "history", "sync",
    )
    val libraryKeys = arrayOf("Library", "clear", "recent", "searches", "listening", "history")
    val backupKeys = arrayOf("Backup", "restore", "export", "import", "json", "file")
    val supportKeys = arrayOf("Support", "donate", "UPI", "Google Pay", "contribution", "money")
    val developerKeys = arrayOf("Developer", "telegram", "contact", "github", "source")
    val aboutKeys = arrayOf("About", "version", "update", "release", "app", "Meoify")
    val anyMatch = listOf(
        accountKeys, appearanceKeys, playbackKeys, libraryKeys,
        backupKeys, supportKeys, developerKeys, aboutKeys,
    ).any { matches(*it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search settings") },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .focusRequester(searchFocus),
                    )
                } else {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    )
                }
                Surface(
                    onClick = {
                        if (searchOpen) {
                            searchOpen = false
                            searchQuery = ""
                        } else {
                            searchOpen = true
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close settings search" else "Search settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = searchListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!anyMatch) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No settings match \"${searchQuery.trim()}\"",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Try theme, audio quality, backup or updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (matches(*accountKeys)) {
            item { SectionLabel("Account") }
            item {
                val displayName = account?.displayName
                    ?: settings.localProfileName
                    ?: settings.accountName
                    ?: "Your profile"
                ProfileCard(
                    name = displayName,
                    subtitle = account?.email
                        ?: account?.handle
                        ?: when {
                            settings.ytCookies != null -> "Signed in to YouTube Music"
                            settings.localProfileName != null -> "Local profile · not signed in"
                            else -> "No account connected"
                        },
                    avatarUrl = account?.avatarUrl
                        ?: settings.localAvatarPath?.let { File(it) },
                    initials = account?.initials
                        ?: settings.localProfileName?.trim()?.take(1)?.uppercase(),
                    signedIn = settings.ytCookies != null,
                    loading = profileLoading,
                    liked = if (signedIn && ytLibrary.ready) {
                        ytLibrary.likedCount
                    } else {
                        library.liked.size
                    },
                    playlists = if (signedIn) {
                        ytLibrary.playlists.size
                    } else {
                        library.playlists.size
                    },
                    played = if (signedIn && ytLibrary.ready) {
                        ytLibrary.playedCount
                    } else {
                        library.recent.size
                    },
                    onLiked = { actions.openLiked() },
                    onPlaylists = { actions.openLibrary() },
                    onPlayed = { actions.openLibrary() },
                    onRefresh = { profileViewModel.refresh(force = true) },
                    onEditLocal = {
                        nameDraft = settings.localProfileName.orEmpty()
                        editProfile = true
                    },
                    onSignIn = onOpenLogin,
                    onSignOut = {
                        app.container.settings.logoutYouTube()
                        profileViewModel.clear()
                        actions.showSnackbar("Signed out of YouTube Music")
                    },
                )
            }
            }

            if (matches(*appearanceKeys)) {
            item { SectionLabel("Appearance") }
            item {
                SettingsCard {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { app.container.settings.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.LIGHT -> "Light"
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Dynamic color",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Use colors from your wallpaper"
                        } else {
                            "Requires Android 12 or newer"
                        },
                        checked = settings.dynamicColor,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = { app.container.settings.setDynamicColor(it) },
                    )
                    ToggleRow(
                        icon = Icons.Filled.ColorLens,
                        title = "Pure black",
                        subtitle = "True black surfaces for OLED screens",
                        checked = settings.pureBlack,
                        onCheckedChange = { app.container.settings.setPureBlack(it) },
                    )
                }
            }
            }

            if (matches(*playbackKeys)) {
            item { SectionLabel("Playback") }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Audio quality",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AudioQuality.entries.forEachIndexed { index, quality ->
                            SegmentedButton(
                                selected = settings.quality == quality,
                                onClick = { app.container.settings.setQuality(quality) },
                                shape = SegmentedButtonDefaults.itemShape(index, AudioQuality.entries.size),
                            ) {
                                Text(
                                    when (quality) {
                                        AudioQuality.HIGH -> "High"
                                        AudioQuality.NORMAL -> "Normal"
                                        AudioQuality.SAVER -> "Saver"
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (settings.quality) {
                            AudioQuality.HIGH -> "Best available AAC stream (up to 256 kbps)."
                            AudioQuality.NORMAL -> "Balanced quality for everyday listening."
                            AudioQuality.SAVER -> "Lower bitrate to use less data."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToggleRow(
                        icon = Icons.Filled.History,
                        title = "Sync to YouTube Music history",
                        subtitle = when {
                            settings.ytCookies == null ->
                                "Sign in to YouTube Music to mirror your listening"
                            historyStatus != null -> historyStatus.orEmpty()
                            else ->
                                "Songs you listen to appear in your account history, whatever the source"
                        },
                        checked = settings.historySync,
                        enabled = settings.ytCookies != null,
                        onCheckedChange = { app.container.settings.setHistorySync(it) },
                    )
                }
            }
            }

            if (matches(*libraryKeys)) {
            item { SectionLabel("Library") }
            item {
                SettingsCard {
                    ActionRow(
                        icon = Icons.Filled.Search,
                        title = "Clear recent searches",
                        onClick = actions.clearRecentSearches,
                    )
                    ActionRow(
                        icon = Icons.Filled.History,
                        title = "Clear listening history",
                        onClick = actions.clearRecent,
                    )
                }
            }
            }

            if (matches(*backupKeys)) {
            item { SectionLabel("Backup & restore") }
            item {
                SettingsCard {
                    ActionRow(
                        icon = Icons.Filled.Backup,
                        title = "Backup",
                        subtitle = "Save your playlists and local profile",
                        onClick = { backupLauncher.launch(backupManager.suggestedFileName()) },
                    )
                    ActionRow(
                        icon = Icons.Filled.Restore,
                        title = "Restore",
                        subtitle = "Replaces local playlists and profile",
                        onClick = { restoreLauncher.launch("application/json") },
                    )
                }
            }
            }

            if (matches(*supportKeys)) {
            item { SectionLabel("Support") }
            item {
                DonateCard(
                    onDonate = {
                        val started = context.openUpiPayment(
                            upiId = DeveloperInfo.UPI_ID,
                            payeeName = DeveloperInfo.PAYEE_NAME,
                            note = DeveloperInfo.UPI_NOTE,
                        )
                        if (!started) {
                            context.copyText("UPI ID", DeveloperInfo.UPI_ID)
                            actions.showSnackbar("No UPI app found — UPI ID copied instead")
                        }
                    },
                    onCopy = {
                        context.copyText("UPI ID", DeveloperInfo.UPI_ID)
                    },
                )
            }
            }

            if (matches(*developerKeys)) {
            item { SectionLabel("Developer") }
            item {
                DeveloperCard(
                    onOpen = { url -> context.openUrl(url) },
                )
            }
            }

            if (matches(*aboutKeys)) {
            item { SectionLabel("About") }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val appIcon = remember {
                            runCatching {
                                context.packageManager
                                    .getApplicationIcon(context.packageName)
                                    .toBitmap(144, 144)
                                    .asImageBitmap()
                            }.getOrNull()
                        }
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = "Meoify icon",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Meoify",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = "Version $versionName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "An unofficial YouTube Music client built with Material 3 and Jetpack Compose. Search, stream and organise your music with a clean, modern interface.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Not affiliated with YouTube or Google. All audio is streamed from YouTube Music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ActionRow(
                        icon = Icons.Filled.Refresh,
                        title = "Check for updates",
                        subtitle = if (checkingUpdate) {
                            "Checking GitHub for a newer release…"
                        } else {
                            "Installed version $versionName"
                        },
                        onClick = {
                            if (checkingUpdate) return@ActionRow
                            checkingUpdate = true
                            scope.launch {
                                val latest = UpdateChecker.latest()
                                checkingUpdate = false
                                when {
                                    latest == null -> actions.showSnackbar(
                                        "Couldn't check for updates — try again later",
                                    )
                                    UpdateChecker.isNewer(latest.version, versionName) -> updateRelease = latest
                                    else -> actions.showSnackbar("Meoify is up to date ($versionName)")
                                }
                            }
                        },
                    )
                    ActionRow(
                        icon = Icons.Filled.Code,
                        title = "Source Code",
                        subtitle = "github.com/${DeveloperInfo.HANDLE}/Meoify",
                        onClick = { context.openUrl(DeveloperInfo.REPO_URL) },
                    )
                }
            }
            }
        }
    }

    val pendingUpdate = updateRelease
    if (pendingUpdate != null) {
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            title = { Text("Update available") },
            text = {
                Text(
                    "Meoify ${pendingUpdate.version} is available. You are on " +
                        "$versionName. Open the GitHub release to get the new APK.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateRelease = null
                        context.openUrl(pendingUpdate.url)
                    },
                ) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updateRelease = null }) { Text("Later") }
            },
        )
    }

    if (editProfile) {
        ModalBottomSheet(
            onDismissRequest = { editProfile = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(
                        name = nameDraft.ifBlank { "Profile" },
                        url = settings.localAvatarPath?.let { File(it) },
                        initials = nameDraft.trim().take(1).uppercase().ifBlank { "M" },
                        size = 64.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Shown around Meoify while you're not signed in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Display name") },
                    placeholder = { Text("Your name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { avatarPicker.launch("image/*") },
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (settings.localAvatarPath == null) "Add photo" else "Change photo")
                    }
                    if (settings.localAvatarPath != null) {
                        TextButton(
                            onClick = {
                                app.container.settings.setLocalAvatarPath(null)
                                actions.showSnackbar("Profile photo removed")
                            },
                        ) {
                            Text("Remove")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Saved with your .json backups.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { editProfile = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            app.container.settings.setLocalProfileName(nameDraft)
                            editProfile = false
                            actions.showSnackbar(
                                if (nameDraft.isBlank()) "Local profile cleared" else "Local profile saved",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    val pendingRestore = restoreUri
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces your local playlists and local profile with the contents of the selected file.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreUri = null
                        scope.launch {
                            val result = runCatching { backupManager.restoreFrom(pendingRestore) }.getOrNull()
                            actions.showSnackbar(
                                if (result != null) {
                                    "Restored ${result.playlists} playlist${if (result.playlists == 1) "" else "s"}" +
                                        if (result.profileName != null) " and local profile" else ""
                                } else {
                                    "Couldn't restore that file"
                                },
                            )
                        }
                    },
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreUri = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    name: String,
    subtitle: String,
    avatarUrl: Any?,
    initials: String?,
    signedIn: Boolean,
    loading: Boolean,
    liked: Int,
    playlists: Int,
    played: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit,
    onPlayed: () -> Unit,
    onRefresh: () -> Unit,
    onEditLocal: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val accent = ArtworkColorCache.gradientPair(name)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.first.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ),
                    )
                    .padding(start = 18.dp, end = 10.dp, top = 20.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    ProfileAvatar(
                        name = name,
                        url = avatarUrl,
                        initials = initials ?: name.take(1).uppercase(),
                        size = 76.dp,
                        ring = signedIn,
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (signedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (signedIn) Icons.Filled.Verified else Icons.Filled.Person,
                                contentDescription = null,
                                tint = if (signedIn) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(9.dp))
                    if (loading) {
                        SyncChip("Syncing account…", active = true)
                    } else {
                        SyncChip(
                            text = if (signedIn) "YouTube Music connected" else "Local profile",
                            active = signedIn,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileStat("Liked", liked, Modifier.weight(1f), onLiked)
                StatDivider()
                ProfileStat("Playlists", playlists, Modifier.weight(1f), onPlaylists)
                StatDivider()
                ProfileStat("Played", played, Modifier.weight(1f), onPlayed)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (signedIn) {
                    FilledTonalButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh")
                    }
                    TextButton(onClick = onSignOut) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                } else {
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in")
                    }
                    TextButton(onClick = onEditLocal) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit profile")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncChip(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Verified else Icons.Filled.Person,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun DeveloperCard(onOpen: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val context = LocalContext.current
                val developerPhoto = remember {
                    BitmapFactory.decodeResource(context.resources, R.drawable.meoify_developer)
                        ?.asImageBitmap()
                }
                ProfileAvatar(
                    name = DeveloperInfo.NAME,
                    url = null,
                    image = developerPhoto,
                    size = 54.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DeveloperInfo.NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "@${DeveloperInfo.HANDLE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "Developer",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SocialRow(
                label = "GitHub",
                value = "github.com/${DeveloperInfo.HANDLE}",
                icon = ImageVector.vectorResource(R.drawable.ic_github),
                brandColor = Color(0xFF24292F),
                onClick = { onOpen(DeveloperInfo.GITHUB_URL) },
            )
            SocialRow(
                label = "Telegram",
                value = DeveloperInfo.TELEGRAM_LABEL,
                icon = ImageVector.vectorResource(R.drawable.ic_telegram),
                brandColor = Color(0xFF229ED9),
                onClick = { onOpen(DeveloperInfo.TELEGRAM_URL) },
            )
        }
    }
}

@Composable
private fun SocialRow(
    label: String,
    value: String,
    icon: ImageVector,
    brandColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(brandColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.ArrowOutward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DonateCard(
    onDonate: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        Icons.Filled.CurrencyRupee,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Support Meoify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Free, no ads, no tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "A donation helps cover the time spent building features and fixing bugs. Any amount is appreciated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UPI ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = DeveloperInfo.UPI_ID,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onCopy) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy UPI ID",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDonate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Donate with UPI")
                }
                FilledTonalButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Copy")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Works with Google Pay, PhonePe, Paytm and any other UPI app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconTile(icon = icon, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsIconTile(icon: ImageVector, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconTile(icon = icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
