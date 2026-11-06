package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.HighlightItem
import com.example.data.model.SportType
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HighlightsScreen(
    highlights: List<HighlightItem>,
    searchQuery: String = "",
    onHighlightClick: (HighlightItem) -> Unit
) {
    var selectedSport by remember { mutableStateOf(SportType.ALL) }

    val totalCount = highlights.size
    val footballCount = remember(highlights) { highlights.count { it.sport == SportType.FOOTBALL } }
    val cricketCount = remember(highlights) { highlights.count { it.sport == SportType.CRICKET } }
    val boxingCount = remember(highlights) { highlights.count { it.sport == SportType.BOXING } }
    val basketballCount = remember(highlights) { highlights.count { it.sport == SportType.BASKETBALL } }
    val racingCount = remember(highlights) { highlights.count { it.sport == SportType.RACING } }

    val filteredHighlights = remember(highlights, selectedSport, searchQuery) {
        highlights.filter { item ->
            val sportMatches = when (selectedSport) {
                SportType.ALL -> true
                else -> item.sport == selectedSport
            }
            val queryMatches = if (searchQuery.isBlank()) {
                true
            } else {
                item.title.contains(searchQuery, ignoreCase = true) ||
                    item.team1Name.contains(searchQuery, ignoreCase = true) ||
                    item.team2Name.contains(searchQuery, ignoreCase = true) ||
                    item.tournament.contains(searchQuery, ignoreCase = true) ||
                    item.categoryLabel.contains(searchQuery, ignoreCase = true)
            }
            sportMatches && queryMatches
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
    ) {
        // Circular Sport Filter Row matching the Highlights screenshot (All 95, Football 91, Cricket 4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightSportCircleItem(
                sportType = SportType.ALL,
                label = "All",
                badgeCount = totalCount,
                isSelected = selectedSport == SportType.ALL,
                onClick = { selectedSport = SportType.ALL }
            )

            if (footballCount > 0 || totalCount == 0) {
                HighlightSportCircleItem(
                    sportType = SportType.FOOTBALL,
                    label = "Football",
                    badgeCount = footballCount,
                    isSelected = selectedSport == SportType.FOOTBALL,
                    onClick = {
                        selectedSport = if (selectedSport == SportType.FOOTBALL) SportType.ALL else SportType.FOOTBALL
                    }
                )
            }

            if (cricketCount > 0 || totalCount == 0) {
                HighlightSportCircleItem(
                    sportType = SportType.CRICKET,
                    label = "Cricket",
                    badgeCount = cricketCount,
                    isSelected = selectedSport == SportType.CRICKET,
                    onClick = {
                        selectedSport = if (selectedSport == SportType.CRICKET) SportType.ALL else SportType.CRICKET
                    }
                )
            }

            if (boxingCount > 0) {
                HighlightSportCircleItem(
                    sportType = SportType.BOXING,
                    label = "Boxing",
                    badgeCount = boxingCount,
                    isSelected = selectedSport == SportType.BOXING,
                    onClick = {
                        selectedSport = if (selectedSport == SportType.BOXING) SportType.ALL else SportType.BOXING
                    }
                )
            }

            if (basketballCount > 0) {
                HighlightSportCircleItem(
                    sportType = SportType.BASKETBALL,
                    label = "Basketball",
                    badgeCount = basketballCount,
                    isSelected = selectedSport == SportType.BASKETBALL,
                    onClick = {
                        selectedSport = if (selectedSport == SportType.BASKETBALL) SportType.ALL else SportType.BASKETBALL
                    }
                )
            }

            if (racingCount > 0) {
                HighlightSportCircleItem(
                    sportType = SportType.RACING,
                    label = "Motorsport",
                    badgeCount = racingCount,
                    isSelected = selectedSport == SportType.RACING,
                    onClick = {
                        selectedSport = if (selectedSport == SportType.RACING) SportType.ALL else SportType.RACING
                    }
                )
            }
        }

        if (filteredHighlights.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading Match Highlights...",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the refresh icon in the top bar to sync the latest Football & Cricket highlights.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredHighlights,
                    key = { it.id }
                ) { item ->
                    HighlightMatchCard(
                        item = item,
                        onClick = { onHighlightClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightSportCircleItem(
    sportType: SportType,
    label: String,
    badgeCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("highlight_sport_${label.lowercase()}")
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF151E2E))
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 2.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
                                ),
                                shape = CircleShape
                            )
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = Color(0xFF223044),
                                shape = CircleShape
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (sportType) {
                    SportType.ALL -> AllSportOutlineIcon()
                    SportType.FOOTBALL -> FootballOutlineIcon()
                    SportType.CRICKET -> CricketOutlineIcon()
                    SportType.BOXING -> BoxingOutlineIcon()
                    SportType.BASKETBALL -> BasketballOutlineIcon()
                    SportType.RACING -> RacingCarOutlineIcon()
                    else -> AllSportOutlineIcon()
                }
            }

            // Red count badge on top-right matching the Highlights screenshot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .border(1.4.dp, Color.White, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp
            ),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HighlightMatchCard(
    item: HighlightItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val team1Display = item.team1Name.ifBlank {
        item.title.substringBefore(" vs ", item.title.substringBefore(" - ", "Team 1")).trim()
    }
    val team2Display = item.team2Name.ifBlank {
        item.title.substringAfter(" vs ", item.title.substringAfter(" - ", "Team 2")).trim()
    }
    val headerTitle = buildString {
        val cat = item.categoryLabel.ifBlank {
            if (item.sport == SportType.CRICKET) "Cricket" else "Football"
        }
        append(cat)
        if (item.tournament.isNotBlank() && !item.tournament.equals(cat, ignoreCase = true)) {
            append(" | ")
            append(item.tournament)
        }
    }

    Surface(
        color = Color(0xFF151F30),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                color = Color(0xFF2A3B54),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("highlight_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Top Header Row: Event Logo + "Football | UEFA Europa League" on Left, "02:00 AM 06/11/2026" on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (item.eventLogo.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.eventLogo)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.tournament,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(20.dp),
                            error = {
                                Icon(
                                    imageVector = if (item.sport == SportType.CRICKET) Icons.Default.SportsCricket else Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = if (item.sport == SportType.CRICKET) Icons.Default.SportsCricket else Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.timeText.isNotBlank() || item.dateText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.timeText.isNotBlank()) {
                            Text(
                                text = item.timeText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF3B82F6),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                        if (item.timeText.isNotBlank() && item.dateText.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (item.dateText.isNotBlank()) {
                            Text(
                                text = item.dateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teams Row: Team 1 Crest + Team 1 Name | VS | Team 2 Name + Team 2 Crest
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Team
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightTeamLogo(
                        logoUrl = item.team1Logo,
                        teamName = team1Display
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = team1Display,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee()
                    )
                }

                // Center VS
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                // Right Team
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = team2Display,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    HighlightTeamLogo(
                        logoUrl = item.team2Logo,
                        teamName = team2Display
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightTeamLogo(
    logoUrl: String,
    teamName: String
) {
    val context = LocalContext.current
    val initials = remember(teamName) {
        teamName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "TV" }
    }

    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = teamName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(50.dp),
                error = {
                    FallbackCrestBadge(initials = initials)
                }
            )
        } else {
            FallbackCrestBadge(initials = initials)
        }
    }
}

@Composable
private fun FallbackCrestBadge(initials: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E2D45))
            .border(1.5.dp, Color(0xFF3B82F6), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        )
    }
}
