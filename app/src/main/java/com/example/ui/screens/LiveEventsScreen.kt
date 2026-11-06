package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crex.CrexDashboardState
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.ui.components.MatchCard
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiveEventsScreen(
    matches: List<LiveMatch>,
    crexState: CrexDashboardState = CrexDashboardState(),
    selectedSport: SportType,
    onSportSelect: (SportType) -> Unit,
    selectedStatus: MatchStatus?,
    onStatusSelect: (MatchStatus?) -> Unit,
    onManualRefresh: () -> Unit = {},
    onMatchClick: (LiveMatch) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onStreamSelectClick: (LiveMatch) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    val totalCount = matches.size
    val cricketCount = remember(matches) { matches.count { it.sport == SportType.CRICKET } }
    val footballCount = remember(matches) { matches.count { it.sport == SportType.FOOTBALL } }
    val boxingCount = remember(matches) { matches.count { it.sport == SportType.BOXING } }
    val basketballCount = remember(matches) { matches.count { it.sport == SportType.BASKETBALL } }
    val racingCount = remember(matches) { matches.count { it.sport == SportType.RACING } }
    val tennisCount = remember(matches) { matches.count { it.sport == SportType.TENNIS } }

    val sportFilteredMatches = remember(matches, selectedSport, searchQuery) {
        matches.filter { match ->
            val sportMatches = when (selectedSport) {
                SportType.ALL -> true
                else -> match.sport == selectedSport
            }
            val queryMatches = if (searchQuery.isBlank()) true else {
                match.team1Name.contains(searchQuery, ignoreCase = true) ||
                    match.team2Name.contains(searchQuery, ignoreCase = true) ||
                    match.team1Code.contains(searchQuery, ignoreCase = true) ||
                    match.team2Code.contains(searchQuery, ignoreCase = true) ||
                    match.tournament.contains(searchQuery, ignoreCase = true)
            }
            sportMatches && queryMatches
        }
    }

    val allStatusCount = sportFilteredMatches.size
    val liveStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count { it.status == MatchStatus.LIVE }
    }
    val upcomingStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count { it.status == MatchStatus.UPCOMING || it.status == MatchStatus.UNKNOWN }
    }
    val finishedStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count { it.status == MatchStatus.FINISHED }
    }

    val filteredMatches = remember(sportFilteredMatches, selectedStatus) {
        when (selectedStatus) {
            null -> sportFilteredMatches
            MatchStatus.UPCOMING -> sportFilteredMatches.filter {
                it.status == MatchStatus.UPCOMING || it.status == MatchStatus.UNKNOWN
            }
            else -> sportFilteredMatches.filter { it.status == selectedStatus }
        }
    }

    val isCrexLoading = crexState.liveState.isLoading ||
        crexState.upcomingState.isLoading ||
        crexState.finishedState.isLoading

    val isServingStaleCache = remember(filteredMatches, crexState) {
        filteredMatches.any { it.isStale } ||
            crexState.liveState.isStale ||
            crexState.upcomingState.isStale ||
            crexState.finishedState.isStale
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
    ) {
        // 1. Top Sport Selector Row (All, Cricket, Football, Boxing, Basketball, Motorsport) matching screenshot
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SportIconTopCard(
                sportType = SportType.ALL,
                badgeCount = totalCount,
                isSelected = selectedSport == SportType.ALL,
                showTextLabel = selectedSport == SportType.ALL,
                label = "All",
                onClick = { onSportSelect(SportType.ALL) }
            )

            SportIconTopCard(
                sportType = SportType.CRICKET,
                badgeCount = cricketCount,
                isSelected = selectedSport == SportType.CRICKET,
                showTextLabel = selectedSport == SportType.CRICKET,
                label = "Cricket",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.CRICKET) SportType.ALL else SportType.CRICKET)
                }
            )

            SportIconTopCard(
                sportType = SportType.FOOTBALL,
                badgeCount = footballCount,
                isSelected = selectedSport == SportType.FOOTBALL,
                showTextLabel = selectedSport == SportType.FOOTBALL,
                label = "Football",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.FOOTBALL) SportType.ALL else SportType.FOOTBALL)
                }
            )

            SportIconTopCard(
                sportType = SportType.BOXING,
                badgeCount = boxingCount,
                isSelected = selectedSport == SportType.BOXING,
                showTextLabel = selectedSport == SportType.BOXING,
                label = "Boxing",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.BOXING) SportType.ALL else SportType.BOXING)
                }
            )

            SportIconTopCard(
                sportType = SportType.BASKETBALL,
                badgeCount = basketballCount,
                isSelected = selectedSport == SportType.BASKETBALL,
                showTextLabel = selectedSport == SportType.BASKETBALL,
                label = "Basketball",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.BASKETBALL) SportType.ALL else SportType.BASKETBALL)
                }
            )

            SportIconTopCard(
                sportType = SportType.RACING,
                badgeCount = racingCount,
                isSelected = selectedSport == SportType.RACING,
                showTextLabel = selectedSport == SportType.RACING,
                label = "Motorsport",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.RACING) SportType.ALL else SportType.RACING)
                }
            )

            if (tennisCount > 0) {
                SportIconTopCard(
                    sportType = SportType.TENNIS,
                    badgeCount = tennisCount,
                    isSelected = selectedSport == SportType.TENNIS,
                    showTextLabel = selectedSport == SportType.TENNIS,
                    label = "Tennis",
                    onClick = {
                        onSportSelect(if (selectedSport == SportType.TENNIS) SportType.ALL else SportType.TENNIS)
                    }
                )
            }
        }

        // 2. Status Filter Pills Row: All (74), Live (13), Upcoming (15), Finished (...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusFilterPill(
                label = "All ($allStatusCount)",
                isSelected = selectedStatus == null,
                onClick = { onStatusSelect(null) },
                tag = "filter_all"
            )
            StatusFilterPill(
                label = "Live ($liveStatusCount)",
                isSelected = selectedStatus == MatchStatus.LIVE,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.LIVE) null else MatchStatus.LIVE)
                },
                tag = "filter_live"
            )
            StatusFilterPill(
                label = "Upcoming ($upcomingStatusCount)",
                isSelected = selectedStatus == MatchStatus.UPCOMING,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.UPCOMING) null else MatchStatus.UPCOMING)
                },
                tag = "filter_upcoming"
            )
            StatusFilterPill(
                label = "Finished ($finishedStatusCount)",
                isSelected = selectedStatus == MatchStatus.FINISHED,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.FINISHED) null else MatchStatus.FINISHED)
                },
                tag = "filter_finished"
            )
        }

        if (isServingStaleCache) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "সর্বশেষ ক্যাশ করা ম্যাচ তালিকা দেখানো হচ্ছে (Showing cached feed)",
                        style = MaterialTheme.typography.labelSmall.copy(color = GoldYellow),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onManualRefresh,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Retry", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Match Cards List
        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCrexLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF00D1C1),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Matches Found (Yesterday, Today, Tomorrow)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onManualRefresh,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sync Matches Now", color = CyanAccent)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredMatches,
                    key = { "${it.sport.name}_${it.status}_${it.id}" }
                ) { match ->
                    MatchCard(
                        match = match,
                        onClick = { onMatchClick(match) },
                        onFavoriteToggle = { onFavoriteToggle(match.id) },
                        onStreamSelectClick = { onStreamSelectClick(match) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SportIconTopCard(
    sportType: SportType,
    badgeCount: Int,
    isSelected: Boolean,
    showTextLabel: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF223044)
    val borderWidth = if (isSelected) 1.6.dp else 1.2.dp
    val bgColor = if (isSelected) Color(0xFF101C2A) else Color(0xFF121A28)

    Box(
        modifier = Modifier
            .height(70.dp)
            .widthIn(min = if (showTextLabel) 114.dp else 72.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (showTextLabel) 12.dp else 10.dp, vertical = 8.dp)
            .testTag("sport_chip_${label.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                when (sportType) {
                    SportType.ALL -> AllSportOutlineIcon()
                    SportType.CRICKET -> CricketOutlineIcon()
                    SportType.FOOTBALL -> FootballOutlineIcon()
                    SportType.BOXING -> BoxingOutlineIcon()
                    SportType.BASKETBALL -> BasketballOutlineIcon()
                    SportType.RACING -> RacingCarOutlineIcon()
                    else -> AllSportOutlineIcon()
                }

                // Red circular count badge with crisp white border at top-left (matching screenshot)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-3).dp, y = (-2).dp)
                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .border(1.4.dp, Color.White, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (showTextLabel) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun StatusFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF233246)
    val bgColor = if (isSelected) Color(0xFF112634) else Color(0xFF121A28)

    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = Color.White,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun AllSportOutlineIcon() {
    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c = Offset(w / 2f, h / 2f)
            val outerStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val innerStroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)

            // Outer circular arrows wrapping around "ALL"
            val outerDiameter = size.minDimension * 0.86f
            val arcTopLeft = Offset((w - outerDiameter) / 2f, (h - outerDiameter) / 2f)
            drawArc(
                color = Color.White,
                startAngle = -140f,
                sweepAngle = 295f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = Size(outerDiameter, outerDiameter),
                style = outerStroke
            )

            // Arrowhead at bottom-right of outer circular arrow
            val arrowTip = Offset(w * 0.80f, h * 0.78f)
            val arrowPath = Path().apply {
                moveTo(arrowTip.x, arrowTip.y - h * 0.13f)
                lineTo(arrowTip.x, arrowTip.y)
                lineTo(arrowTip.x - w * 0.13f, arrowTip.y)
            }
            drawPath(arrowPath, color = Color.White, style = outerStroke)

            // Inner circle surrounding "ALL"
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.30f,
                center = c,
                style = innerStroke
            )
        }
        Text(
            text = "ALL",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.5.sp,
                letterSpacing = 0.4.sp
            )
        )
    }
}

@Composable
internal fun CricketOutlineIcon() {
    Canvas(modifier = Modifier.size(46.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Left stump (angled slightly left as hit by ball)
        rotate(degrees = -9f, pivot = Offset(w * 0.34f, h * 0.88f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.31f, h * 0.32f),
                size = Size(w * 0.06f, h * 0.56f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = stroke)
            )
        }

        // Middle stump
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.46f, h * 0.36f),
            size = Size(w * 0.06f, h * 0.52f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = stroke)
        )

        // Right stump (angled right as hit by ball)
        rotate(degrees = 15f, pivot = Offset(w * 0.62f, h * 0.88f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.60f, h * 0.30f),
                size = Size(w * 0.06f, h * 0.58f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = stroke)
            )
        }

        // Cricket ball hitting center-left of stumps
        val ballCenter = Offset(w * 0.45f, h * 0.47f)
        val ballRadius = w * 0.10f
        drawCircle(color = Color(0xFF121A28), radius = ballRadius, center = ballCenter)
        drawCircle(color = Color.White, radius = ballRadius, center = ballCenter, style = Stroke(width = stroke))
        drawLine(
            color = Color.White,
            start = Offset(ballCenter.x - ballRadius * 0.65f, ballCenter.y - ballRadius * 0.65f),
            end = Offset(ballCenter.x + ballRadius * 0.65f, ballCenter.y + ballRadius * 0.65f),
            strokeWidth = stroke * 0.85f
        )

        // Flying left bail at top
        rotate(degrees = -28f, pivot = Offset(w * 0.40f, h * 0.16f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.32f, h * 0.13f),
                size = Size(w * 0.16f, h * 0.06f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = stroke)
            )
        }

        // Flying right bail at top-right
        rotate(degrees = 32f, pivot = Offset(w * 0.66f, h * 0.18f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.58f, h * 0.15f),
                size = Size(w * 0.16f, h * 0.06f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = stroke)
            )
        }

        // Impact sparks / lines around flying bails
        drawLine(Color.White, Offset(w * 0.45f, h * 0.28f), Offset(w * 0.43f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.52f, h * 0.28f), Offset(w * 0.54f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun FootballOutlineIcon() {
    Canvas(modifier = Modifier.size(46.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val c = Offset(w * 0.58f, h * 0.44f)
        val r = w * 0.30f

        // Soccer ball outer circle
        drawCircle(color = Color.White, radius = r, center = c, style = Stroke(width = stroke))

        // Inner pentagon and seam spokes
        val pentPath = Path()
        val pr = r * 0.44f
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            val px = c.x + (pr * cos(a)).toFloat()
            val py = c.y + (pr * sin(a)).toFloat()
            if (i == 0) pentPath.moveTo(px, py) else pentPath.lineTo(px, py)
            val ox = c.x + (r * cos(a)).toFloat()
            val oy = c.y + (r * sin(a)).toFloat()
            drawLine(Color.White, Offset(px, py), Offset(ox, oy), strokeWidth = stroke * 0.85f)
        }
        pentPath.close()
        drawPath(pentPath, color = Color.White, style = Stroke(width = stroke * 0.9f))

        // Speed motion lines + dots at bottom-left matching screenshot
        drawLine(Color.White, Offset(w * 0.14f, h * 0.66f), Offset(w * 0.27f, h * 0.53f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.16f, h * 0.84f), Offset(w * 0.36f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.34f, h * 0.84f), Offset(w * 0.48f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = stroke * 0.6f, center = Offset(w * 0.28f, h * 0.90f))
    }
}

@Composable
internal fun BoxingOutlineIcon() {
    Canvas(modifier = Modifier.size(46.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Left boxing glove (behind/left)
        rotate(degrees = -20f, pivot = Offset(w * 0.36f, h * 0.54f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.16f, h * 0.26f),
                size = Size(w * 0.34f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.15f, w * 0.15f),
                style = Stroke(width = stroke)
            )
            // Cuff
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.21f, h * 0.62f),
                size = Size(w * 0.24f, h * 0.16f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = stroke)
            )
        }

        // Right boxing glove (foreground/right)
        rotate(degrees = 20f, pivot = Offset(w * 0.62f, h * 0.52f)) {
            drawRoundRect(
                color = Color(0xFF121A28),
                topLeft = Offset(w * 0.44f, h * 0.24f),
                size = Size(w * 0.36f, h * 0.38f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.44f, h * 0.24f),
                size = Size(w * 0.36f, h * 0.38f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                style = Stroke(width = stroke)
            )
            // Thumb curve inside glove
            drawArc(
                color = Color.White,
                startAngle = 110f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.42f, h * 0.34f),
                size = Size(w * 0.18f, h * 0.20f),
                style = Stroke(width = stroke)
            )
            // Cuff with inner patch
            drawRoundRect(
                color = Color(0xFF121A28),
                topLeft = Offset(w * 0.49f, h * 0.62f),
                size = Size(w * 0.26f, h * 0.17f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.49f, h * 0.62f),
                size = Size(w * 0.26f, h * 0.17f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = stroke)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(w * 0.55f, h * 0.66f),
                size = Size(w * 0.14f, h * 0.09f),
                style = Stroke(width = stroke * 0.8f)
            )
        }
    }
}

@Composable
internal fun BasketballOutlineIcon() {
    Canvas(modifier = Modifier.size(46.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Backboard outer rounded rectangle
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.14f, h * 0.24f),
            size = Size(w * 0.68f, h * 0.44f),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = stroke)
        )

        // Inner shooter's target square on backboard
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.34f, h * 0.42f),
            size = Size(w * 0.28f, h * 0.24f),
            style = Stroke(width = stroke)
        )

        // Basketball at top-right overlapping backboard (matching screenshot)
        val ballCenter = Offset(w * 0.68f, h * 0.28f)
        val ballRadius = w * 0.20f
        drawCircle(color = Color(0xFF121A28), radius = ballRadius, center = ballCenter)
        drawCircle(color = Color.White, radius = ballRadius, center = ballCenter, style = Stroke(width = stroke))
        // Basketball seam lines
        drawLine(
            color = Color.White,
            start = Offset(ballCenter.x, ballCenter.y - ballRadius),
            end = Offset(ballCenter.x, ballCenter.y + ballRadius),
            strokeWidth = stroke * 0.85f
        )
        drawLine(
            color = Color.White,
            start = Offset(ballCenter.x - ballRadius, ballCenter.y),
            end = Offset(ballCenter.x + ballRadius, ballCenter.y),
            strokeWidth = stroke * 0.85f
        )
        drawArc(
            color = Color.White,
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(ballCenter.x - ballRadius * 1.1f, ballCenter.y - ballRadius * 0.8f),
            size = Size(ballRadius * 1.0f, ballRadius * 1.6f),
            style = Stroke(width = stroke * 0.85f)
        )
        drawArc(
            color = Color.White,
            startAngle = 120f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(ballCenter.x + ballRadius * 0.1f, ballCenter.y - ballRadius * 0.8f),
            size = Size(ballRadius * 1.0f, ballRadius * 1.6f),
            style = Stroke(width = stroke * 0.85f)
        )

        // Hoop rim bar
        drawLine(
            color = Color.White,
            start = Offset(w * 0.30f, h * 0.68f),
            end = Offset(w * 0.66f, h * 0.68f),
            strokeWidth = stroke * 1.2f,
            cap = StrokeCap.Round
        )

        // Hanging net outline & diagonal mesh
        val netPath = Path().apply {
            moveTo(w * 0.33f, h * 0.68f)
            lineTo(w * 0.38f, h * 0.90f)
            lineTo(w * 0.58f, h * 0.90f)
            lineTo(w * 0.63f, h * 0.68f)
        }
        drawPath(netPath, color = Color.White, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawLine(Color.White, Offset(w * 0.35f, h * 0.74f), Offset(w * 0.54f, h * 0.90f), strokeWidth = stroke * 0.75f)
        drawLine(Color.White, Offset(w * 0.46f, h * 0.68f), Offset(w * 0.60f, h * 0.84f), strokeWidth = stroke * 0.75f)
        drawLine(Color.White, Offset(w * 0.61f, h * 0.74f), Offset(w * 0.42f, h * 0.90f), strokeWidth = stroke * 0.75f)
        drawLine(Color.White, Offset(w * 0.50f, h * 0.68f), Offset(w * 0.36f, h * 0.84f), strokeWidth = stroke * 0.75f)
    }
}

@Composable
internal fun RacingCarOutlineIcon() {
    Canvas(modifier = Modifier.size(46.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Rear wing / spoiler
        val wingPath = Path().apply {
            moveTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.24f, h * 0.38f)
            lineTo(w * 0.26f, h * 0.50f)
        }
        drawPath(wingPath, color = Color.White, style = Stroke(width = stroke, join = StrokeJoin.Round))

        // Race car chassis body
        val bodyPath = Path().apply {
            moveTo(w * 0.10f, h * 0.64f)
            lineTo(w * 0.14f, h * 0.48f)
            lineTo(w * 0.38f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.36f)
            lineTo(w * 0.68f, h * 0.48f)
            lineTo(w * 0.90f, h * 0.52f)
            lineTo(w * 0.92f, h * 0.64f)
            close()
        }
        drawPath(bodyPath, color = Color.White, style = Stroke(width = stroke, join = StrokeJoin.Round))

        // Left & right wheels with inner hubs
        val leftWheel = Offset(w * 0.26f, h * 0.66f)
        val rightWheel = Offset(w * 0.74f, h * 0.66f)
        drawCircle(color = Color(0xFF121A28), radius = w * 0.12f, center = leftWheel)
        drawCircle(color = Color.White, radius = w * 0.12f, center = leftWheel, style = Stroke(width = stroke))
        drawCircle(color = Color.White, radius = w * 0.05f, center = leftWheel, style = Stroke(width = stroke * 0.85f))

        drawCircle(color = Color(0xFF121A28), radius = w * 0.12f, center = rightWheel)
        drawCircle(color = Color.White, radius = w * 0.12f, center = rightWheel, style = Stroke(width = stroke))
        drawCircle(color = Color.White, radius = w * 0.05f, center = rightWheel, style = Stroke(width = stroke * 0.85f))
    }
}
