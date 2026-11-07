package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryItem
import com.example.ui.theme.*

@Composable
fun CategoriesScreen(
    categories: List<CategoryItem>,
    onCategoryClick: (CategoryItem) -> Unit,
    searchQuery: String = ""
) {
    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredCategories, key = { it.id }) { cat ->
                CategoryGridTile(
                    category = cat,
                    onClick = { onCategoryClick(cat) }
                )
            }
        }
    }
}

@Composable
fun CategoryGridTile(
    category: CategoryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.34f),
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("cat_tile_${category.name.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.11f))
                    .border(0.8.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val icon: ImageVector = when {
                    category.name.contains("LIVE", ignoreCase = true) -> Icons.Default.Sensors
                    category.name.contains("UPCOMING", ignoreCase = true) -> Icons.Default.Schedule
                    category.name.contains("CRICKET", ignoreCase = true) ||
                        category.name.contains("FANCODE", ignoreCase = true) ||
                        category.name.contains("ICC", ignoreCase = true) ||
                        category.name.contains("CRIC", ignoreCase = true) -> Icons.Default.SportsCricket
                    category.name.contains("FOOTBALL", ignoreCase = true) -> Icons.Default.SportsSoccer
                    category.name.contains("SONY", ignoreCase = true) -> Icons.Default.LiveTv
                    category.name.contains("PAKISTAN", ignoreCase = true) ||
                        category.name.contains("TAPMAD", ignoreCase = true) -> Icons.Default.SportsCricket
                    category.name.contains("HD", ignoreCase = true) -> Icons.Default.Hd
                    category.name.contains("SD", ignoreCase = true) -> Icons.Default.Sd
                    else -> Icons.Default.Tv
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${category.channelCount} Streams",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 9.5.sp
                    )
                )
            }
        }
    }
}
