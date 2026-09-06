package com.example.meowmba

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.meowmba.core.audio.AsrBackend
import com.example.meowmba.core.audio.AsrBackendStatus
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.VoiceModelScreen
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class VoiceRepairUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `missing Whisper shows a working repair action not an in use badge`() {
        var download = 0
        var repair = 0
        compose.setContent {
            MeowmbaTheme {
                VoiceModelScreen(
                    state = CompanionState(asrBackend = AsrBackend.WHISPER_LOCAL,
                        asrBackendStatuses = listOf(AsrBackendStatus(AsrBackend.WHISPER_LOCAL, false, "Needs streaming ASR and Whisper"))),
                    onDownloadWhisper = { download++ }, onRepairStreamingModel = { repair++ },
                    onDownloadLocalVoices = {}, onSelectLocalVoice = {}, onPreviewLocalVoice = {}, onUseSystemVoice = {}, onClose = {})
            }
        }
        compose.onNodeWithText("IN USE").assertDoesNotExist()
        compose.onNodeWithText("Download or repair Whisper").performScrollTo().performClick()
        compose.onNodeWithText("Repair live-listening model").performScrollTo().performClick()
        assertEquals(1, download)
        assertEquals(1, repair)
    }
}
