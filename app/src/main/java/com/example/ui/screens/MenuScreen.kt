package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppConfig
import com.example.ui.theme.*

@Composable
fun MenuScreen(
    config: AppConfig,
    userEmail: String? = null,
    onNetworkStreamClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    onCricketScoreClick: () -> Unit,
    onFootballScoreClick: () -> Unit,
    onFloatingPlayerClick: () -> Unit,
    onForceLowQualityToggle: (Boolean) -> Unit,
    onAdminPanelClick: () -> Unit,
    onGoogleSignInClick: (() -> Unit)? = null,
    onSignOutClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo & Header
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "Sports Live Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = config.appTitle,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
            )
        )

        Text(
            text = "Version ${config.version}",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 11.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Features Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "FEATURES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Menu Items List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MenuItemCard(
                icon = Icons.Default.Podcasts,
                title = "Network Stream",
                subtitle = "Play direct M3U8, MP4 or HLS link",
                onClick = onNetworkStreamClick
            )

            MenuItemCard(
                icon = Icons.Default.PlaylistPlay,
                title = "Playlists",
                subtitle = "Load IPTV M3U / M3U8 playlists",
                onClick = onPlaylistsClick
            )

            MenuItemCard(
                icon = Icons.Default.SmartDisplay,
                title = "Highlights",
                subtitle = "Match recaps & top moments",
                onClick = onHighlightsClick
            )

            MenuItemCard(
                icon = Icons.Default.SportsCricket,
                title = "Cricket Score",
                subtitle = "Live ball-by-ball score updates",
                onClick = onCricketScoreClick
            )

            MenuItemCard(
                icon = Icons.Default.SportsSoccer,
                title = "Football Score",
                subtitle = "FIFA World Cup, Nations League & International scores",
                onClick = onFootballScoreClick
            )

            MenuItemCard(
                icon = Icons.Default.PictureInPictureAlt,
                title = "Floating Player",
                subtitle = "Picture-in-picture background streaming",
                onClick = onFloatingPlayerClick
            )

            MenuItemCard(
                icon = Icons.Default.AdminPanelSettings,
                title = "Admin Control Panel",
                subtitle = "Real-time cloud sync, M3U8 scanner & match controller",
                onClick = onAdminPanelClick
            )

            if (userEmail != null) {
                MenuItemCard(
                    icon = Icons.Default.CloudDone,
                    title = "Real-Time Cloud Connected",
                    subtitle = "Signed in as $userEmail • Tap to sign out",
                    onClick = { onSignOutClick?.invoke() }
                )
            } else if (onGoogleSignInClick != null) {
                MenuItemCard(
                    icon = Icons.Default.CloudSync,
                    title = "Connect Google Cloud Sync",
                    subtitle = "Receive instant Admin stream updates & sync favourites",
                    onClick = onGoogleSignInClick
                )
            }

            // Force Low Quality Toggle Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .border(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.34f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(13.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = 0.11f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Force Low Quality",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "Forces lowest resolution on mobile data",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = config.forceLowQuality,
                        onCheckedChange = onForceLowQualityToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyanAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.testTag("switch_force_low_quality")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Footer
        Text(
            text = "${config.appTitle.uppercase()} • ULTRA HD SPORTS STREAMING ⚽",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.5.sp,
                letterSpacing = 1.sp
            )
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun MenuItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(13.dp)
            )
            .testTag("menu_item_${title.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.11f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
