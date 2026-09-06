package com.example.meowmba

import android.content.Intent
import android.net.Uri
import com.example.meowmba.core.media.ArtifactViewer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class ArtifactViewerTest {
    @Test fun `video audio and web images use actual media types`() {
        assertEquals("video/webm", ArtifactViewer.mimeFor(File("clip.WEBM")))
        assertEquals("audio/mp4", ArtifactViewer.mimeFor(File("song.m4a")))
        assertEquals("image/webp", ArtifactViewer.mimeFor(File("picture.webp")))
        assertEquals("application/pdf", ArtifactViewer.mimeFor(File("document.pdf")))
    }

    @Test fun `external intent gives read access to the exact content uri`() {
        val uri = Uri.parse("content://com.meow.meowmba.fileprovider/workspace/movie.mp4")
        val intent = ArtifactViewer.viewIntent(uri, "video/mp4")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("video/mp4", intent.type)
        assertEquals(uri, intent.data)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNull(intent.`package`)
    }

    @Test fun `latest artifact includes nested studio folders and skips empty files`() {
        val root = kotlin.io.path.createTempDirectory("artifact-test").toFile()
        try {
            val nested = File(root, "comics/latest.html").apply { parentFile.mkdirs(); writeText("<h1>Comic</h1>") }
            File(root, "empty.mp4").createNewFile()
            assertEquals(nested, ArtifactViewer.latest(listOf(root)))
        } finally { root.deleteRecursively() }
    }

    @Test fun `missing artifact fails instead of announcing it opened`() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(ArtifactViewer.open(ctx, File(ctx.filesDir, "workspace/missing.mp4")).isFailure)
    }

    @Test fun `generated web pages are covered by file provider`() {
        val ctx = RuntimeEnvironment.getApplication()
        val file = File(ctx.filesDir, "web_pages/audit.html").apply { parentFile.mkdirs(); writeText("<h1>Ready</h1>") }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            assertEquals("content", uri.scheme)
            assertTrue(uri.toString().contains("web_pages"))
        } finally { file.delete() }
    }
}
