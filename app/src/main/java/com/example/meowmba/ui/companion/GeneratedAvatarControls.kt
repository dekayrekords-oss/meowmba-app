package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.Surface

/** A draft is never applied on typing or restoration. Only explicit Create/selection changes the form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneratedAvatarControls(
    current: GeneratedAvatarForm?,
    saved: List<GeneratedAvatarForm>,
    busy: Boolean,
    message: String?,
    isError: Boolean,
    onGenerate: (String) -> Unit,
    onCancel: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var request by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val canGenerate = request.isNotBlank() && !busy
    val submit = {
        if (canGenerate) {
            keyboard?.hide()
            onGenerate(request.trim())
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("generated-avatar-controls"),
        shape = RoundedCornerShape(AvatarSetupTokens.corner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Become anything", style = MaterialTheme.typography.titleLarge)
            Text(
                "Describe a creature, character or object. Your selected AI model draws a new cartoon form for Lumen, with the same brain and memory.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = request,
                onValueChange = { request = it.take(GeneratedAvatarForms.MAX_REQUEST) },
                modifier = Modifier.fillMaxWidth().testTag("generated-avatar-request"),
                enabled = !busy,
                label = { Text("What should I become?") },
                placeholder = { Text("A purple dragon with tiny wings") },
                supportingText = { Text("${request.length}/${GeneratedAvatarForms.MAX_REQUEST} · Describe colors and distinctive features.") },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Purple dragon", "Walking coffee cup", "Tiny spaceship").forEach { example ->
                    FilterChip(
                        selected = request.equals(example, ignoreCase = true),
                        enabled = !busy,
                        onClick = { request = example },
                        label = { Text(example) },
                    )
                }
            }
            Button(
                onClick = submit,
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth().testTag("generated-avatar-create"),
            ) { Text(if (busy) "Creating your form…" else "Create & become") }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("generated-avatar-progress"))
                Text("Your current avatar stays here while the new form is created.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancel, modifier = Modifier.testTag("generated-avatar-cancel")) { Text("Cancel creation") }
            }
            message?.takeIf { it.isNotBlank() }?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("generated-avatar-message")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                ) { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            Text(
                "You can also say “turn into…” in chat or by voice. New forms need your selected AI model; saved forms work offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (saved.isNotEmpty()) {
                Text("Your saved forms", style = MaterialTheme.typography.titleMedium)
                Text("Tap a form to wear it again. Pick an avatar below to return to a built-in look.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { form ->
                        val selected = current?.id == form.id
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).testTag("generated-avatar-select-${form.id}")
                                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(form.id) })
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(form.name, style = MaterialTheme.typography.titleSmall)
                                        if (selected) Text("Wearing now", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (selected) Icon(Icons.Default.Check, contentDescription = null)
                                }
                                IconButton(onClick = { onDelete(form.id) }, modifier = Modifier.testTag("generated-avatar-delete-${form.id}")) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${form.name} from saved forms")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
