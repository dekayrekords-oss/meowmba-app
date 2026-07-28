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
                    .background(Color(0xFF1A1A1A))
                    .padding(24.dp)
                    .clickable(enabled = false) { }
            ) {
                Text(
                    text = "Memba Ecosystem",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(tileItems) { item ->
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
            .background(Color(0xFF2C2C2C))
            .padding(16.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.name,
            tint = item.color,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

data class MembaTile(val name: String, val icon: ImageVector, val color: Color)

val tileItems = listOf(
    MembaTile("Memory", Icons.Default.Terminal, Color(0xFF448AFF)),
    MembaTile("Hardware", Icons.Default.Memory, Color(0xFF00E5FF)),
    MembaTile("Projects", Icons.Default.Folder, Color(0xFF00E676)),
    MembaTile("Tools", Icons.Default.Build, Color(0xFFFFD600)),
    MembaTile("Agents", Icons.Default.SmartToy, Color(0xFFAA00FF)),
    MembaTile("Privacy", Icons.Default.Security, Color(0xFF3FB950)),
    MembaTile("Settings", Icons.Default.Settings, Color(0xFFBDBDBD))
)
