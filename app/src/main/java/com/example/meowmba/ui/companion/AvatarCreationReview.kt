package com.example.meowmba.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A bounded native proof, not an aspirational image made by a different renderer. */
data class AvatarCreationReview(
    val request: String,
    val reason: InventReasonLevel,
    val candidate: GeneratedAvatarForm? = null,
    val reviewing: Boolean = false,
    val referenceDescription: String? = null,
)

@Composable
internal fun AvatarCreationReviewDialog(state: CompanionState, viewModel: CompanionViewModel) {
    AvatarCreationReviewDialog(state, viewModel::chooseAvatarExample, viewModel::refineAvatarExample,
        viewModel::acceptAvatarExample, viewModel::dismissAvatarReview)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AvatarCreationReviewDialog(state: CompanionState, onChooseExample: (Boolean) -> Unit,
    onRefine: (String) -> Unit, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val review = state.features.avatarCreationReview ?: return
    val canRetry = review.reviewing && review.candidate == null && state.avatarFormError && !state.avatarFormGenerating
    var refinement by rememberSaveable(review.candidate?.id) { mutableStateOf("") }
    val painter = remember { GeneratedAvatarPainter() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Bound the preview and scroll area from the window, not from the content's
        // wrap-content height. Title/actions stay outside the scrolling body so
        // they remain available in landscape and while the keyboard is visible.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)
                .testTag("avatar-example-panel").semantics { paneTitle = "Avatar creation review" },
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (!review.reviewing) "Do you want an example?" else "Your avatar example",
                        style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .testTag("avatar-example-body"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(review.request.take(240))
                        if (!review.reviewing) Text("Preview and refine the design before wearing it, or skip the example and create it now.")
                        review.candidate?.let { form ->
                            Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("avatar-example-preview")
                                .semantics { contentDescription = "Avatar example: ${form.name}" }) {
                                val placement = generatedAvatarPlacement(size.width, size.height, LumenFrame(), scale = 1.2f)
                                painter.draw(this, form, LumenFrame(), 0f, placement.center, placement.unit)
                            }
                            Text("Actual native cartoon preview from your selected model. Accepting uses this same design, with live movement—not a photorealistic 3D reconstruction.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(refinement, { refinement = it.take(500) }, label = { Text("Refine the example") },
                                enabled = !state.avatarFormGenerating,
                                modifier = Modifier.fillMaxWidth().testTag("avatar-example-refinement"))
                            TextButton(onClick = { onRefine(refinement) }, enabled = refinement.isNotBlank() && !state.avatarFormGenerating) { Text("Update example") }
                        }
                        if (state.avatarFormGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (review.reviewing || state.avatarFormError) {
                            val message = state.avatarFormMessage?.takeIf { it.isNotBlank() }
                                ?: if (state.avatarFormError) "Could not create the example. Try again; your current avatar is unchanged." else null
                            message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                                color = if (state.avatarFormError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("avatar-example-message")) }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { if (review.reviewing) onDismiss() else onChooseExample(false) }) {
                            Text(if (review.reviewing) "Cancel" else "No, just make it")
                        }
                        TextButton(enabled = !state.avatarFormGenerating && (!review.reviewing || review.candidate != null || canRetry),
                            onClick = { if (canRetry || !review.reviewing) onChooseExample(true) else onAccept() },
                            modifier = Modifier.testTag("avatar-example-confirm")) {
                            Text(when { canRetry -> "Retry example"; review.reviewing -> "Accept & become"; else -> "Yes, show example" })
                        }
                    }
                }
            }
        }
    }
}
