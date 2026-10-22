package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AvatarMorphControls(state: CompanionState, viewModel: CompanionViewModel) {
    Column(Modifier.padding(vertical = 12.dp)) {
        VoiceToggle("Occasional idle characters", "Your selected avatar is home. While idle, another character may visit for 3–6 seconds, then your avatar returns. Visits happen at random, never during conversation or driving.", state.idleMorphEnabled, viewModel::setIdleMorphEnabled)
        VoiceToggle("Dance to music", "Your companion grooves while music plays and pauses for conversation or driving.", state.musicDancingEnabled, viewModel::setMusicDancingEnabled)
        Text(state.musicPlayback.label, style = MaterialTheme.typography.bodySmall)
        if (!state.musicPlayback.metadataAccess) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Text("To show the song and artist, allow Meow notification access in Android settings. Meow reads the player’s shared title and artist without recording the music.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Music access") }
        }
        SongRecognitionControls(state, viewModel)
        VoiceToggle("Include invented characters", "Use your saved generated forms too.", state.idleMorphIncludeGenerated, viewModel::setIdleMorphIncludeGenerated)
    }
}
