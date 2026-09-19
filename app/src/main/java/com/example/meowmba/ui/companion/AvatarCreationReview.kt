package com.example.meowmba.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val review = state.features.avatarCreationReview ?: return
    var refinement by rememberSaveable(review.candidate?.id) { mutableStateOf("") }
    val painter = remember { GeneratedAvatarPainter() }
    AlertDialog(
        onDismissRequest = viewModel::dismissAvatarReview,
        title = { Text(if (!review.reviewing) "Do you want an example?" else "Your avatar example") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(review.request.take(240))
                if (!review.reviewing) Text("Preview and refine the design before wearing it, or skip the example and create it now.")
                review.candidate?.let { form ->
                    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                        val placement = generatedAvatarPlacement(size.width, size.height, LumenFrame(), scale = 1.2f)
                        painter.draw(this, form, LumenFrame(), 0f, placement.center, placement.unit)
                    }
                    Text("Actual native cartoon preview from your selected model. Accepting uses this same design, with live movement—not a photorealistic 3D reconstruction.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(refinement, { refinement = it.take(500) }, label = { Text("Refine the example") }, enabled = !state.avatarFormGenerating)
                    TextButton(onClick = { viewModel.refineAvatarExample(refinement) }, enabled = refinement.isNotBlank() && !state.avatarFormGenerating) { Text("Update example") }
                }
                if (state.avatarFormGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (review.reviewing) state.avatarFormMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.avatarFormGenerating && (!review.reviewing || review.candidate != null),
                onClick = { if (review.reviewing) viewModel.acceptAvatarExample() else viewModel.chooseAvatarExample(true) }) {
                Text(if (review.reviewing) "Accept & become" else "Yes, show example")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (review.reviewing) viewModel.dismissAvatarReview() else viewModel.chooseAvatarExample(false) }) {
                Text(if (review.reviewing) "Cancel" else "No, just make it")
            }
        },
    )
}
