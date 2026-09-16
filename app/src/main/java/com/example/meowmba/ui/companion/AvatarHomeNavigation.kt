package com.example.meowmba.ui.companion

/** Only the launcher Home action resets navigation. Notifications and resume keep their destination. */
internal fun isAvatarHomeIntent(action: String?, categories: Set<String>?): Boolean =
    action == "android.intent.action.MAIN" && categories?.contains("android.intent.category.HOME") == true

/** Navigation reset only: drafts, ongoing work, appearance and privacy preferences survive. */
internal fun CompanionState.atAvatarHome() = copy(
    isChatExpanded = false, isPreviewVisible = false,
    showTiles = false, showSettings = false, showModelManager = false,
    showPrivacyDashboard = false, showMemoryScreen = false, showStudioScreen = false,
    showAssetBrowser = false, showWeatherScreen = false, showBackgroundScreen = false,
    showNavScreen = false, showAgentsScreen = false, showBusinessAgents = false,
    showProjectsScreen = false, showToolsScreen = false, showRobotScreen = false,
    showConnectorsScreen = false, showVaultScreen = false, showBrowser = false,
    showAvatarScreen = false, showAppDrawer = false, showVoiceModelScreen = false,
    showNeuralInfo = false, settingsInitialPage = null, neuralInputNotice = null,
)
