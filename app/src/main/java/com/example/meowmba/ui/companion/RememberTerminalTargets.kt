package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Database and keystore discovery belongs to opening chat, never animation
 * recomposition. Reopening refreshes edits made in Connections while closed. */
@Composable
internal fun rememberTerminalTargets(
    owner: Any,
    visible: Boolean,
    load: () -> List<CompanionTerminalTarget>,
): State<List<CompanionTerminalTarget>> = produceState(emptyList(), owner, visible) {
    if (visible) value = withContext(Dispatchers.IO) { load() }
}
