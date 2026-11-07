package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.model.StreamSource
import com.example.data.util.TeamFlagResolver
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.request.ImageRequest
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSportsBar(
    title: String = "PLAYLIVE",
    onTelegramClick: () -> Unit,
    onFavouritesClick: () -> Unit = {},
    isFavouritesActive: Boolean = false,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    searchQuery: String = "",
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {}
) {
    Surface(
        color = Color.Black.copy(alpha = 0.82f),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
        ) {
            if (isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input"),
                        placeholder = {
                            Text(
                                "Search matches, leagues, channels...",
                                color = TextSecondary,
                                fontSize = 12.5.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.09f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = onCloseSearch) {
                                Icon(Icons.Default.Close, contentDescription = "Close search", tint = TextSecondary)
                            }
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        // Glowing Neon Accent Pill Bar
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(CyanAccent, SkyBlue)
                                    )
                                )
                        )
                        Text(
                            text = title.ifBlank { "PLAYLIVE" },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp,
                                letterSpacing = 0.6.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CyanAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.5.sp,
                                    letterSpacing = 0.8.sp
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favourites Star Frosted Glass Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isFavouritesActive) GoldYellow.copy(alpha = 0.20f)
                                    else Color.White.copy(alpha = 0.09f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFavouritesActive) GoldYellow else Color.White.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(11.dp)
                                )
                                .clickable(onClick = onFavouritesClick)
                                .testTag("favourites_header_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Favourites",
                                tint = if (isFavouritesActive) GoldYellow else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Refresh Frosted Glass Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(11.dp))
                                .clickable(onClick = onRefreshClick)
                                .testTag("refresh_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Search Frosted Glass Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(11.dp))
                                .clickable(onClick = onSearchClick)
                                .testTag("search_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.25f),
                                CyanAccent.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

enum class NavTab(val label: String, val icon: ImageVector, val showInBottomBar: Boolean = true) {
    LIVE_EVENTS("Live Events", Icons.Default.Podcasts, true),
    CATEGORIES("Categories", Icons.Default.Tv, true),
    SPORTS("Sports", Icons.Default.SportsSoccer, true),
    HIGHLIGHTS("Highlights", Icons.Default.VideoLibrary, true),
    MENU("Settings", Icons.Default.Settings, true),
    FAVOURITES("Favourites", Icons.Default.Star, false)
}

@Composable
fun BottomSportsNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    val visibleTabs = remember { NavTab.values().filter { it.showInBottomBar } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                visibleTabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val activeNeon = CyanAccent
                    val inactiveGrey = Color.White.copy(alpha = 0.62f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 5.dp, horizontal = 3.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.18f),
                                            CyanAccent.copy(alpha = 0.12f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Transparent)
                                    )
                                }
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.34f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                } else Modifier
                            )
                            .clickable { onTabSelected(tab) }
                            .testTag("nav_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) activeNeon else inactiveGrey,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else inactiveGrey,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumCricketEmblem(
    size: Dp = 36.dp,
    isSelected: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFF10B981).copy(alpha = 0.42f), Color(0xFF047857).copy(alpha = 0.25f))
                    } else {
                        listOf(Color(0xFF0EA5E9).copy(alpha = 0.28f), Color(0xFF1E293B).copy(alpha = 0.6f))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF38BDF8))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.16f)) {
            val w = this.size.width
            val h = this.size.height

            val stumpColor = Color(0xFFFDE68A).copy(alpha = 0.45f)
            val stumpStroke = w * 0.055f
            drawLine(
                color = stumpColor,
                start = Offset(w * 0.24f, h * 0.22f),
                end = Offset(w * 0.24f, h * 0.78f),
                strokeWidth = stumpStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = stumpColor,
                start = Offset(w * 0.36f, h * 0.20f),
                end = Offset(w * 0.36f, h * 0.78f),
                strokeWidth = stumpStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = stumpColor,
                start = Offset(w * 0.20f, h * 0.20f),
                end = Offset(w * 0.40f, h * 0.20f),
                strokeWidth = stumpStroke,
                cap = StrokeCap.Round
            )

            rotate(degrees = -35f, pivot = Offset(w * 0.5f, h * 0.5f)) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF8FAFC), Color(0xFF93C5FD))
                    ),
                    topLeft = Offset(w * 0.455f, h * 0.06f),
                    size = Size(w * 0.09f, h * 0.28f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706))
                    ),
                    topLeft = Offset(w * 0.39f, h * 0.31f),
                    size = Size(w * 0.22f, h * 0.58f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                )
            }

            val ballCenter = Offset(w * 0.67f, h * 0.66f)
            val ballRadius = w * 0.23f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6B6B), Color(0xFFEF4444), Color(0xFF991B1B)),
                    center = Offset(ballCenter.x - ballRadius * 0.25f, ballCenter.y - ballRadius * 0.25f),
                    radius = ballRadius * 1.3f
                ),
                radius = ballRadius,
                center = ballCenter
            )
        }
    }
}

@Composable
fun PremiumFootballEmblem(
    size: Dp = 36.dp,
    isSelected: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.42f), Color(0xFF1D4ED8).copy(alpha = 0.28f))
                    } else {
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.25f), Color(0xFF1E293B).copy(alpha = 0.6f))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.16f)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)
            val radius = w * 0.44f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFE0F2FE), Color(0xFF93C5FD)),
                    center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                    radius = radius * 1.35f
                ),
                radius = radius,
                center = center
            )

            drawCircle(
                color = Color(0xFF0284C7),
                radius = radius,
                center = center,
                style = Stroke(width = w * 0.055f)
            )
        }
    }
}

@Composable
fun SportHeaderSquareBadge(sport: SportType, tournament: String) {
    val isMotoGp = sport == SportType.RACING || tournament.contains("MOTOGP", ignoreCase = true)
    if (isMotoGp) {
        Text(
            text = "MGP",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = (-0.5).sp
            )
        )
    } else {
        Box(
            modifier = Modifier
                .size(19.dp)
                .background(Color.White, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(13.dp)) {
                val w = size.width
                val h = size.height
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = w * 0.14f,
                    center = Offset(w * 0.55f, h * 0.22f)
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.30f, h * 0.42f),
                    end = Offset(w * 0.78f, h * 0.28f),
                    strokeWidth = w * 0.13f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.52f, h * 0.35f),
                    end = Offset(w * 0.42f, h * 0.70f),
                    strokeWidth = w * 0.14f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.42f, h * 0.65f),
                    end = Offset(w * 0.24f, h * 0.90f),
                    strokeWidth = w * 0.12f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.48f, h * 0.62f),
                    end = Offset(w * 0.72f, h * 0.88f),
                    strokeWidth = w * 0.12f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun MatchCard(
    match: LiveMatch,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit = {},
    onStreamSelectClick: () -> Unit = {}
) {
    val team1Visual = remember(match.team1Name, match.team1Code, match.team1FlagUrl, match.isNationalTeam) {
        TeamFlagResolver.resolveForMatch(
            teamName = match.team1Name,
            teamCode = match.team1Code,
            existingFlag = match.team1FlagUrl,
            isNationalTeam = match.isNationalTeam
        )
    }
    val team2Visual = remember(match.team2Name, match.team2Code, match.team2FlagUrl, match.isNationalTeam) {
        TeamFlagResolver.resolveForMatch(
            teamName = match.team2Name,
            teamCode = match.team2Code,
            existingFlag = match.team2FlagUrl,
            isNationalTeam = match.isNationalTeam
        )
    }

    val formattedHeader = remember(match.sport, match.tournament) {
        val raw = match.tournament.trim().uppercase()
        if (raw.contains("|")) {
            raw
        } else {
            val sportPrefix = when (match.sport) {
                SportType.CRICKET -> "CRICKET"
                SportType.FOOTBALL -> "FOOTBALL"
                SportType.BOXING -> "BOXING"
                SportType.RACING -> "MOTORSPORT"
                else -> match.sport.displayName.uppercase()
            }
            "$sportPrefix | ${raw.replace("•", "|")}"
        }
    }

    val initialSeconds = remember(match.id, match.startTimeEpochMs) {
        val now = System.currentTimeMillis()
        if (match.startTimeEpochMs in 1 until now) {
            ((now - match.startTimeEpochMs) / 1000L).coerceIn(60L, 4 * 3600L)
        } else {
            val hash = kotlin.math.abs(match.id.hashCode())
            val h = (hash % 2)
            val m = 15 + (hash % 40)
            val s = 10 + (hash % 49)
            h * 3600L + m * 60L + s
        }
    }

    var liveSeconds by remember(initialSeconds) { mutableStateOf(initialSeconds) }
    LaunchedEffect(match.status, initialSeconds) {
        if (match.status == MatchStatus.LIVE) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                liveSeconds += 1L
            }
        }
    }

    val liveTimerFormatted = remember(liveSeconds) {
        val hrs = liveSeconds / 3600L
        val mins = (liveSeconds % 3600L) / 60L
        val secs = liveSeconds % 60L
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    }

    val (upcomingTimeText, upcomingDateText) = remember(match.matchTime, match.startTimeEpochMs) {
        if (match.startTimeEpochMs > 0L) {
            val dhakaTz = TimeZone.getTimeZone("Asia/Dhaka")
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = dhakaTz }
            val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = dhakaTz }
            val dt = Date(match.startTimeEpochMs)
            Pair(timeFmt.format(dt), dateFmt.format(dt))
        } else {
            val parts = match.matchTime.split("|", "•", ",")
            val timePart = Regex("""\b\d{1,2}:\d{2}\s*(?:AM|PM)\b""", RegexOption.IGNORE_CASE)
                .find(match.matchTime)?.value?.uppercase()
                ?: parts.firstOrNull()?.trim()?.takeIf { it.contains(":") }
                ?: "Scheduled"
            val datePart = Regex("""\b\d{2}/\d{2}/\d{4}\b""")
                .find(match.matchTime)?.value
                ?: Regex("""\b\d{1,2}\s+[A-Za-z]{3}\s+\d{4}\b""").find(match.matchTime)?.value
                ?: match.matchTime.take(16)
            Pair(timePart, datePart)
        }
    }

    val bottomStatusText = remember(match.status, liveTimerFormatted, match.startTimeEpochMs, match.timeOrScore) {
        when (match.status) {
            MatchStatus.LIVE -> liveTimerFormatted
            MatchStatus.UPCOMING -> {
                val now = System.currentTimeMillis()
                if (match.startTimeEpochMs > now) {
                    val diffMin = ((match.startTimeEpochMs - now) / 60_000L).coerceAtLeast(1L)
                    val hrs = diffMin / 60L
                    val mins = diffMin % 60L
                    when {
                        hrs >= 24L -> "Starts in ${hrs / 24L}d ${hrs % 24L}h"
                        hrs > 0L -> "Starts in ${hrs}h ${mins}m"
                        else -> "Starts in ${mins}m"
                    }
                } else if (match.timeOrScore.startsWith("Starts", ignoreCase = true)) {
                    match.timeOrScore
                } else {
                    "Upcoming"
                }
            }
            MatchStatus.FINISHED -> "Finished"
            MatchStatus.UNKNOWN -> "অনির্ধারিত"
        }
    }

    val team1Display = remember(match.team1Name, match.team1Code, team1Visual.countryName) {
        match.team1Name.ifBlank { team1Visual.countryName }.ifBlank { match.team1Code }
    }
    val team2Display = remember(match.team2Name, match.team2Code, team2Visual.countryName) {
        match.team2Name.ifBlank { team2Visual.countryName }.ifBlank { match.team2Code }
    }

    val isLive = match.status == MatchStatus.LIVE
    // Specular Frosted White Glass Border (brighter top-left edge, subtle bottom-right edge)
    val cardBorderBrush = remember(isLive) {
        if (isLive) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.42f),
                    LiveRed.copy(alpha = 0.65f),
                    CyanAccent.copy(alpha = 0.45f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.34f),
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.08f)
                )
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                brush = cardBorderBrush,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("match_card_${match.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            // 1. Compact Frosted Header Row: Sport/Tournament Pill + Favourite Star
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isLive) LiveRed else CyanAccent)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = formattedHeader,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 9.5.sp,
                            letterSpacing = 0.3.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (match.isFavorite) GoldYellow.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            0.8.dp,
                            if (match.isFavorite) GoldYellow.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.20f),
                            RoundedCornerShape(7.dp)
                        )
                        .testTag("fav_toggle_${match.id}")
                ) {
                    Icon(
                        imageVector = if (match.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (match.isFavorite) "Remove from Favourites" else "Add to Favourites",
                        tint = if (match.isFavorite) GoldYellow else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(12.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 2. Compact Arena Row: Team 1 | Center VS/LIVE | Team 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Team 1 Column (Compact Shield + Name)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamFlagBadge(
                        flagUrl = team1Visual.flagUrl,
                        emoji = team1Visual.emoji,
                        code = match.team1Code.ifBlank { team1Display },
                        teamName = match.team1Name,
                        isNationalTeam = match.isNationalTeam
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = team1Display,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                // Center Frosted Glass Hub
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    when (match.status) {
                        MatchStatus.LIVE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(LiveRed.copy(alpha = 0.22f))
                                    .border(0.9.dp, LiveRed, RoundedCornerShape(99.dp))
                                    .padding(horizontal = 8.dp, vertical = 1.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(LiveRed)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = liveTimerFormatted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanAccent,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                        MatchStatus.UPCOMING -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(0.8.dp, GoldYellow.copy(alpha = 0.60f), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 8.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "VS",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldYellow,
                                        fontSize = 9.5.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = upcomingTimeText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanAccent,
                                    fontSize = 10.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = upcomingDateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 8.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        MatchStatus.FINISHED -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 7.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "FINISHED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 8.5.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = upcomingDateText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 8.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        MatchStatus.UNKNOWN -> {
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldYellow,
                                    fontSize = 11.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Team 2 Column (Compact Shield + Name)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamFlagBadge(
                        flagUrl = team2Visual.flagUrl,
                        emoji = team2Visual.emoji,
                        code = match.team2Code.ifBlank { team2Display },
                        teamName = match.team2Name,
                        isNationalTeam = match.isNationalTeam
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = team2Display,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Compact Frosted Glass Bottom Action Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.Sensors else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (isLive) LiveRed else SkyBlue,
                        modifier = Modifier.size(11.5.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLive) "Live Broadcast Active" else bottomStatusText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (isLive) {
                                Brush.horizontalGradient(colors = listOf(CyanAccent, SkyBlue))
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                )
                            }
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (isLive) Color.Transparent else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isLive) Color.Black else CyanAccent,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (isLive) "WATCH LIVE" else "SERVERS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLive) Color.Black else CyanAccent,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.3.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TeamFlagBadge(
    flag: String = "",
    code: String = "",
    flagUrl: String = flag,
    emoji: String = "",
    teamName: String = "",
    isNationalTeam: Boolean = true
) {
    val context = LocalContext.current
    val effectiveIsNational = remember(teamName, code, isNationalTeam) {
        isNationalTeam || TeamFlagResolver.isRecognizedNationalTeam(teamName, code)
    }
    val resolved = remember(flag, code, flagUrl, emoji, teamName, effectiveIsNational) {
        val lookupVisual = TeamFlagResolver.resolveForMatch(
            teamName = teamName.ifBlank { code },
            teamCode = code,
            existingFlag = flagUrl.ifBlank { flag },
            isNationalTeam = effectiveIsNational
        )
        val primary = TeamFlagResolver.sanitizeFlagOrLogoUrl(flagUrl.ifBlank { flag }).let { raw ->
            if (raw.isNotBlank() && !raw.endsWith(".svg", ignoreCase = true)) raw else lookupVisual.flagUrl
        }
        val secondary = if (lookupVisual.flagUrl.isNotBlank() && lookupVisual.flagUrl != primary) {
            lookupVisual.flagUrl
        } else {
            lookupVisual.fallbackFlagUrl
        }
        TeamFlagResolver.TeamVisual(
            code = code.ifBlank { lookupVisual.code },
            flagUrl = primary,
            emoji = emoji.ifBlank { lookupVisual.emoji },
            countryName = lookupVisual.countryName,
            fallbackFlagUrl = secondary
        )
    }

    val isMotoGp = code.equals("MotoGP", ignoreCase = true) || teamName.contains("MotoGP", ignoreCase = true)

    if (isMotoGp && resolved.flagUrl.isBlank()) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MGP",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = CyanAccent,
                    fontSize = 10.sp,
                    letterSpacing = (-0.5).sp
                )
            )
        }
    } else {
        var useFallbackUrl by remember(resolved.flagUrl, resolved.fallbackFlagUrl) { mutableStateOf(false) }
        var imageLoadFailed by remember(resolved.flagUrl, resolved.fallbackFlagUrl) { mutableStateOf(false) }

        val activeRemoteUrl = if (useFallbackUrl && resolved.fallbackFlagUrl.isNotBlank()) {
            resolved.fallbackFlagUrl
        } else {
            resolved.flagUrl
        }
        val hasRemoteUrl = (activeRemoteUrl.startsWith("http://", ignoreCase = true) ||
            activeRemoteUrl.startsWith("https://", ignoreCase = true)) && !imageLoadFailed

        val imageRequest = remember(context, activeRemoteUrl) {
            if (activeRemoteUrl.isNotBlank()) {
                ImageRequest.Builder(context)
                    .data(activeRemoteUrl)
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    .crossfade(true)
                    .build()
            } else {
                null
            }
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    ),
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!hasRemoteUrl) {
                if (effectiveIsNational && resolved.emoji.isNotBlank() && resolved.emoji != "🏳️") {
                    Text(text = resolved.emoji, fontSize = 16.sp)
                } else {
                    Text(
                        text = resolved.code.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = CyanAccent,
                            fontSize = 9.5.sp
                        )
                    )
                }
            }

            if (hasRemoteUrl && imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = teamName.ifBlank { resolved.code },
                    contentScale = if (effectiveIsNational) ContentScale.Crop else ContentScale.Fit,
                    onError = {
                        if (!useFallbackUrl && resolved.fallbackFlagUrl.isNotBlank() && resolved.fallbackFlagUrl != resolved.flagUrl) {
                            useFallbackUrl = true
                        } else {
                            imageLoadFailed = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (effectiveIsNational) 0.dp else 3.5.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
fun PulsingLiveBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LiveRed.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = LiveRed,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun UpcomingBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SkyBlue.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "UPCOMING",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = SkyBlue,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun FinishedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(NavySurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "FINISHED",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontSize = 10.sp
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultipleStreamsModalDialog(
    streams: List<StreamSource>,
    currentSelectedStream: StreamSource? = null,
    match: LiveMatch? = null,
    matchTitle: String? = null,
    matchTournament: String? = null,
    matchStatus: String? = null,
    isLoadingStreams: Boolean = false,
    matchDetailState: com.example.data.crex.CrexMatchDetailState? = null,
    approvedHighlightUrl: String? = null,
    matchedHighlightItem: com.example.data.model.HighlightItem? = null,
    broadcastChannels: List<String> = emptyList(),
    onWatchHighlight: ((String) -> Unit)? = null,
    onWatchHighlightItem: ((com.example.data.model.HighlightItem) -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onRefreshDetail: (() -> Unit)? = null,
    onStreamSelected: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    val enabledStreams = remember(streams) {
        streams
            .filter { s ->
                val cleanUrl = s.url.substringBefore("|").trim()
                s.isEnabled &&
                    cleanUrl.isNotBlank() &&
                    (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) &&
                    !cleanUrl.endsWith(".txt", ignoreCase = true) &&
                    !cleanUrl.contains("workers.dev/?q=", ignoreCase = true)
            }
            .sortedWith(
                compareBy<StreamSource> { it.isBackup }
                    .thenBy { it.priorityOrder }
            )
    }

    val isUpcomingMatch = match?.status == MatchStatus.UPCOMING ||
        match?.status == MatchStatus.UNKNOWN ||
        matchStatus.equals("UPCOMING", ignoreCase = true)
    val isFinishedMatch = match?.status == MatchStatus.FINISHED ||
        matchStatus.equals("FINISHED", ignoreCase = true)

    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isUpcomingMatch, match?.startTimeEpochMs) {
        if (isUpcomingMatch && (match?.startTimeEpochMs ?: 0L) > 0L) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                nowEpochMs = System.currentTimeMillis()
            }
        }
    }

    val countdownText = remember(nowEpochMs, match?.startTimeEpochMs) {
        val startMs = match?.startTimeEpochMs ?: 0L
        val remainingSec = ((startMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
        if (startMs <= 0L || remainingSec <= 0L) {
            "Starting Soon"
        } else {
            val days = remainingSec / 86400L
            val hrs = (remainingSec % 86400L) / 3600L
            val mins = (remainingSec % 3600L) / 60L
            val secs = remainingSec % 60L
            if (days > 0L) {
                String.format(Locale.US, "%dd : %02dh : %02dm : %02ds", days, hrs, mins, secs)
            } else {
                String.format(Locale.US, "%02dh : %02dm : %02ds", hrs, mins, secs)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEB070A12),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(
                                1.dp,
                                when {
                                    isUpcomingMatch -> GoldYellow.copy(alpha = 0.65f)
                                    isFinishedMatch -> SkyBlue.copy(alpha = 0.65f)
                                    else -> Color.White.copy(alpha = 0.32f)
                                },
                                RoundedCornerShape(13.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isUpcomingMatch -> Icons.Default.Schedule
                                isFinishedMatch -> Icons.Default.PlayCircle
                                else -> Icons.Default.Sensors
                            },
                            contentDescription = null,
                            tint = when {
                                isUpcomingMatch -> GoldYellow
                                isFinishedMatch -> SkyBlue
                                else -> CyanAccent
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                isUpcomingMatch -> "UPCOMING MATCH SCHEDULE"
                                isFinishedMatch -> "MATCH COMPLETED"
                                else -> "STREAM LAUNCHPAD"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 15.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = matchTitle?.takeIf { it.isNotBlank() } ?: "Select a high-speed FHD server",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isUpcomingMatch) GoldYellow else CyanAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Team vs Team Header Banner inside Modal when match is present
            if (match != null && (isUpcomingMatch || isFinishedMatch)) {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isUpcomingMatch) GoldYellow.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.22f),
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                TeamFlagBadge(
                                    flag = match.team1FlagUrl,
                                    code = match.team1Code,
                                    teamName = match.team1Name,
                                    isNationalTeam = match.isNationalTeam
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = match.team1Name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                if (isUpcomingMatch) {
                                    UpcomingBadge()
                                } else {
                                    FinishedBadge()
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = "VS",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyanAccent,
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                TeamFlagBadge(
                                    flag = match.team2FlagUrl,
                                    code = match.team2Code,
                                    teamName = match.team2Name,
                                    isNationalTeam = match.isNationalTeam
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = match.team2Name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isUpcomingMatch) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, GoldYellow.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "MATCH STARTS IN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp,
                                            letterSpacing = 1.1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = countdownText,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = GoldYellow,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    if (match.matchTime.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Kickoff: ${match.matchTime} (BD Time)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = CyanAccent,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "এই ম্যাচটি এখনো শুরু হয়নি। খেলা শুরুর ১৫-৩০ মিনিট আগে লাইভ স্ট্রিম সার্ভার অটোমেটিক চালু হবে।",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            if (onToggleFavorite != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onToggleFavorite,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(
                                            if (match.isFavorite) GoldYellow else CyanAccent
                                        )
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (match.isFavorite) GoldYellow.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (match.isFavorite) GoldYellow else CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (match.isFavorite) "Saved in Favourites ⭐" else "Add to Favourites / Remind Me",
                                        color = if (match.isFavorite) GoldYellow else CyanAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else if (isFinishedMatch) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "এই ম্যাচটি সমাপ্ত হয়েছে। লাইভ স্ট্রিমিং বন্ধ রয়েছে।",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (isFinishedMatch) {
                if (matchedHighlightItem != null && onWatchHighlightItem != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onWatchHighlightItem(matchedHighlightItem)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("watch_matched_highlight_button"),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldYellow,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Watch Highlight",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Watch Match Highlights (FHD)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                } else if (!approvedHighlightUrl.isNullOrBlank() && onWatchHighlight != null) {
                    Button(
                        onClick = {
                            onWatchHighlight(approvedHighlightUrl)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("watch_highlight_button"),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldYellow,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Watch Highlight",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Watch Official Highlight",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            } else if (isUpcomingMatch) {
                if (enabledStreams.isNotEmpty()) {
                    Text(
                        text = "PRE-MATCH SERVERS AVAILABLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enabledStreams.forEach { stream ->
                            val isSelected = currentSelectedStream?.id == stream.id
                            StreamItemButton(
                                stream = stream,
                                isSelected = isSelected,
                                onClick = {
                                    onStreamSelected(stream)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            } else {
                if (isLoadingStreams && enabledStreams.isEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CyanAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Decrypting live FHD servers...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                } else if (enabledStreams.isEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "No active stream servers are currently online for this event.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.5.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enabledStreams.forEach { stream ->
                            val isSelected = currentSelectedStream?.id == stream.id
                            StreamItemButton(
                                stream = stream,
                                isSelected = isSelected,
                                onClick = {
                                    onStreamSelected(stream)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                if (!approvedHighlightUrl.isNullOrBlank() && onWatchHighlight != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onWatchHighlight(approvedHighlightUrl)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("watch_highlight_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldYellow.copy(alpha = 0.2f),
                            contentColor = GoldYellow
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Watch Highlight",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Official Highlight",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun StreamItemButton(
    stream: StreamSource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 1.3.dp else 1.dp,
                color = if (isSelected) CyanAccent else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag("stream_option_${stream.name.lowercase().replace(" ", "_")}"),
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stream.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Instant Direct Server • Low Latency",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 9.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            QualityBadge(quality = stream.quality)
        }
    }
}

@Composable
fun QualityBadge(quality: String) {
    val label = when {
        quality.uppercase().contains("FHD") || quality.contains("1080") -> "FHD"
        quality.uppercase().contains("SD") || quality.contains("480") -> "SD"
        else -> "FHD"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CyanAccent.copy(alpha = 0.16f))
            .border(1.dp, CyanAccent.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = CyanAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
        )
    }
}

