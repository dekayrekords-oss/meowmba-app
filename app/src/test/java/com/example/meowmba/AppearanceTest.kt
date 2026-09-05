package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun `all theme keys round trip`() {
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromKey(it.key)) }
        AppearanceMode.entries.forEach { assertEquals(it, AppearanceMode.fromKey(it.key)) }
        AvatarStyle.entries.forEach { assertEquals(it, AvatarStyle.fromKey(it.key)) }
    }

    @Test fun `old and unknown preferences preserve existing defaults`() {
        assertEquals(AppTheme.COMPANION, AppTheme.fromKey(null))
        assertEquals(AppTheme.COMPANION, AppTheme.fromKey("future"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromKey(null))
        assertEquals(AvatarStyle.NEO, AvatarStyle.fromKey("neo"))
        assertEquals(AvatarStyle.SIGNAL, AvatarStyle.fromKey("signal"))
    }

    @Test fun `explicit brightness overrides system setting`() {
        assertTrue(AppearanceMode.DARK.isDark(false))
        assertFalse(AppearanceMode.LIGHT.isDark(true))
        assertTrue(AppearanceMode.SYSTEM.isDark(true))
        assertFalse(AppearanceMode.SYSTEM.isDark(false))
    }

    @Test fun `cat colors round trip without losing alpha`() {
        AvatarColors.swatches.forEach {
            assertEquals(it.argb, AvatarColors.parse(AvatarColors.toPref(it.argb), 0L))
        }
    }

    @Test fun `professional theme does not change sensor permissions`() {
        val initial = CompanionState(isMicEnabled = false, isCameraEnabled = false, isMuted = true)
        val themed = initial.copy(appTheme = AppTheme.PROFESSIONAL, avatarStyle = AvatarStyle.PEARL)
        val pose = CatAvatarStateMachine.resolvePose(themed)
        assertTrue(pose.micOffMark)
        assertTrue(pose.cameraOffMark)
        assertEquals(MouthMode.MUTED, pose.mouth)
    }

    @Test fun `brightness does not reset theme avatar or colors`() {
        val initial = CompanionState(appTheme = AppTheme.PROFESSIONAL, avatarStyle = AvatarStyle.PEARL,
            avatarFeatureColor = 0xFFFFA6C9L, avatarEyeColor = 0xFF39D0D8L)
        val changed = initial.copy(appearanceMode = AppearanceMode.LIGHT)
        assertEquals(initial.appTheme, changed.appTheme)
        assertEquals(initial.avatarStyle, changed.avatarStyle)
        assertEquals(initial.avatarFeatureColor, changed.avatarFeatureColor)
        assertEquals(initial.avatarEyeColor, changed.avatarEyeColor)
    }
}
