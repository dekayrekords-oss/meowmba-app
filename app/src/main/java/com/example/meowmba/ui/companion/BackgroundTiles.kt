package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

/** Modal navigation workbench. Original route keys stay stable despite clearer display labels. */
@Composable
fun BackgroundTiles(
    visible: Boolean,
    onClose: () -> Unit,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedTile: String? = null,
) {
    if (!visible) return
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val themedTiles = tileItems
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding().padding(AvatarSetupTokens.page), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(Modifier.padding(AvatarSetupTokens.page)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Memba", style = MaterialTheme.typography.headlineSmall)
                            Text("Your workspace", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close workspace") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = AvatarSetupTokens.item), color = MaterialTheme.colorScheme.outlineVariant)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive((176f * fontScale).dp),
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(AvatarSetupTokens.compact),
                        horizontalArrangement = Arrangement.spacedBy(AvatarSetupTokens.compact),
                    ) {
                        items(themedTiles, key = { it.name }) { item ->
                            TileItem(item, onClick = { onTileClick(item.name) }, selected = item.name == selectedTile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TileItem(item: MembaTile, onClick: () -> Unit, selected: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = AvatarSetupTokens.item, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AvatarSetupTokens.item)) {
            Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(24.dp))
            Text(tileDisplayName(item.name), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = if (selected) "Selected" else null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

private fun tileDisplayName(route: String): String = when (route) {
    "LLM" -> "Models"
    "NAV" -> "Navigate"
    else -> route
}

data class MembaTile(val name: String, val icon: ImageVector, val color: Color)

val tileItems: List<MembaTile> @Composable get() = listOf(
    MembaTile("Memory", Icons.Default.Terminal, MaterialTheme.colorScheme.primary),
    MembaTile("Files", Icons.Default.FolderOpen, MaterialTheme.colorScheme.secondary),
    MembaTile("Studio", Icons.Default.Palette, MaterialTheme.colorScheme.tertiary),
    MembaTile("LLM", Icons.Default.Memory, MaterialTheme.colorScheme.primary),
    MembaTile("Robot", Icons.Default.Android, MaterialTheme.colorScheme.secondary),
    MembaTile("Projects", Icons.Default.Folder, MaterialTheme.colorScheme.primary),
    MembaTile("Tools", Icons.Default.Build, MaterialTheme.colorScheme.tertiary),
    MembaTile("Agents", Icons.Default.SmartToy, MaterialTheme.colorScheme.primary),
    MembaTile("Privacy", Icons.Default.Security, MaterialTheme.colorScheme.primary),
    MembaTile("Notes", Icons.Default.Book, MaterialTheme.colorScheme.secondary),
    MembaTile("NAV", Icons.Default.Navigation, MaterialTheme.colorScheme.tertiary),
    MembaTile("Weather", Icons.Default.WbSunny, MaterialTheme.colorScheme.tertiary),
    MembaTile("Background", Icons.Default.Wallpaper, MaterialTheme.colorScheme.secondary),
    MembaTile("Avatar", Icons.Default.Face, MaterialTheme.colorScheme.primary),
    MembaTile("Settings", Icons.Default.Settings, MaterialTheme.colorScheme.secondary),
)
