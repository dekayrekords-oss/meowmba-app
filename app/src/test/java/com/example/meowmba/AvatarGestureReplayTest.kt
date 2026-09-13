package com.example.meowmba

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Host counterpart of the phone gesture acceptance test; runs while the phone is locked. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], qualifiers = "w412dp-h915dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class AvatarGestureReplayTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var storageMock: org.mockito.MockedConstruction<com.example.meowmba.core.security.SecureStorageManager>
    @org.junit.Before fun fakeStorageForHostOnly() {
        storageMock = org.mockito.Mockito.mockConstruction(com.example.meowmba.core.security.SecureStorageManager::class.java)
    }
    @org.junit.After fun closeStorageMock() { storageMock.close() }

    @Test fun allStylesInBothModesRespondWithoutSensorZonesAndKeepNavigation() {
        val state = mutableStateOf(CompanionState(isCameraEnabled = false, isMicEnabled = false))
        var pets = 0; var plays = 0; var zones = 0; var menus = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            AvatarRenderer(state.value, onZoneClick = { zones++ }, onPet = { pets++ },
                onPlay = { plays++ }, onSwipeDown = { menus++ }, paused = true,
                modifier = Modifier.testTag("avatar"))
        } }
        fun settle() { rule.mainClock.advanceTimeBy(700); rule.waitForIdle() }
        val characters = AvatarStyle.entries.map { it to AvatarAnimal.NONE } +
            AvatarAnimal.entries.filter { it != AvatarAnimal.NONE }.map { AvatarStyle.LUMEN to it }
        for ((style, animal) in characters) for (habitat in listOf(false, true)) {
            rule.runOnIdle { state.value = state.value.copy(avatarStyle = style, avatarAnimal = animal, habitatMode = habitat) }
            settle()
            val node = rule.onNodeWithTag("avatar")
            var before = pets
            node.performTouchInput { click(center) }; settle()
            assertTrue("$style/$animal habitat=$habitat attention", pets > before)
            before = pets
            node.performTouchInput { longClick(center, 650) }; settle()
            assertTrue("$style/$animal habitat=$habitat pet", pets > before)
            val playBefore = plays
            node.performTouchInput { doubleClick(center) }; settle()
            assertEquals("$style play", playBefore + 1, plays)
            before = pets
            node.performTouchInput { swipe(center, center + Offset(90f, 0f), 400) }; settle()
            assertEquals("one pet per stroke", before + 1, pets)
            val menuBefore = menus; before = pets
            node.performTouchInput { swipe(center - Offset(0f, 180f), center + Offset(0f, 180f), 400) }; settle()
            assertEquals("$style swipe menu", menuBefore + 1, menus)
            assertEquals("navigation must not teach pet feedback", before, pets)
            assertEquals("avatar gestures must not dispatch sensor settings", 0, zones)
        }
    }
}
