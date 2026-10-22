package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.OutlinedCard
import com.example.meowmba.ui.theme.FilterChip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A workspace over the existing actions, not a second agent or simulated dashboard. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ProfessionalWorkspace(
    state: CompanionState,
    viewModel: CompanionViewModel,
    avatar: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(Modifier.width(164.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Meowmba", style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(vertical = 20.dp))
                    WorkspaceAction("Assistant", Icons.AutoMirrored.Filled.Chat, viewModel::openChat)
                    WorkspaceAction("Projects", Icons.Default.Folder, viewModel::toggleProjectsScreen)
                    WorkspaceAction("Connections", Icons.Default.Link, viewModel::toggleConnectorsScreen)
                    WorkspaceAction("Notes", Icons.Default.Description, viewModel::toggleVaultScreen)
                    WorkspaceAction("All tools", Icons.Default.Apps, viewModel::openTiles)
                    HorizontalDivider()
                    WorkspaceAction("Appearance", Icons.Default.Palette, viewModel::toggleAvatarScreen)
                    WorkspaceAction("Settings", Icons.Default.Settings, viewModel::toggleSettings)
                }
                VerticalDivider()
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(if (wide) 28.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (wide) "Your workspace" else "Meowmba",
                            style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                        Text("A clear space to think and work.", color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = viewModel::toggleAvatarScreen) {
                        Icon(Icons.Default.Palette, "Customize appearance", tint = colors.onSurface)
                    }
                    IconButton(onClick = viewModel::toggleSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = colors.onSurface)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(if (wide) 180.dp else 136.dp).clip(RoundedCornerShape(28.dp))
                        // Was hard-coded to the old dark-mode background, which
                        // left a near-black panel in Light mode. surfaceVariant
                        // keeps the avatar stage distinct in both brightnesses.
                        // Touching the orb toggles the mic, same as the orb tap
                        // contract — the stage is a shortcut, not just a prop.
                        .background(colors.surfaceVariant)
                        .clickable { viewModel.onAvatarZoneClick(AvatarZone.EAR_LEFT) }) { avatar() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.interactionState.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        Text(state.activeTask ?: "Ready when you are.", color = colors.onSurfaceVariant)
                        TextButton(onClick = viewModel::toggleSetupWizard) { Text("Guided setup") }
                    }
                }
                state.captionText?.let {
                    Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(18.dp)) }
                }
                state.errorMessage?.let { Text(it, color = colors.error) }
                state.policeAlert?.let {
                    Card(onClick = viewModel::dismissPoliceAlert, modifier = Modifier.fillMaxWidth()) {
                        Text(it, Modifier.padding(16.dp), color = colors.error)
                    }
                }
                if (state.isNoting) {
                    Column {
                        OutlinedButton(onClick = viewModel::stopNoting) { Text("Local notes · Stop & summarize") }
                        Text("Records while this app is visible. Stops in the background. Summary stays in Vault.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("How can I help?", style = MaterialTheme.typography.headlineMedium)
                        Text("Start a conversation, pick up a project, or connect an app.", color = colors.onSurfaceVariant)
                        WorkspaceAction("Ask Meowmba anything…", Icons.AutoMirrored.Filled.Chat, viewModel::openChat)
                        Button(onClick = viewModel::onMicClick, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Mic, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.interactionState == InteractionState.LISTENING) "Stop listening" else "Talk to Meowmba")
                        }
                    }
                }
                Text("Projects", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                state.projectWork?.takeIf { it.projectId == state.activeProjectId }?.let { work ->
                    Card(onClick = { viewModel.openProject(work.projectId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Current project · ${work.name}", style = MaterialTheme.typography.titleMedium)
                            Text(work.currentTasks.firstOrNull()?.let { "${it.status.name.lowercase().replace('_', ' ')}: ${it.title}" }
                                ?: "No action running · ${work.remainingStepCount} plan steps unconfirmed")
                            Text("${work.artifacts.count { it.available }} saved files · Open plan & activity", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.projects.isEmpty()) {
                    Text("No projects yet. Open Projects to get started.", color = colors.onSurfaceVariant)
                } else {
                    state.projects.take(3).forEach { project ->
                        OutlinedCard(onClick = { viewModel.openProject(project.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(project.name, style = MaterialTheme.typography.titleMedium)
                                Text(project.kindLabel, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Wrap privacy chips rather than hiding a control on a narrow cover screen.
                    FilterChip(selected = state.isMicEnabled,
                        onClick = { viewModel.onAvatarZoneClick(AvatarZone.EAR_LEFT) },
                        label = { Text(if (state.isMicEnabled) "Mic on" else "Mic off") })
                    FilterChip(selected = state.isCameraEnabled,
                        onClick = { viewModel.onAvatarZoneClick(AvatarZone.EYE_LEFT) },
                        label = { Text(if (state.isCameraEnabled) "Camera on" else "Camera off") })
                    FilterChip(selected = !state.isMuted,
                        onClick = { viewModel.onAvatarZoneClick(AvatarZone.MOUTH) },
                        label = { Text(if (state.isMuted) "Muted" else "Voice on") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::toggleProjectsScreen, modifier = Modifier.weight(1f)) { Text("Projects") }
                    OutlinedButton(onClick = viewModel::toggleConnectorsScreen, modifier = Modifier.weight(1f)) { Text("Connections") }
                }
                WorkspaceAction("All tools and apps", Icons.Default.Apps, viewModel::openTiles)
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun WorkspaceAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f))
    }
}
