package com.example.meowmba

import android.app.Activity
import com.example.meowmba.core.agent.tools.GlassesTakePhotoTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.glasses.MetaGlassesController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Run with -PincludeMwdat=false: missing native support must never fake hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MetaGlassesUnavailableTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var controller: MetaGlassesController

    @Before fun setUp() {
        assumeFalse(BuildConfig.HAS_MWDAT_SDK)
        controller = MetaGlassesController(RuntimeEnvironment.getApplication())
        controller.disconnect()
    }

    @After fun tearDown() {
        if (::controller.isInitialized) controller.disconnect()
    }

    @Test fun `connect without SDK does not claim a link`() {
        assertFalse(controller.hasSdk())
        controller.connect()
        assertFalse(controller.isConnected.value)
        assertFalse(controller.isRealDevice.value)
        assertFalse(controller.isStreaming.value)
        assertTrue(controller.status.value.contains("Update"))
    }

    @Test fun `capture without SDK never starts mock frames`() {
        controller.ensureCaptureStream()
        assertNull(controller.latestJpeg.value)
        assertEquals(0L, controller.frameSeq.value)
        assertFalse(controller.isConnected.value)
        assertFalse(controller.isStreaming.value)
    }

    @Test fun `disconnect invalidates the old camera frame`() {
        controller.pushFrameJpeg(byteArrayOf(1, 2, 3))
        assertNotNull(controller.latestJpeg.value)
        controller.disconnect()
        assertNull(controller.latestJpeg.value)
        assertEquals(0L, MetaGlassesController.lastFrameAtMs.value)
        assertFalse(controller.isRealDevice.value)
    }

    @Test fun `photo cannot pass off a simulated frame as real glasses`() = runBlocking {
        controller.pushFrameJpeg(byteArrayOf(1, 2, 3))
        val workspace = Workspace(temporaryFolder.newFolder())
        val result = GlassesTakePhotoTool(controller, workspace).execute(emptyMap())
        assertTrue(result is ToolResult.Failure)
        assertTrue(workspace.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `registration without SDK fails safely`() = runBlocking {
        assertFalse(controller.ensureRegistered(Activity()))
        assertFalse(controller.isConnected.value)
    }
}
