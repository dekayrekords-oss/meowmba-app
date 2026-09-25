package com.example.meowmba

import com.example.meowmba.ui.companion.*
import com.example.meowmba.core.background.*
import org.junit.Assert.*
import org.junit.Test

class AvatarHomeNavigationTest {
    @Test fun onlyExplicitHomeNavigationResetsTheScreen() {
        assertTrue(isAvatarHomeIntent("android.intent.action.MAIN", setOf("android.intent.category.HOME")))
        assertFalse(isAvatarHomeIntent("android.intent.action.MAIN", setOf("android.intent.category.LAUNCHER")))
        assertFalse(isAvatarHomeIntent("android.intent.action.VIEW", setOf("android.intent.category.HOME")))
        assertFalse(isAvatarHomeIntent(null, null))
    }

    @Test fun homeClosesEveryPanelAndLeavesAppearanceAndPrivacyAlone() {
        val wallpaper = BackgroundSelection(BackgroundKind.IMAGE, "asset:///backgrounds/conservatory.png", 10)
        val before = CompanionState(
            isChatExpanded=true, isPreviewVisible=true, showTiles=true, showSettings=true,
            showModelManager=true, showPrivacyDashboard=true, showAvatarScreen=true,
            showAppDrawer=true, showBrowser=true, showMemoryScreen=true, showStudioScreen=true,
            showAssetBrowser=true, showWeatherScreen=true, showBackgroundScreen=true, showNavScreen=true,
            showAgentsScreen=true, showBusinessAgents=true, showProjectsScreen=true, showToolsScreen=true,
            showRobotScreen=true, showVoiceModelScreen=true, showVaultScreen=true, showConnectorsScreen=true,
            avatarStyle=AvatarStyle.FULL_FLY, avatarScale=1.5f, habitatMode=true, background=wallpaper,
            isCameraEnabled=false, isMicEnabled=false,
            features=CompanionFeatures(showCozmoScreen=true),
            showNeuralInfo=true, settingsInitialPage="Neural / Lumen", neuralInputNotice="Pet sent to the brain",
        )
        val after=before.atAvatarHome()
        // Include every show* panel getter so a future route cannot silently escape this contract.
        for (method in CompanionState::class.java.methods.filter { it.name.startsWith("getShow") && it.returnType == Boolean::class.javaPrimitiveType }) {
            assertEquals(method.name, false, method.invoke(after))
        }
        assertFalse(after.isChatExpanded); assertFalse(after.isPreviewVisible)
        assertFalse(after.features.showCozmoScreen)
        assertNull(after.settingsInitialPage); assertNull(after.neuralInputNotice)
        assertEquals(before.avatarStyle,after.avatarStyle);assertEquals(before.avatarScale,after.avatarScale,0f)
        assertEquals(before.background,after.background);assertEquals(before.habitatMode,after.habitatMode)
        assertEquals(before.isCameraEnabled,after.isCameraEnabled);assertEquals(before.isMicEnabled,after.isMicEnabled)
        assertEquals(after,after.atAvatarHome())
    }
}
