package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.model.*
import com.example.data.repository.SportsStreamRepository
import com.example.player.ExoPlayerManager
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOut
import com.example.ui.components.*
import com.example.ui.dialogs.*
import com.example.ui.screens.*
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

enum class AppScreen {
    SPLASH,
    MAIN_TABS,
    PLAYER,
    ADMIN_PANEL
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: SportsStreamRepository
    private lateinit var playerManager: ExoPlayerManager
    private val isInPipModeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure lightweight, high-speed image caching for team shields & tournament logos
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .crossfade(true)
                .allowHardware(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.18)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("team_logos_cache"))
                        .maxSizeBytes(24L * 1024L * 1024L)
                        .build()
                }
                .build()
        )

        repository = SportsStreamRepository(applicationContext)
        playerManager = ExoPlayerManager(applicationContext)

        setContent {
            val isInPipMode by isInPipModeFlow.collectAsState()
            MyApplicationTheme {
                FootballLiveTvApp(
                    repository = repository,
                    playerManager = playerManager,
                    isInPipMode = isInPipMode,
                    onEnterPiP = { triggerPictureInPicture() }
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeFlow.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPipModeFlow.value = isInPictureInPictureMode
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::playerManager.isInitialized && playerManager.isPlaying.value) {
            triggerPictureInPicture()
        }
    }

    private fun triggerPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                isInPipModeFlow.value = true
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                val entered = enterPictureInPictureMode(params)
                if (!entered) {
                    isInPipModeFlow.value = false
                }
            } catch (e: Exception) {
                isInPipModeFlow.value = false
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::playerManager.isInitialized) {
            playerManager.release()
        }
    }
}

@Composable
fun FootballLiveTvApp(
    repository: SportsStreamRepository,
    playerManager: ExoPlayerManager,
    isInPipMode: Boolean = false,
    onEnterPiP: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authFlow = remember(context) {
        if (com.example.ui.auth.isFirebaseReady(context)) {
            runCatching { Firebase.auth.authStateFlow() }.getOrElse { kotlinx.coroutines.flow.flowOf(null) }
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }
    val initialUser = remember(context) {
        if (com.example.ui.auth.isFirebaseReady(context)) {
            runCatching { Firebase.auth.currentUser }.getOrNull()
        } else {
            null
        }
    }
    val currentUser by authFlow.collectAsState(initial = initialUser)

    LaunchedEffect(Unit) {
        if (currentUser == null && com.example.ui.auth.isFirebaseReady(context)) {
            runCatching {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = coroutineScope
                )
            }
        }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid != null) {
            runCatching { repository.startCloudSyncForUser(uid) }
        } else {
            runCatching { repository.stopCloudSync() }
        }
    }

    val matches by repository.matches.collectAsState()
    val crexState by repository.crexDashboardState.collectAsState()
    val channels by repository.channels.collectAsState()
    val categories by repository.categories.collectAsState()
    val highlights by repository.highlights.collectAsState()
    val config by repository.config.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var selectedTab by remember { mutableStateOf(NavTab.LIVE_EVENTS) }

    // Active playback target
    var activeMatch by remember { mutableStateOf<LiveMatch?>(null) }
    var activeChannelTitle by remember { mutableStateOf<String?>(null) }
    var activeStreams by remember { mutableStateOf<List<StreamSource>>(emptyList()) }

    // Filter states
    var selectedSport by remember { mutableStateOf(SportType.ALL) }
    var selectedStatus by remember { mutableStateOf<MatchStatus?>(null) }
    var selectedChannelCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // Dialog states
    var showStreamPickerModal by remember { mutableStateOf(false) }
    var streamPickerMatch by remember { mutableStateOf<LiveMatch?>(null) }
    var streamPickerChannel by remember { mutableStateOf<SportsChannel?>(null) }
    var streamPickerHighlight by remember { mutableStateOf<HighlightItem?>(null) }
    var resolvedHighlightStreams by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var isResolvingMatchStreams by remember { mutableStateOf(false) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showM3UImportDialog by remember { mutableStateOf(false) }
    var showHighlightsDialog by remember { mutableStateOf(false) }
    var showScoresDialog by remember { mutableStateOf(false) }
    var scoresSportTitle by remember { mutableStateOf("Cricket") }
    var showAnnouncementBanner by remember { mutableStateOf(true) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }

    val isPlayerPlaying by playerManager.isPlaying.collectAsState()
    val currentStream by playerManager.currentStream.collectAsState()

    fun openMatchModal(match: LiveMatch) {
        streamPickerMatch = match
        streamPickerChannel = null
        streamPickerHighlight = null
        showStreamPickerModal = true
        val initialStreams = match.publicEnabledStreams()
        isResolvingMatchStreams = initialStreams.isEmpty()
        if (initialStreams.isNotEmpty()) {
            playerManager.preWarmStreams(initialStreams)
        }
        coroutineScope.launch {
            val resolved = repository.resolveMatchStreamsOnDemand(match.id)
            if (resolved != null) {
                playerManager.preWarmStreams(resolved.publicEnabledStreams())
                if (streamPickerMatch?.id == match.id) {
                    streamPickerMatch = resolved
                }
            }
            if (streamPickerMatch?.id == match.id) {
                isResolvingMatchStreams = false
            }
        }
        if (match.sport == SportType.CRICKET) {
            coroutineScope.launch {
                repository.loadMatchDetailOnDemand(match.id, force = false)
            }
        }
    }

    fun startPlaying(match: LiveMatch?, title: String?, streams: List<StreamSource>) {
        val nonPointerStreams = streams.filter { s ->
            val cleanUrl = s.url.substringBefore("|").trim()
            s.isEnabled &&
                cleanUrl.isNotBlank() &&
                !cleanUrl.endsWith(".txt", ignoreCase = true) &&
                !cleanUrl.contains("workers.dev/?q=", ignoreCase = true)
        }
        // Always stop & clear any previously playing stream before switching to a new match/channel
        playerManager.stopAndClear()
        activeMatch = match
        activeChannelTitle = title
        activeStreams = nonPointerStreams
        playerManager.setFallbackCandidates(activeStreams, config.forceLowQuality)
        if (match != null) {
            coroutineScope.launch {
                val resolved = repository.resolveMatchStreamsOnDemand(match.id)
                if (resolved != null && activeMatch?.id == match.id) {
                    activeMatch = resolved
                    val resolvedList = resolved.publicEnabledStreams()
                    activeStreams = resolvedList
                    playerManager.setFallbackCandidates(resolvedList, config.forceLowQuality)
                    if (nonPointerStreams.isEmpty() && resolvedList.isNotEmpty()) {
                        val def = resolvedList.find { it.isDefault } ?: resolvedList.first()
                        playerManager.playStream(def, config.forceLowQuality)
                    }
                }
            }
            if (match.sport == SportType.CRICKET) {
                coroutineScope.launch {
                    repository.loadMatchDetailOnDemand(match.id, force = false)
                }
            }
        }
        if (nonPointerStreams.isNotEmpty()) {
            val defaultStream = nonPointerStreams.find { it.isDefault } ?: nonPointerStreams.first()
            playerManager.playStream(defaultStream, config.forceLowQuality)
        }
        currentScreen = AppScreen.PLAYER
    }

    fun openHighlightModal(highlight: HighlightItem) {
        streamPickerHighlight = highlight
        streamPickerMatch = null
        streamPickerChannel = null
        resolvedHighlightStreams = emptyList()
        isResolvingMatchStreams = true
        showStreamPickerModal = true
        coroutineScope.launch {
            val resolved = repository.resolveHighlightStreamsOnDemand(highlight)
            playerManager.preWarmStreams(resolved)
            if (streamPickerHighlight?.id == highlight.id) {
                resolvedHighlightStreams = resolved
                isResolvingMatchStreams = false
                // If there is exactly 1 highlight stream, launch the player immediately for instant playback
                if (resolved.size == 1) {
                    showStreamPickerModal = false
                    streamPickerHighlight = null
                    startPlaying(null, highlight.title, resolved)
                }
            }
        }
    }

    // Auto-sync activeMatch, activeStreams, and streamPickerMatch when live playlists & match broadcasts refresh
    LaunchedEffect(matches) {
        activeMatch?.let { currentActive ->
            matches.find { it.id == currentActive.id }?.let { updated ->
                activeMatch = updated
                val updatedStreams = updated.publicEnabledStreams()
                if (updatedStreams.isNotEmpty()) {
                    activeStreams = updatedStreams
                    playerManager.setFallbackCandidates(updatedStreams, config.forceLowQuality)
                }
            }
        }
        streamPickerMatch?.let { currentModalMatch ->
            matches.find { it.id == currentModalMatch.id }?.let { updated ->
                streamPickerMatch = updated
            }
        }
    }

    // 1. Auto-refresh Football (ESPN) & Cricket (CREX Live/Upcoming/Finished) + Auto-Playlist Matcher
    // ONLY while on MAIN_TABS (pauses background network polling during active video playback so 100% of bandwidth goes to ExoPlayer).
    LaunchedEffect(
        lifecycleOwner,
        currentScreen,
        isInPipMode,
        config.autoRefreshIntervalSec,
        config.footballSyncEnabled,
        config.cricketSyncEnabled,
        config.enabledFootballLeagues
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (currentScreen != AppScreen.PLAYER && !isInPipMode) {
                    runCatching { repository.refreshLiveMatches(force = false) }
                }
                val intervalMs = config.autoRefreshIntervalSec.coerceIn(25, 120) * 1000L
                delay(intervalMs)
            }
        }
    }

    // 2. Refresh when status filter or scores dialog is explicitly toggled
    LaunchedEffect(selectedStatus, showScoresDialog) {
        if (selectedStatus == MatchStatus.FINISHED || showScoresDialog) {
            runCatching { repository.refreshFinishedMatches(force = false) }
        }
    }

    // Top Level Navigation
    if (isInPipMode && currentStream != null) {
        PlayerScreen(
            match = activeMatch,
            channelTitle = activeChannelTitle,
            streams = activeStreams,
            playerManager = playerManager,
            forceLowQuality = config.forceLowQuality,
            isInPipMode = true,
            onBackClick = {
                currentScreen = AppScreen.MAIN_TABS
            },
            otherMatches = emptyList(),
            onSwitchMatch = {},
            onFavoriteToggle = {},
            onEnterPiP = onEnterPiP
        )
        return
    }

    when (currentScreen) {
        AppScreen.SPLASH -> {
            SplashScreen(
                config = config,
                onSplashFinished = {
                    currentScreen = AppScreen.MAIN_TABS
                }
            )
        }

        AppScreen.PLAYER -> {
            PlayerScreen(
                match = activeMatch,
                channelTitle = activeChannelTitle,
                streams = activeStreams,
                playerManager = playerManager,
                forceLowQuality = config.forceLowQuality,
                isInPipMode = isInPipMode,
                onBackClick = {
                    currentScreen = AppScreen.MAIN_TABS
                },
                otherMatches = matches.filter { it.id != activeMatch?.id },
                onSwitchMatch = { newMatch ->
                    openMatchModal(newMatch)
                },
                onFavoriteToggle = { repository.toggleMatchFavorite(it) },
                onEnterPiP = onEnterPiP
            )
        }

        AppScreen.ADMIN_PANEL -> {
            AdminPanelScreen(
                repository = repository,
                onBackClick = {
                    currentScreen = AppScreen.MAIN_TABS
                },
                onTestPlayStream = { stream ->
                    startPlaying(null, stream.name, listOf(stream))
                }
            )
        }

        AppScreen.MAIN_TABS -> {
            BackHandler(enabled = isSearching || selectedTab != NavTab.LIVE_EVENTS) {
                if (isSearching) {
                    isSearching = false
                    searchQuery = ""
                } else {
                    selectedTab = NavTab.LIVE_EVENTS
                }
            }
            Scaffold(
                topBar = {
                    Column {
                        val topTitle = when (selectedTab) {
                            NavTab.HIGHLIGHTS -> "Highlights"
                            NavTab.CATEGORIES -> "Categories"
                            NavTab.SPORTS -> "Sports"
                            NavTab.FAVOURITES -> "Favourites"
                            NavTab.MENU -> "Settings"
                            else -> config.appTitle
                        }
                        TopSportsBar(
                            title = topTitle,
                            onTelegramClick = { showTelegramDialog = true },
                            onFavouritesClick = {
                                selectedTab = if (selectedTab == NavTab.FAVOURITES) {
                                    NavTab.LIVE_EVENTS
                                } else {
                                    NavTab.FAVOURITES
                                }
                            },
                            isFavouritesActive = selectedTab == NavTab.FAVOURITES,
                            onRefreshClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Refreshing live & upcoming matches and active streams...")
                                    val res = repository.syncFromRemoteFeed()
                                    res.fold(
                                        onSuccess = { count ->
                                            snackbarHostState.showSnackbar("Updated $count live & upcoming matches!")
                                        },
                                        onFailure = { err ->
                                            snackbarHostState.showSnackbar("Sync: ${err.localizedMessage ?: "Network error"}")
                                        }
                                    )
                                }
                            },
                            onSearchClick = { isSearching = !isSearching },
                            searchQuery = searchQuery,
                            isSearching = isSearching,
                            onSearchQueryChange = { searchQuery = it },
                            onCloseSearch = {
                                isSearching = false
                                searchQuery = ""
                            }
                        )

                        // Announcement Banner (kept on non-LIVE_EVENTS & non-HIGHLIGHTS tabs so headers match screenshots)
                        if (selectedTab != NavTab.LIVE_EVENTS && selectedTab != NavTab.HIGHLIGHTS && showAnnouncementBanner && config.announcement.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NavySurfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = GoldYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = config.announcement,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { showAnnouncementBanner = false },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                },
                bottomBar = {
                    Column {
                        // Mini Now-Playing Bar when a stream was opened
                        if (currentStream != null && activeStreams.isNotEmpty()) {
                            val nowPlayingTitle = activeMatch?.let { "${it.team1Code} vs ${it.team2Code}" }
                                ?: activeChannelTitle
                                ?: currentStream?.name
                                ?: "Live Stream"
                            Surface(
                                color = NavySurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, CyanAccent.copy(alpha = 0.4f))
                                    .clickable { currentScreen = AppScreen.PLAYER }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = nowPlayingTitle,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 12.sp
                                                ),
                                                maxLines = 1
                                            )
                                            Text(
                                                text = currentStream?.name ?: "Tap to return to player",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = CyanAccent,
                                                    fontSize = 10.sp
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (isPlayerPlaying) playerManager.pause() else playerManager.resume()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = CyanAccent
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                playerManager.pause()
                                                activeStreams = emptyList()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close mini player",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        BottomSportsNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                },
                containerColor = Color.Black
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(padding)
                ) {
                    // Ambient Glassmorphism Backdrop Glow Orbs (visible through frosted white glass cards)
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    CyanAccent.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.12f),
                                radius = size.minDimension * 0.65f
                            ),
                            radius = size.minDimension * 0.65f,
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.12f)
                        )
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    com.example.ui.theme.SkyBlue.copy(alpha = 0.11f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.45f),
                                radius = size.minDimension * 0.60f
                            ),
                            radius = size.minDimension * 0.60f,
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.45f)
                        )
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                     Color.White.copy(alpha = 0.06f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.82f),
                                radius = size.minDimension * 0.55f
                            ),
                            radius = size.minDimension * 0.55f,
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.82f)
                        )
                    }

                    when (selectedTab) {
                        NavTab.LIVE_EVENTS -> {
                            LiveEventsScreen(
                                matches = matches,
                                crexState = crexState,
                                selectedSport = selectedSport,
                                onSportSelect = { selectedSport = it },
                                selectedStatus = selectedStatus,
                                onStatusSelect = { selectedStatus = it },
                                onManualRefresh = {
                                    coroutineScope.launch {
                                        when (selectedStatus) {
                                            MatchStatus.LIVE -> {
                                                val s = repository.refreshLiveMatches(force = true)
                                                snackbarHostState.showSnackbar(
                                                    s.errorMessage ?: "Refreshed Live Football & Cricket matches"
                                                )
                                            }
                                            MatchStatus.UPCOMING -> {
                                                val s = repository.refreshUpcomingMatches(force = true)
                                                snackbarHostState.showSnackbar(
                                                    s.errorMessage ?: "Refreshed Upcoming Football & Cricket matches"
                                                )
                                            }
                                            MatchStatus.FINISHED -> {
                                                val s = repository.refreshFinishedMatches(force = true)
                                                snackbarHostState.showSnackbar(
                                                    s.errorMessage ?: "Refreshed Finished Football & Cricket matches"
                                                )
                                            }
                                            MatchStatus.UNKNOWN, null -> {
                                                val res = repository.syncFromRemoteFeed()
                                                res.fold(
                                                    onSuccess = { count ->
                                                        snackbarHostState.showSnackbar("Refreshed $count Football & Cricket matches")
                                                    },
                                                    onFailure = { err ->
                                                        snackbarHostState.showSnackbar("Sync Error: ${err.localizedMessage}")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                onMatchClick = { match ->
                                    openMatchModal(match)
                                },
                                onFavoriteToggle = { repository.toggleMatchFavorite(it) },
                                onStreamSelectClick = { match ->
                                    openMatchModal(match)
                                },
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it }
                            )
                        }

                        NavTab.SPORTS -> {
                            SportsChannelsScreen(
                                channels = channels.filter { it.isEnabled },
                                selectedCategory = selectedChannelCategory,
                                onCategorySelect = { selectedChannelCategory = it },
                                onChannelClick = { channel ->
                                    val pubStreams = channel.publicEnabledStreams()
                                    if (pubStreams.size > 1) {
                                        streamPickerChannel = channel
                                        streamPickerMatch = null
                                        showStreamPickerModal = true
                                    } else {
                                        startPlaying(null, channel.name, pubStreams)
                                    }
                                },
                                onFavoriteToggle = { repository.toggleChannelFavorite(it) },
                                searchQuery = searchQuery
                            )
                        }

                        NavTab.CATEGORIES -> {
                            CategoriesScreen(
                                categories = categories,
                                searchQuery = searchQuery,
                                onCategoryClick = { cat ->
                                    val nameUpper = cat.name.uppercase()
                                    when {
                                        nameUpper.contains("LIVE EVENT") -> {
                                            selectedStatus = MatchStatus.LIVE
                                            selectedTab = NavTab.LIVE_EVENTS
                                        }
                                        nameUpper.contains("HIGHLIGHT") -> {
                                            selectedTab = NavTab.HIGHLIGHTS
                                        }
                                        else -> {
                                            val matchedChannelCat = channels
                                                .map { it.category }
                                                .firstOrNull { it.equals(cat.name, ignoreCase = true) }
                                                ?: "All"
                                            selectedChannelCategory = matchedChannelCat
                                            selectedTab = NavTab.SPORTS
                                        }
                                    }
                                }
                            )
                        }

                        NavTab.HIGHLIGHTS -> {
                            HighlightsScreen(
                                highlights = highlights,
                                searchQuery = searchQuery,
                                onHighlightClick = { highlight ->
                                    openHighlightModal(highlight)
                                }
                            )
                        }

                        NavTab.FAVOURITES -> {
                            FavouritesScreen(
                                favoriteMatches = matches.filter { it.isFavorite },
                                favoriteChannels = channels.filter { it.isFavorite },
                                searchQuery = searchQuery,
                                onMatchClick = { match ->
                                    openMatchModal(match)
                                },
                                onChannelClick = { channel ->
                                    val pubStreams = channel.publicEnabledStreams()
                                    if (pubStreams.size > 1) {
                                        streamPickerChannel = channel
                                        streamPickerMatch = null
                                        streamPickerHighlight = null
                                        showStreamPickerModal = true
                                    } else {
                                        startPlaying(null, channel.name, pubStreams)
                                    }
                                },
                                onMatchFavoriteToggle = { repository.toggleMatchFavorite(it) },
                                onChannelFavoriteToggle = { repository.toggleChannelFavorite(it) },
                                onStreamSelectClick = { match ->
                                    openMatchModal(match)
                                }
                            )
                        }

                        NavTab.MENU -> {
                            MenuScreen(
                                config = config,
                                userEmail = currentUser?.email,
                                onNetworkStreamClick = { showNetworkStreamDialog = true },
                                onPlaylistsClick = { showM3UImportDialog = true },
                                onHighlightsClick = { selectedTab = NavTab.HIGHLIGHTS },
                                onCricketScoreClick = {
                                    scoresSportTitle = "Cricket"
                                    showScoresDialog = true
                                },
                                onFootballScoreClick = {
                                    scoresSportTitle = "Football"
                                    showScoresDialog = true
                                },
                                onFloatingPlayerClick = {
                                    if (currentStream == null && matches.isNotEmpty()) {
                                        val firstLive = matches.firstOrNull {
                                            it.status == MatchStatus.LIVE && it.publicEnabledStreams().isNotEmpty()
                                        } ?: matches.firstOrNull { it.publicEnabledStreams().isNotEmpty() }
                                        if (firstLive != null) {
                                            startPlaying(firstLive, null, firstLive.publicEnabledStreams())
                                        }
                                    }
                                    onEnterPiP()
                                },
                                onForceLowQualityToggle = { lowQuality ->
                                    repository.updateConfig(config.copy(forceLowQuality = lowQuality))
                                    currentStream?.let { active ->
                                        playerManager.playStream(active, lowQuality)
                                    }
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (lowQuality) "Data Saver enabled (SD 480p max)" else "HD/FHD quality unlocked"
                                        )
                                    }
                                },
                                onAdminPanelClick = { showAdminPinDialog = true },
                                onGoogleSignInClick = {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Connected to Real-Time Cloud Sync!")
                                            }
                                        },
                                        onAuthError = { msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Cloud Sign-In: $msg")
                                            }
                                        },
                                        scope = coroutineScope
                                    )
                                },
                                onSignOutClick = {
                                    repository.stopCloudSync()
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = coroutineScope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdminPinDialog) {
        AdminPinDialog(
            correctPin = config.adminPin,
            onDismiss = { showAdminPinDialog = false },
            onSuccess = {
                showAdminPinDialog = false
                currentScreen = AppScreen.ADMIN_PANEL
            }
        )
    }

    // Modal: Stream selection & on-demand CREX getSV3 match details
    if (showStreamPickerModal && (streamPickerMatch != null || streamPickerChannel != null || streamPickerHighlight != null)) {
        val currentMatch = streamPickerMatch
        val currentChannel = streamPickerChannel
        val currentHighlight = streamPickerHighlight
        if (currentMatch != null) {
            val latestMatch = matches.find { it.id == currentMatch.id } ?: currentMatch
            val pubStreams = latestMatch.publicEnabledStreams()
            val detailState = crexState.activeDetailState.takeIf {
                it.matchId == latestMatch.id.removePrefix("crex-")
            }
            val matchedHighlight = remember(latestMatch.id, latestMatch.team1Name, latestMatch.team2Name, highlights) {
                highlights.firstOrNull { hl ->
                    (hl.team1Name.equals(latestMatch.team1Name, ignoreCase = true) &&
                        hl.team2Name.equals(latestMatch.team2Name, ignoreCase = true)) ||
                        (hl.team1Name.equals(latestMatch.team2Name, ignoreCase = true) &&
                            hl.team2Name.equals(latestMatch.team1Name, ignoreCase = true))
                }
            }
            MultipleStreamsModalDialog(
                streams = pubStreams,
                currentSelectedStream = pubStreams.firstOrNull(),
                match = latestMatch,
                matchTitle = "${latestMatch.team1Name} vs ${latestMatch.team2Name}",
                matchTournament = latestMatch.tournament,
                matchStatus = latestMatch.status.name,
                isLoadingStreams = isResolvingMatchStreams,
                matchDetailState = detailState,
                onRefreshDetail = {
                    coroutineScope.launch {
                        repository.loadMatchDetailOnDemand(latestMatch.id, force = true)
                    }
                },
                approvedHighlightUrl = latestMatch.approvedHighlightUrl,
                matchedHighlightItem = matchedHighlight,
                onWatchHighlight = { highlightUrl ->
                    showStreamPickerModal = false
                    streamPickerMatch = null
                    val hlTitle = "${latestMatch.team1Code} vs ${latestMatch.team2Code} Highlights"
                    coroutineScope.launch {
                        val tempHl = HighlightItem(
                            id = "highlight-${latestMatch.id}",
                            title = hlTitle,
                            tournament = latestMatch.tournament,
                            duration = "Highlights",
                            thumbnailUrl = "",
                            videoUrl = highlightUrl,
                            sport = latestMatch.sport
                        )
                        val resolved = repository.resolveHighlightStreamsOnDemand(tempHl)
                        startPlaying(null, hlTitle, resolved)
                    }
                },
                onWatchHighlightItem = { hl ->
                    openHighlightModal(hl)
                },
                onToggleFavorite = {
                    repository.toggleMatchFavorite(latestMatch.id)
                },
                broadcastChannels = latestMatch.broadcastChannels,
                onStreamSelected = { selected ->
                    startPlaying(latestMatch, null, listOf(selected) + pubStreams.filter { it.id != selected.id })
                },
                onDismiss = {
                    showStreamPickerModal = false
                    streamPickerMatch = null
                }
            )
        } else if (currentChannel != null) {
            val pubStreams = currentChannel.publicEnabledStreams()
            MultipleStreamsModalDialog(
                streams = pubStreams,
                currentSelectedStream = pubStreams.firstOrNull(),
                matchTitle = currentChannel.name,
                matchTournament = currentChannel.category.uppercase(),
                matchStatus = "LIVE",
                onStreamSelected = { selected ->
                    startPlaying(null, currentChannel.name, listOf(selected) + pubStreams.filter { it.id != selected.id })
                },
                onDismiss = {
                    showStreamPickerModal = false
                    streamPickerChannel = null
                }
            )
        } else if (currentHighlight != null) {
            MultipleStreamsModalDialog(
                streams = resolvedHighlightStreams,
                currentSelectedStream = resolvedHighlightStreams.firstOrNull(),
                matchTitle = currentHighlight.title,
                matchTournament = currentHighlight.tournament,
                matchStatus = "HIGHLIGHTS",
                isLoadingStreams = isResolvingMatchStreams,
                onStreamSelected = { selected ->
                    showStreamPickerModal = false
                    streamPickerHighlight = null
                    startPlaying(
                        null,
                        currentHighlight.title,
                        listOf(selected) + resolvedHighlightStreams.filter { it.id != selected.id }
                    )
                },
                onDismiss = {
                    showStreamPickerModal = false
                    streamPickerHighlight = null
                }
            )
        }
    }

    // Modal: Network stream URL player
    if (showNetworkStreamDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkStreamDialog = false },
            onPlayUrl = { directStream ->
                startPlaying(null, directStream.name, listOf(directStream))
            }
        )
    }

    // Modal: M3U Playlist Importer
    if (showM3UImportDialog) {
        M3UPlaylistImportDialog(
            onDismiss = { showM3UImportDialog = false },
            onImport = { playlistData ->
                coroutineScope.launch {
                    val count = repository.importM3UPlaylist(playlistData)
                    snackbarHostState.showSnackbar("Successfully imported $count channels from M3U playlist!")
                }
            }
        )
    }

    // Modal: Highlights & Replays
    if (showHighlightsDialog) {
        HighlightsDialog(
            highlights = highlights,
            onPlayHighlight = { highlight ->
                showHighlightsDialog = false
                coroutineScope.launch {
                    val resolvedStreams = repository.resolveHighlightStreamsOnDemand(highlight)
                    startPlaying(null, highlight.title, resolvedStreams)
                }
            },
            onDismiss = { showHighlightsDialog = false }
        )
    }


    // Modal: Scores dialog
    if (showScoresDialog) {
        LiveScoresModalDialog(
            sportTitle = scoresSportTitle,
            matches = matches,
            onWatchMatch = { match ->
                openMatchModal(match)
            },
            onDismiss = { showScoresDialog = false }
        )
    }

    // Modal: Telegram community dialog
    if (showTelegramDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent) },
            title = { Text("Join Telegram Community") },
            text = {
                Text("Join our sports live stream updates channel on Telegram to get instant match notifications, direct links, and server status.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.telegramLink))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                        showTelegramDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Open Telegram", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTelegramDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = NavyDark
        )
    }
}
