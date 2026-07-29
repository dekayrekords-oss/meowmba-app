package com.example.meowmba.ui.companion

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meowmba.core.vision.VisionManager

@Composable
fun Viewfinder(
    visionManager: VisionManager?,
    modifier: Modifier = Modifier
) {
    val mgr = visionManager ?: return

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                mgr.setPreviewSurface(view.surfaceProvider)
            }
        },
        onRelease = {
            mgr.setPreviewSurface(null)
        },
        modifier = modifier
    )
}
