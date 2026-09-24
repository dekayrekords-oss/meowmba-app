package com.example.meowmba.ui.flow

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.meowmba.core.attach.Attachments
import com.example.meowmba.core.flow.FlowThemes
import com.example.meowmba.ui.companion.CompanionViewModel

/**
 * The + button (Flow, Desk, chat): take a photo, choose a photo, or choose any file.
 * Whatever is picked is read on the phone and handed to Meow with the user's note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachSheet(vm: CompanionViewModel, onDismiss: () -> Unit, note: String? = null) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    fun done(uri: Uri?, kind: Attachments.Kind) { onDismiss(); uri?.let { vm.onAttachment(it, kind, note) } }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        done(cameraUri.takeIf { ok }, Attachments.Kind.PHOTO)
    }
    fun startCamera() {
        val file = Attachments.newCameraFile(context)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        cameraUri = uri
        takePhoto.launch(uri)
    }
    // The app declares CAMERA, so Android requires it granted before another app's camera can be used.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else onDismiss()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { done(it, Attachments.Kind.IMAGE) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { done(it, Attachments.Kind.FILE) }

    val c = FlowColors.from(FlowThemes.palette(FlowThemes.DEFAULT_KEY, false))
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null, containerColor = c.paper, shape = RoundedCornerShape(0.dp)) {
        Column(Modifier.fillMaxWidth().background(c.paper).padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 30.dp).testTag("attach_sheet")) {
            BasicText("Show Meow something", style = FlowType.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, color = c.ink))
            BasicText("A receipt, a label, a document. Meow reads it on this phone.", Modifier.padding(top = 6.dp),
                style = FlowType.because.copy(color = c.ink2))
            @Composable fun Row(label: String, tag: String, onClick: () -> Unit) = BasicText(label,
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp).testTag(tag),
                style = FlowType.itemTitle.copy(color = c.ink))
            Spacer(Modifier.height(12.dp))
            Row("Take a photo", "attach_camera") {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            Row("Choose a photo", "attach_photo") { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Row("Choose a file or document", "attach_file") { pickFile.launch(arrayOf("*/*")) }
            Spacer(Modifier.height(8.dp))
            FlowLink("Cancel", c.ink2, onDismiss)
        }
    }
}

/** Pasting a picture or file into a text box hands it to Meow like the + button; pasted text stays text. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.acceptPastedFiles(onUri: (Uri) -> Unit): Modifier = contentReceiver { content ->
    if (!content.hasMediaType(MediaType.Image) && content.clipEntry.clipData.let { cd -> (0 until cd.itemCount).none { cd.getItemAt(it).uri != null } }) content
    else content.consume { item -> item.uri?.also(onUri) != null }
}

/** Public entry for other packages (the chat overlay). */
object PasteFiles {
    fun Modifier.accept(onUri: (Uri) -> Unit): Modifier = acceptPastedFiles(onUri)
}
