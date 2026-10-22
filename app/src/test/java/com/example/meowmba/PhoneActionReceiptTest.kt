package com.example.meowmba

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.agent.tools.NavigateTool
import com.example.meowmba.core.agent.tools.PlayMusicTool
import com.example.meowmba.core.agent.tools.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneActionReceiptTest {
    private class Phone(val accept: (Intent) -> Boolean = { true }) : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val launches = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            if (!accept(intent)) throw ActivityNotFoundException()
            launches += intent
        }
    }
    private val destination = mapOf("lat" to "33.4", "lon" to "-112.0", "name" to "Test destination")

    @Test fun mapsLaunchIsNotProofOfActiveGuidance() = runTest {
        val phone = Phone()
        val result = NavigateTool(phone).execute(destination) as ToolResult.Success
        assertEquals("google.navigation", phone.launches.single().data?.scheme)
        assertTrue(result.output.contains("confirm turn-by-turn"))
        assertFalse(result.output.startsWith("Navigating"))
    }
    @Test fun mapPinFallbackDoesNotClaimGuidanceStarted() = runTest {
        val phone = Phone { it.data?.scheme == "geo" }
        val result = NavigateTool(phone).execute(destination) as ToolResult.Success
        assertEquals("geo", phone.launches.single().data?.scheme)
        assertTrue(result.output.contains("start or confirm"))
    }
    @Test fun noMapsHandlerIsAnHonestFailure() = runTest {
        val phone = Phone { false }
        assertTrue(NavigateTool(phone).execute(destination) is ToolResult.Failure)
        assertTrue(phone.launches.isEmpty())
    }
    @Test fun musicIntentIsRealButDoesNotClaimPlaybackWithoutEvidence() = runTest {
        val phone = Phone()
        val result = PlayMusicTool(phone).execute(mapOf("query" to "jazz")) as ToolResult.Success
        assertEquals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, phone.launches.single().action)
        assertTrue(result.output.contains("isn't confirmed"))
        assertFalse(result.output.startsWith("Playing"))
    }
    @Test fun noMusicHandlerCannotClaimSuccess() = runTest {
        assertTrue(PlayMusicTool(Phone { false }).execute(mapOf("query" to "jazz")) is ToolResult.Failure)
    }
}
