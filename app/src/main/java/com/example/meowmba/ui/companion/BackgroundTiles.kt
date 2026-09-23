package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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
    if (visible) androidx.activity.compose.BackHandler(onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it / 8 } + fadeIn(),
        exit = slideOutVertically { -it / 8 } + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .safeDrawingPadding()
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.78f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Meow menu", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                        OutlinedButton(onClick = onClose,
                            modifier = Modifier.semantics { contentDescription = "Close menu" }) { Text("Close") }
                    }
                    Text("Your companion, tools & settings", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    val themedTiles = tileItems
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(themedTiles, key = { it.name }) { item ->
                            TileItem(item, onClick = { onTileClick(item.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TileItem(item: MembaTile, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(item.name, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

data class MembaTile(val name: String, val icon: ImageVector, val color: Color)

val tileItems: List<MembaTile> @Composable get() = listOf(
    MembaTile("Settings", Icons.Default.Settings, MaterialTheme.colorScheme.tertiary),
    MembaTile("Flow", Icons.Default.List, MaterialTheme.colorScheme.primary),
    MembaTile("Desk", Icons.Default.Desk, MaterialTheme.colorScheme.secondary),
    MembaTile("Computer", Icons.Default.Computer, MaterialTheme.colorScheme.primary),
    MembaTile("Memory", Icons.Default.Terminal, MaterialTheme.colorScheme.primary),
    MembaTile("Files", Icons.Default.FolderOpen, MaterialTheme.colorScheme.secondary),
    MembaTile("Vending", Icons.Default.Storefront, MaterialTheme.colorScheme.tertiary),
    MembaTile("LLM", Icons.Default.Memory, MaterialTheme.colorScheme.primary),
    MembaTile("Brain", Icons.Default.Hub, MaterialTheme.colorScheme.secondary),
    MembaTile("Robot", Icons.Default.Android, MaterialTheme.colorScheme.tertiary),
    MembaTile("Projects", Icons.Default.Folder, MaterialTheme.colorScheme.primary),
    MembaTile("Tools", Icons.Default.Build, MaterialTheme.colorScheme.secondary),
    MembaTile("Agents", Icons.Default.SmartToy, MaterialTheme.colorScheme.tertiary),
    MembaTile("Privacy", Icons.Default.Security, MaterialTheme.colorScheme.primary),
    MembaTile("Notes", Icons.Default.Book, MaterialTheme.colorScheme.primary),
    MembaTile("Auto", Icons.Default.DirectionsCar, MaterialTheme.colorScheme.secondary),
    MembaTile("Weather", Icons.Default.WbSunny, MaterialTheme.colorScheme.tertiary),
    MembaTile("Background", Icons.Default.Wallpaper, MaterialTheme.colorScheme.primary),
    MembaTile("Avatar", Icons.Default.Face, MaterialTheme.colorScheme.secondary),
)
