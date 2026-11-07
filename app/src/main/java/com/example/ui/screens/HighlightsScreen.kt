package com.example.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsCricket
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
import com.example.ui.theme.*

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
            .background(Color.Transparent)
    ) {
        // Modern Frosted Glass Sport Filter Bar for Highlights
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightSportCapsuleItem(
                sportType = SportType.ALL,
                label = "All",
                badgeCount = totalCount,
                isSelected = selectedSport == SportType.ALL,
                onClick = { selectedSport = SportType.ALL }
            )

            if (footballCount > 0 || totalCount == 0) {
                HighlightSportCapsuleItem(
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
                HighlightSportCapsuleItem(
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
                HighlightSportCapsuleItem(
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
                HighlightSportCapsuleItem(
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
                HighlightSportCapsuleItem(
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
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                contentPadding = PaddingValues(top = 4.dp, bottom = 86.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
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
private fun HighlightSportCapsuleItem(
    sportType: SportType,
    label: String,
    badgeCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val emoji = when (sportType) {
        SportType.ALL -> "🎬"
        SportType.FOOTBALL -> "⚽"
        SportType.CRICKET -> "🏏"
        SportType.BOXING -> "🥊"
        SportType.BASKETBALL -> "🏀"
        SportType.RACING -> "🏎️"
        else -> "⚡"
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(colors = listOf(CyanAccent, SkyBlue))
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (isSelected) CyanAccent else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("highlight_sport_${label.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, fontSize = 11.5.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isSelected) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isSelected) Color.Black.copy(alpha = 0.22f) else LiveRed)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                    )
                )
            }
        }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.36f),
                        GoldYellow.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("highlight_card_${item.id}"),
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
            // 1. Compact Frosted Top Bar: League Pill on Left + Time/Date Pill on Right
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
                    if (item.eventLogo.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.eventLogo)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.tournament,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(12.dp),
                            error = {
                                Icon(
                                    imageVector = if (item.sport == SportType.CRICKET) Icons.Default.SportsCricket else Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = GoldYellow,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = if (item.sport == SportType.CRICKET) Icons.Default.SportsCricket else Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = GoldYellow,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.timeText.isNotBlank() || item.dateText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 7.dp, vertical = 1.5.dp)
                    ) {
                        if (item.timeText.isNotBlank()) {
                            Text(
                                text = item.timeText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CyanAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                        if (item.timeText.isNotBlank() && item.dateText.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (item.dateText.isNotBlank()) {
                            Text(
                                text = item.dateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 8.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 2. Compact Team Shields Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    HighlightTeamLogo(
                        logoUrl = item.team1Logo,
                        teamName = team1Display
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = team1Display,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee()
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(0.8.dp, GoldYellow.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 8.dp, vertical = 1.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = GoldYellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    HighlightTeamLogo(
                        logoUrl = item.team2Logo,
                        teamName = team2Display
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = team2Display,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Compact Frosted Glass Bottom Action Bar
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
                Text(
                    text = "★ Official Match Recap",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldYellow,
                        fontSize = 9.5.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(colors = listOf(GoldYellow, CyanAccent))
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "WATCH RECAP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
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
        if (logoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = teamName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .padding(2.dp),
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
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, CyanAccent, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall.copy(
                color = CyanAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.5.sp
            )
        )
    }
}
