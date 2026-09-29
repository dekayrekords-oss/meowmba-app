package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.CheckRow

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection

@Composable
internal fun ReferencePhotoControls(state: CompanionState, viewModel: CompanionViewModel) {
    val context = LocalContext.current
    var consent by rememberSaveable { mutableStateOf(false) }
    val data = state.features
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.setCreationReference(uri.toString())
            consent = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !data.referencePhotoBusy) { Text("Add reference photo") }
        data.referencePhotoUri?.let { uri ->
            Box(Modifier.fillMaxWidth().height(130.dp)) {
                AppBackground(BackgroundSelection(BackgroundKind.IMAGE, uri, 0), null)
            }
            CheckRow(consent, { consent = it }, enabled = !data.referencePhotoBusy) { Text("Allow my configured vision provider to analyze this photo. If it is a cloud provider, the photo will be sent there.", style = MaterialTheme.typography.bodySmall) }
            Row {
                TextButton(onClick = viewModel::analyzeCreationReference, enabled = consent && !data.referencePhotoBusy) { Text("Analyze reference") }
                TextButton(onClick = { viewModel.setCreationReference(null) }) { Text("Remove") }
            }
            TextButton(onClick = {
                viewModel.setHabitatMode(false)
                viewModel.setBackground(BackgroundSelection(BackgroundKind.IMAGE, uri, 0))
            }) { Text("Use photo as background") }
            Text("Avatar and prop creation use the analyzed shapes and colors as a reference, not an exact 3D scan. A photo background stays a still image.", style = MaterialTheme.typography.bodySmall)
        }
        if (data.referencePhotoBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        data.referencePhotoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
