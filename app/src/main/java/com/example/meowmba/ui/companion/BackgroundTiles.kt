package com.example.meowmba.ui.companion

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tile overlay for Memba ecosystem applications.
 */
@Composable
fun BackgroundTiles(
    visible: Boolean,
    onClose: () -> Unit,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .clickable(enabled = false) { }
            ) {
                Text(
                    text = "Memba Ecosystem",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val themedTiles = tileItems
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(themedTiles) { item ->
                        TileItem(item, onClick = { onTileClick(item.name) })
                    }
                }
            }
        }
    }
}

@Composable
fun TileItem(item: MembaTile, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.name,
            tint = item.color,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // One line: "Background" wrapping to "Backgroun / d" made the whole grid look broken.
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

data class MembaTile(val name: String, val icon: ImageVector, val color: Color)

val tileItems: List<MembaTile> @Composable get() = listOf(
    MembaTile("Memory", Icons.Default.Terminal, MaterialTheme.colorScheme.primary),
    MembaTile("Studio", Icons.Default.Palette, Color(0xFFFF5CA8)),
    MembaTile("Hardware", Icons.Default.Memory, Color(0xFF00E5FF)),
    MembaTile("Robot", Icons.Default.Android, Color(0xFF4DB6AC)),
    MembaTile("Projects", Icons.Default.Folder, Color(0xFF00E676)),
    MembaTile("Tools", Icons.Default.Build, Color(0xFFFFD600)),
    MembaTile("Agents", Icons.Default.SmartToy, Color(0xFFAA00FF)),
    MembaTile("Privacy", Icons.Default.Security, MaterialTheme.colorScheme.primary),
    MembaTile("Notes", Icons.Default.Book, Color(0xFFD2A8FF)),
    MembaTile("NAV", Icons.Default.Navigation, Color(0xFFFF6D00)),
    MembaTile("Weather", Icons.Default.WbSunny, Color(0xFF29B6F6)),
    MembaTile("Background", Icons.Default.Wallpaper, Color(0xFFEC407A)),
    MembaTile("Avatar", Icons.Default.Face, Color(0xFF00BFA5)),
    MembaTile("Settings", Icons.Default.Settings, Color(0xFFBDBDBD))
)
