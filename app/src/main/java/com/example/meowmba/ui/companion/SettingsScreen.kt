package com.example.meowmba.ui.companion

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.meowmba.core.media.ImageService
import com.example.meowmba.core.media.ImageServiceSettings
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.ui.onboarding.ImageGenerationSetup
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.agent.OpenRouterModel
import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.launch

/**
 * Settings screen: companion voice (name, wake phrase, conversation mode)
 * and Model Provider configuration.
 */
@Composable
fun SettingsScreen(
    secureStorage: SecureStorageManager,
    companionName: String = "Meow",
    coverAvatarEnabled: Boolean = false,
    onCoverAvatarChange: (Boolean) -> Unit = {},
    wakeWordEnabled: Boolean = false,
    requireWakeWordInFace: Boolean = false,
    conversationMode: Boolean = false,
    bargeInEnabled: Boolean = true,
    routines: List<RoutineUi> = emptyList(),
    keepScreenOnInFaceMode: Boolean = false,
    watchModeEnabled: Boolean = false,
    commentaryEnabled: Boolean = false,
    roadAlertsEnabled: Boolean = false,
    roadAlertStatus: String? = null,
    idleScenesEnabled: Boolean = true,
    onIdleScenesChange: (Boolean) -> Unit = {},
    agentBrainEnabled: Boolean = true,
    hermesAgentUrl: String = "",
    imageServerUrl: String = "",
    mem0ServerUrl: String = "",
    preferServerImageGen: Boolean = false,
    videoServerUrl: String = "",
    imageModelInstalled: () -> Boolean = { false },
    imageModelDownload: com.example.meowmba.core.agent.ModelDownloader.DownloadProgress? = null,
    musicServerUrl: String = "",
    backgroundListeningEnabled: Boolean = false,
    usbAutoBridgeEnabled: Boolean = false,
    robotBodyEnabled: Boolean = false,
    robotHost: String = "",
    robotPort: String = "",
    robotStatus: String = "simulated (no body)",
    robotUseBluetooth: Boolean = false,
    robotBtAddress: String = "",
    robotScanning: Boolean = false,
    robotScanResults: List<Pair<String, String>> = emptyList(),
    carModeEnabled: Boolean = false,
    avatarStyle: AvatarStyle = AvatarStyle.DEFAULT,
    onAvatarStyleChange: (AvatarStyle) -> Unit = {},
    emergencyContact: String = "",
    onCarModeChange: (Boolean) -> Unit = {},
    onEmergencyContactChange: (String) -> Unit = {},
    onRobotScan: () -> Unit = {},
    onRobotBodyChange: (Boolean) -> Unit = {},
    onRobotHostChange: (String) -> Unit = {},
    onRobotPortChange: (String) -> Unit = {},
    onRobotUseBluetoothChange: (Boolean) -> Unit = {},
    onRobotBtAddressChange: (String) -> Unit = {},
    onCompanionNameChange: (String) -> Unit = {},
    onWakeWordChange: (Boolean) -> Unit = {},
    onRequireWakeInFaceChange: (Boolean) -> Unit = {},
    onConversationModeChange: (Boolean) -> Unit = {},

    onBargeInChange: (Boolean) -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onOpenVoiceModels: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    appTheme: AppTheme = AppTheme.COMPANION,
    appearanceMode: AppearanceMode = AppearanceMode.DARK,
    onAppThemeChange: (AppTheme) -> Unit = {},
    onAppearanceModeChange: (AppearanceMode) -> Unit = {},
    idleChatterEnabled: Boolean = true,
    onIdleChatterChange: (Boolean) -> Unit = {},

    onRoutineToggle: (Long, Boolean) -> Unit = { _, _ -> },
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onWatchModeChange: (Boolean) -> Unit = {},
    onCommentaryChange: (Boolean) -> Unit = {},
    onRoadAlertsChange: (Boolean) -> Unit = {},
    onAgentBrainChange: (Boolean) -> Unit = {},
    onHermesUrlChange: (String) -> Unit = {},
    onDownloadImageModel: () -> Unit = {},
    onDeleteImageModel: () -> Unit = {},
    onImageServerUrlChange: (String) -> Unit = {},
    onMem0ServerUrlChange: (String) -> Unit = {},
    onPreferServerImageGenChange: (Boolean) -> Unit = {},
    onMusicServerUrlChange: (String) -> Unit = {},
    onVideoServerUrlChange: (String) -> Unit = {},
    onBackgroundListeningChange: (Boolean) -> Unit = {},
    onUsbAutoBridgeChange: ((Boolean) -> Unit)? = null,
    lumenReflexV2: Boolean = false,
    onLumenReflexV2Change: (Boolean) -> Unit = {},
    agenticRuntimeV1: Boolean = false,
    onAgenticRuntimeV1Change: (Boolean) -> Unit = {},
    connectionProfiles: List<ConnectionProfile> = emptyList(),
    onSaveConnectionProfile: (ConnectionProfile) -> Unit = {},
    onDeleteConnectionProfile: (String) -> Unit = {},
        onSaveAgenticCredential: (String, String) -> Unit = { _, _ -> },
    agenticGoals: List<com.example.meowmba.core.agentic.AgenticGoal> = emptyList(),
    agenticModelConfig: com.example.meowmba.core.agentic.AgenticModelConfig.Snapshot =
        com.example.meowmba.core.agentic.AgenticModelConfig.Snapshot(
            baseUrl = "",
            apiKeyRef = "",
            modelName = com.example.meowmba.core.agentic.AgenticModelConfig.DEFAULT_MODEL,
            configured = false,
        ),
    sshCapabilitySummary: String = "not probed",
    onCreateAgenticGoal: (String) -> Unit = {},
    onTickAgenticGoal: (String) -> Unit = {},
    onTickOpenAgenticGoals: () -> Unit = {},
    onApproveAgenticGoal: (String) -> Unit = {},
    onRejectAgenticGoal: (String) -> Unit = {},
    onRefreshAgenticTasks: () -> Unit = {},
    onSaveAgenticModelConfig: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    lumenReflexGain: Float = 1f,
    onLumenReflexGainChange: (Float) -> Unit = {},
    onSaved: () -> Unit = {},
    onClose: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var opencodeKey by remember { mutableStateOf(secureStorage.getApiKey("opencode") ?: "") }
    var defaultBrain by remember { mutableStateOf(secureStorage.getPreference("default_brain") ?: "auto") }
    var brainMenuOpen by remember { mutableStateOf(false) }
    val sshRegistry = remember { com.example.meowmba.core.ssh.SshHostRegistry(secureStorage) }
    var sshName by remember { mutableStateOf("meow-nb") }
    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("") }
    var sshPassword by remember { mutableStateOf("") }
    var savedServers by remember { mutableStateOf(sshRegistry.list().map { it.name }) }
    var showKey by remember { mutableStateOf(false) }
    var opencodeEndpoint by remember { mutableStateOf(secureStorage.getEndpoint("opencode") ?: "") }
    var imageService by remember { mutableStateOf(ImageServiceSettings.selected(secureStorage)) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    val saveProviders: () -> Unit = {
        if (page == "AI & models") {
        secureStorage.saveApiKey("opencode", opencodeKey.trim())
        secureStorage.savePreference("default_brain", defaultBrain)
        secureStorage.saveEndpoint("opencode", opencodeEndpoint)
        onSaved()
        }
    }
    val back = {
        saveProviders()
        if (page != null) page = null else onClose()
    }
    BackHandler(onBack = back)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) {
                    Icon(Icons.Default.ArrowBack, contentDescription = if (page == null) "Close settings" else "Back to settings")
                }
                Text(page ?: "Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (page == null) {
            Text(
                text = "Everything in its place. Tap a category to open its options.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Finish setup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("AI, voice, images and connections.", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { saveProviders(); onOpenSetup() }) { Text("Start setup") }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            SETTINGS_CATEGORIES.forEach { category ->
                SettingsCategoryCard(category) { page = category.title }
                Spacer(Modifier.height(10.dp))
            }
            }
            if (page == "Neural / Lumen") {
                NeuralSettingsSection(
                    lumenReflexV2 = lumenReflexV2,
                    onLumenReflexV2Change = onLumenReflexV2Change,
                    agenticRuntimeV1 = agenticRuntimeV1,
                    onAgenticRuntimeV1Change = onAgenticRuntimeV1Change,
                    connectionProfiles = connectionProfiles,
                    onSaveConnectionProfile = onSaveConnectionProfile,
                    onDeleteConnectionProfile = onDeleteConnectionProfile,
                    onSaveAgenticCredential = onSaveAgenticCredential,
                    agenticGoals = agenticGoals,
                    agenticModelConfig = agenticModelConfig,
                    sshCapabilitySummary = sshCapabilitySummary,
                    onCreateAgenticGoal = onCreateAgenticGoal,
                    onTickAgenticGoal = onTickAgenticGoal,
                    onTickOpenAgenticGoals = onTickOpenAgenticGoals,
                    onApproveAgenticGoal = onApproveAgenticGoal,
                    onRejectAgenticGoal = onRejectAgenticGoal,
                    onRefreshAgenticTasks = onRefreshAgenticTasks,
                    onSaveAgenticModelConfig = onSaveAgenticModelConfig,
                    reflexGain = lumenReflexGain,
                    onReflexGainChange = onLumenReflexGainChange,
                )
            }
            if (page == "Help & feedback") BetaHelpCard()
            if (page == "Voice & behavior") {
            OutlinedButton(onClick = onOpenVoiceModels, modifier = Modifier.fillMaxWidth()) { Text("Voice recognition & voice models") }

            Spacer(modifier = Modifier.height(24.dp))

            // Companion Voice
            Text("Companion Voice", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = companionName,
                onValueChange = onCompanionNameChange,
                label = { Text("Companion name (wake phrase: \"Hey $companionName\")") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Also always answers to \"Hey Memba\" and \"Hey Meow\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            VoiceToggle(
                label = "Wake phrase",
                description = "Always-on listening for the wake phrase while the app is open. Runs on-device.",
                checked = wakeWordEnabled,
                onCheckedChange = onWakeWordChange
            )
            VoiceToggle(
                label = "Always-on mic in face mode",
                description = "Keeps the mic live while the companion face is up: just talk, " +
                    "no wake phrase needed. When Meow sees you, the mic stays on either way " +
                    "and listens a bit longer so mid-sentence pauses are not cut off.",
                checked = !requireWakeWordInFace,
                onCheckedChange = { onRequireWakeInFaceChange(!it) }
            )
            VoiceToggle(
                label = "Background wake phrase",
                description = "Keeps listening with the screen off via a persistent " +
                    "notification (foreground service). Uses more battery. Requires " +
                    "the wake phrase to be enabled.",
                checked = backgroundListeningEnabled,
                onCheckedChange = onBackgroundListeningChange
            )
            VoiceToggle(
                label = "Keep screen on in face mode",
                description = "Prevents the display from sleeping while the face is up. " +
                    "Uses a lot of battery — leave off unless this is a plugged-in desk pet.",
                checked = keepScreenOnInFaceMode,
                onCheckedChange = onKeepScreenOnChange
            )
            VoiceToggle(
                label = "Watch mode (screen awareness)",
                description = "Meow glances at your screen every few seconds so it can react and comment on what you're doing.",
                checked = watchModeEnabled,
                onCheckedChange = onWatchModeChange
            )

            VoiceToggle(
                label = "Commentary mode",
                description = "While watching, Meow may speak a short helpful comment now and then (max once every 5 minutes).",
                checked = commentaryEnabled,
                onCheckedChange = onCommentaryChange
            )
            VoiceToggle(
                label = "Road alerts",
                description = "Official AZ511 road work in Arizona, community reports elsewhere, and OpenStreetMap cameras. Coverage is incomplete; mobile police reports are not guaranteed. Requires location.",
                checked = roadAlertsEnabled,
                onCheckedChange = onRoadAlertsChange
            )
            if (roadAlertsEnabled) roadAlertStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            VoiceToggle(
                label = "Random idle scenes",
                description = "Mix chases, reading and occasional hats with quiet gestures while the avatar is resting.",
                checked = idleScenesEnabled,
                onCheckedChange = onIdleScenesChange,
            )

            VoiceToggle(
                label = "Conversation mode",
                description = "Stay for follow-up questions after responding. Say \"stop\" to end.",
                checked = conversationMode,
                onCheckedChange = onConversationModeChange
            )

            VoiceToggle(
                label = "Idle chatter",
                description = "Occasional unprompted one-liners while idle — at most once per hour.",
                checked = idleChatterEnabled,
                onCheckedChange = onIdleChatterChange
            )

            VoiceToggle(
                label = "Barge-in",
                description = "Keep listening for 8 seconds after every reply so you can cut in without saying the wake word again.",
                checked = bargeInEnabled,
                onCheckedChange = onBargeInChange
            )
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (page == "Phone & device") {

            VoiceToggle(
                label = "Cover-screen avatar",
                description = "Use your selected avatar in a compact layout when Meow is opened on a supported foldable cover display. Requires your phone's cover-app setup; does not replace its lock screen or launch automatically when folded.",
                checked = coverAvatarEnabled,
                onCheckedChange = onCoverAvatarChange,
            )
            FlipCoverSetupCard(showOnAllDevices = true)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "UI automation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Meow can find, tap, type and scroll on-screen when you ask. " +
                    "It uses the system accessibility service and stays OFF until you enable it. " +
                    "Phone tasks require approval; consequential actions keep their separate confirmation or manual handoff.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenAccessibility) {
                Text("Open accessibility settings")
            }

            PhoneRecipeSettings()

            // ── Home screen ────────────────────────────────────────────────
            val context = LocalContext.current
            val isDefaultHome = remember {
                val homeIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                val resolved = context.packageManager
                    .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                resolved?.activityInfo?.packageName == context.packageName
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Home screen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isDefaultHome) {
                    "MEOWMBA is your default home screen."
                } else {
                    "MEOWMBA can be your home screen so the cat is always there. " +
                        "Set it as the default home app; it is fully reversible from " +
                        "the same screen."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isDefaultHome) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                }) {
                    Text("Set as home screen")
                }
            }

            // Base controls appear only after a real body link exists. Setup
            // belongs in the guided wizard; an unattached phone should not be
            // confronted with hardware-specific controls.
            val physicalBaseAttached = com.example.meowmba.core.robot.RobotBaseControlPolicy.CONTROL_AVAILABLE &&
                robotStatus.contains("body", ignoreCase = true) &&
                !robotStatus.contains("simulated", ignoreCase = true) &&
                !robotStatus.contains("offline", ignoreCase = true)
            if (physicalBaseAttached) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Robot base",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "A physical robot base is attached. Current: " + robotStatus.replace("LOOI", "base", ignoreCase = true) + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable robot body", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(checked = robotBodyEnabled, onCheckedChange = onRobotBodyChange)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Connect over Bluetooth", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Uses the attached base over Bluetooth. Turn off to use Wi-Fi/TCP instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = robotUseBluetooth, onCheckedChange = onRobotUseBluetoothChange)
            }
            if (robotUseBluetooth) {
                OutlinedTextField(
                    value = robotBtAddress,
                    onValueChange = onRobotBtAddressChange,
                    label = { Text("Paired device MAC (e.g. 00:11:22:33:44:55)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onRobotScan, enabled = !robotScanning) {
                    Text(if (robotScanning) "Scanning for bases…" else "Find base (scan)")
                }
                if (robotScanResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap your robot base to use its address:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    robotScanResults.forEach { (name, address) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRobotBtAddressChange(address) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (address == robotBtAddress) {
                                Text("in use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else if (!robotScanning && robotScanResults.isEmpty()) {
                    // Shown only after a scan ran and saw nothing.
                }
            } else {
                OutlinedTextField(
                    value = robotHost,
                    onValueChange = onRobotHostChange,
                    label = { Text("Base host (IP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = robotPort,
                    onValueChange = onRobotPortChange,
                    label = { Text("Base port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            }

            if (routines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Routines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Scheduled moments when Meow speaks up on its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                routines.forEach { routine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                routine.whenLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = routine.enabled,
                            onCheckedChange = { onRoutineToggle(routine.id, it) }
                        )
                    }
                }
            }
            }
            if (page == "Images") {
            ImageGenerationSetup(secureStorage, onSelected = {
                imageService = it
                if (it == ImageService.CUSTOM) onImageServerUrlChange(secureStorage.getPreference("image_server_url").orEmpty())
            })
            if (imageService == ImageService.LOCAL || imageService == ImageService.LEGACY) {
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            // On-device image generation (stable-diffusion.cpp, native GGUF weights)
            Text("Image generation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            val imageOption = com.example.meowmba.core.agent.ImageModelCatalog.options.first()
            Text(imageOption.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(imageOption.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${imageOption.approxSize} · ${imageOption.minRamGb}GB+ RAM. Runs fully on-device; " +
                    "generation can take several minutes and needs substantial free memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            // imageModelInstalled() is a disk stat — check it once per composition and
            // whenever a download's state changes, not on every recomposition (this
            // composable body also re-runs on unrelated keystrokes elsewhere on this
            // screen, e.g. the companion name / Hermes URL fields).
            var imageModelInstalledNow by remember { mutableStateOf(imageModelInstalled()) }
            LaunchedEffect(imageModelDownload) { imageModelInstalledNow = imageModelInstalled() }
            when {
                imageModelInstalledNow -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Installed", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onDeleteImageModel()
                            imageModelInstalledNow = false
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
                imageModelDownload != null && !imageModelDownload.done && imageModelDownload.error == null -> {
                    if (imageModelDownload.fraction != null) {
                        LinearProgressIndicator(
                            progress = { imageModelDownload.fraction!! },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${(imageModelDownload.fraction!! * 100).toInt()}% · " +
                                "${imageModelDownload.received / 1_048_576} MB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "${imageModelDownload.received / 1_048_576} MB…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                }
                imageModelDownload?.error != null -> {
                    Text(imageModelDownload.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    TextButton(onClick = onDownloadImageModel) {
                        Text("Retry", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
                else -> {
                    OutlinedButton(onClick = onDownloadImageModel) {
                        Text("Download (${imageOption.approxSize})", fontSize = 12.sp)
                    }
                }
            }
            Text(
                text = "A downloaded model is required for local images. If generation fails, Meow reports the error; it does not substitute a drawing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            }
            if (imageService == ImageService.LEGACY) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsTextField(
                value = imageServerUrl,
                onValueChange = onImageServerUrlChange,
                label = { Text("Image server URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Your own image-generation server, e.g. " +
                    "http://192.168.1.10:9100. Leave blank to always use " +
                    "on-device generation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VoiceToggle(
                label = "Prefer server for images",
                description = "Try the server above before on-device generation. " +
                    "Falls back to on-device if the server is unreachable.",
                checked = preferServerImageGen,
                onCheckedChange = onPreferServerImageGenChange
            )
            }
            }
            if (page == "Music & video") {
            Spacer(modifier = Modifier.height(24.dp))

            // Music generation (opt-in remote server only — no on-device generator)
            Text("Music generation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = musicServerUrl,
                onValueChange = onMusicServerUrlChange,
                label = { Text("Music server URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Your own music-generation server, e.g. " +
                    "http://192.168.1.10:9200. A working server is required; leaving this blank disables music generation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Video generation (opt-in remote server — no on-device path exists)
            Text("Video generation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            SettingsTextField(
                value = videoServerUrl,
                onValueChange = onVideoServerUrlChange,
                label = { Text("Video server URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Your own video-generation server, e.g. " +
                    "http://192.168.1.10:9300. A working server is required. Generation can take several minutes per clip.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            }
            if (page == "Connections & memory") {
            // Semantic memory (meow-mem0): share on-device facts with Meow's brain.
            Text("Semantic memory", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = mem0ServerUrl,
                onValueChange = onMem0ServerUrlChange,
                label = { Text("Semantic memory server URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Meow's shared semantic memory service, e.g. " +
                    "http://192.168.1.69:11438. New conversation facts are synced " +
                    "here so the server-side brain can recall them. Leave blank to " +
                    "keep memory on-device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            }

            Spacer(modifier = Modifier.height(24.dp))

            if (page == "Appearance") {
            Text("Color mode", style = MaterialTheme.typography.titleMedium)
            AppearanceMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().clickable { onAppearanceModeChange(mode) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appearanceMode == mode, onClick = { onAppearanceModeChange(mode) })
                    Text(mode.name.lowercase().replaceFirstChar { it.titlecase() })
                }
            }
            Text("App theme", style = MaterialTheme.typography.titleMedium)
            AppTheme.entries.forEach { theme ->
                Row(Modifier.fillMaxWidth().clickable { onAppThemeChange(theme) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appTheme == theme, onClick = { onAppThemeChange(theme) })
                    Text(theme.name.lowercase().replaceFirstChar { it.titlecase() })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            // Avatar: which face the companion wears. The full picker (with previews
            // and the animation shelf) lives on the Avatar tile; this is the same
            // setting for anyone who looks for it here first.
            Text("Avatar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            AvatarStyle.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAvatarStyleChange(option) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == avatarStyle,
                        onClick = { onAvatarStyleChange(option) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(option.label, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            }
            if (page == "Driving") {
            // Car assistant: knows when you're driving, tracks trips, watches for crashes.
            Text("Car assistant", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            VoiceToggle(
                label = "Drive mode",
                description = "Meow senses when you're in a car (GPS speed, your car's " +
                    "Bluetooth, or a voice \"drive mode on\"), shows a speed/trip card, " +
                    "and can answer \"how fast\", \"are we there yet\", or find gas and " +
                    "food along the way. Uses GPS + accelerometer only while enabled.",
                checked = carModeEnabled,
                onCheckedChange = onCarModeChange
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsTextField(
                value = emergencyContact,
                onValueChange = onEmergencyContactChange,
                label = { Text("Emergency contact phone (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Used by the crash alert — say \"text mom\" after a detected " +
                    "impact and Meow will open a text with your location ready to send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            }
            if (page == "Connections & memory") {
            // Desktop Bridge (Phase 6): pair with a computer running meowmba-bridge.py
            DesktopBridgeSection(
                secureStorage,
                usbAutoBridgeEnabled = usbAutoBridgeEnabled,
                onUsbAutoBridgeChange = onUsbAutoBridgeChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            }
            if (page == "AI & models") {
            OutlinedButton(onClick = { saveProviders(); onOpenModels() }, modifier = Modifier.fillMaxWidth()) { Text("Open LLM settings") }
            Text("OpenRouter, OpenAI and vision settings are now in their cards on the LLM page.", style = MaterialTheme.typography.bodySmall)
            VoiceToggle(
                label = "Agent brain",
                description = "Understands natural tool commands (\"make a file called todo.txt\") " +
                    "using the active model. Select a local model on the LLM page for fully " +
                    "offline use — a local model file must be present.",
                checked = agentBrainEnabled,
                onCheckedChange = onAgentBrainChange
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = hermesAgentUrl,
                onValueChange = onHermesUrlChange,
                label = { Text("Hermes agent endpoint (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "OpenAI-compatible agent (hermes-agent v0.19+), e.g. http://192.168.1.10:8642. " +
                    "Leave blank for on-device routing only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))



            // BigPickle (OpenCode Zen) — free fallback brain, can be made the default.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BigPickle (OpenCode Zen)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                KeyStatusBadge(opencodeKey.isNotBlank(), "BigPickle")
            }
            BeginnerHelp("A free fallback brain from OpenCode Zen. Sign up at opencode.ai to get a key — no payment needed to start.")
            ApiKeyField(
                value = opencodeKey,
                onValueChange = { opencodeKey = it; secureStorage.saveApiKey("opencode", it.trim()) },
                label = "OpenCode Zen API key",
                show = showKey,
                onToggleShow = { showKey = !showKey },
            )
            CopyKeyButton(opencodeKey, "Copy BigPickle key")
            ApiKeyLink(
                url = "https://opencode.ai/zen",
                label = "Get a BigPickle / OpenCode Zen key",
            )
            ProviderEndpointField(
                value = opencodeEndpoint,
                onValueChange = { opencodeEndpoint = it },
                label = "OpenCode Zen base URL (optional)",
                helper = "Leave blank for https://opencode.ai/zen/v1.",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick = { brainMenuOpen = true }) {
                    Text("Default brain: " + brainLabel(defaultBrain))
                }
                DropdownMenu(expanded = brainMenuOpen, onDismissRequest = { brainMenuOpen = false }) {
                    BRAIN_CHOICES.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { defaultBrain = id; brainMenuOpen = false },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Local downloads, Hugging Face access and model files now live in LLM → Local.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpenModels, modifier = Modifier.fillMaxWidth()) { Text("Open LLM model settings") }
            }
            if (page == "Connections & memory") {
            // SSH servers meow may manage (encrypted, allowlisted commands only)
            Text("Servers (SSH)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (savedServers.isNotEmpty()) {
                Text("Saved: " + savedServers.joinToString(", "), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            SettingsTextField(
                value = sshName, onValueChange = { sshName = it },
                label = { Text("Server name (how you'll refer to it, e.g. meow-nb)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = sshHost, onValueChange = { sshHost = it },
                label = { Text("Host (IP or hostname)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = sshPort, onValueChange = { sshPort = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = sshUser, onValueChange = { sshUser = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = sshPassword, onValueChange = { sshPassword = it },
                label = { Text("Password (stored encrypted on this phone only)") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val port = sshPort.toIntOrNull() ?: 22
                    if (sshName.isNotBlank() && sshHost.isNotBlank() && sshUser.isNotBlank()) {
                        sshRegistry.save(
                            com.example.meowmba.core.ssh.SshHost(
                                name = sshName.trim(), host = sshHost.trim(),
                                port = port, user = sshUser.trim(), password = sshPassword,
                            )
                        )
                        savedServers = sshRegistry.list().map { it.name }
                        sshHost = ""; sshUser = ""; sshPassword = ""
                    }
                },
            ) { Text("Save server") }

            Spacer(modifier = Modifier.height(24.dp))

            }
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = {
                    back()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (page == null) "Close settings" else "Done")
            }
        }
        }
    }
}

@Composable
fun DesktopBridgeSection(
    secureStorage: SecureStorageManager,
    usbAutoBridgeEnabled: Boolean = false,
    onUsbAutoBridgeChange: ((Boolean) -> Unit)? = null
) {
    val bridgeManager = remember { DesktopBridgeManager(secureStorage) }
    var paired by remember { mutableStateOf(bridgeManager.pairedBridge()) }
    var payload by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text("Desktop Bridge", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(modifier = Modifier.height(4.dp))
    Text("Pair once with the desktop helper's code. If the network cannot connect, Meow tries the same trusted Mac through a configured USB tunnel. Keep the helper running; USB debugging and the Mac tunnel must be enabled.", style = MaterialTheme.typography.bodySmall)
    Text("Bridge 0.9 supports Logic workflows through its menus, track and region controls, plugin Controls view and export dialogs. Each edit has an approval and audit receipt. WAV checks include loudness and true peak when the Mac has libebur128. Enable Logic in the Mac helper and grant Accessibility. Custom controls and finished mixes still need review.", style = MaterialTheme.typography.bodySmall)

    val current = paired
    if (current != null) {
        Text("Paired: ${current.name} (${current.host}:${current.port})", color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "cert ${current.certSha256.take(16)}… · requests are HMAC-signed, cert pinned",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = bridgeManager.ping().fold(
                            onSuccess = { messageIsError = false; "Pong — desktop alive via ${bridgeManager.connectionDescription()} (ts $it)" },
                            onFailure = { messageIsError = true; it.message ?: "ping failed" }
                        )
                        busy = false
                    }
                }
            ) { Text(if (busy) "…" else "Ping") }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    bridgeManager.unpair()
                    paired = null
                    message = null
                }
            ) { Text("Unpair") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        BridgeWorkspaceBrowser(bridgeManager)
    } else {
        var discovered by remember {
            mutableStateOf<List<com.example.meowmba.core.bridge.DesktopBridgeProtocol.DiscoveredBridge>>(emptyList())
        }
        var scanning by remember { mutableStateOf(false) }
        var selected by remember {
            mutableStateOf<com.example.meowmba.core.bridge.DesktopBridgeProtocol.DiscoveredBridge?>(null)
        }
        var pairCode by remember { mutableStateOf("") }
        val discovery = remember { com.example.meowmba.core.bridge.BridgeDiscovery() }

        Text(
            text = "Run `meowmba-bridge.py --pair` on a computer on this network, then find it here and enter the code it shows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            enabled = !busy && !scanning,
            onClick = {
                scope.launch {
                    scanning = true
                    message = null
                    discovered = discovery.discover()
                    if (discovered.isEmpty()) {
                        messageIsError = true
                        message = "No desktops heard — is the daemon running? Is the phone on the same Wi-Fi?"
                    }
                    scanning = false
                }
            }
        ) { Text(if (scanning) "Listening…" else "Find desktops") }

        discovered.forEach { d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = d }
                    .padding(vertical = 6.dp)
            ) {
                RadioButton(selected = selected == d, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${d.name} · ${d.host}:${d.port}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Text(
                        "cert ${d.certSha256.take(12)}…" + (if (d.pairingOpen) " · pairing open" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.pairingOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        selected?.let { d ->
            Spacer(modifier = Modifier.height(4.dp))
            SettingsTextField(
                value = pairCode,
                onValueChange = { pairCode = it.uppercase().take(9) },
                label = { Text("Pairing code (e.g. ABCD-1234)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                enabled = !busy && pairCode.length >= 8,
                onClick = {
                    scope.launch {
                        busy = true
                        message = bridgeManager.pairWithCode(d, pairCode.trim(), Build.MODEL ?: "phone").fold(
                            onSuccess = {
                                messageIsError = false
                                paired = bridgeManager.pairedBridge()
                                discovered = emptyList()
                                selected = null
                                "Paired with $it"
                            },
                            onFailure = { messageIsError = true; it.message ?: "pairing failed" }
                        )
                        busy = false
                    }
                }
            ) { Text(if (busy) "Pairing…" else "Pair ${d.name}") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = usbAutoBridgeEnabled, onCheckedChange = {
                onUsbAutoBridgeChange?.invoke(it)
            })
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("USB bridge discovery", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Text(
                    text = if (usbAutoBridgeEnabled) "Checks the trusted desktop when USB reconnects; keeps pairing on disconnect" else "Discover over USB tethering; one-time code pairing is required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Or paste the full payload manually:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        SettingsTextField(
            value = payload,
            onValueChange = { payload = it },
            label = { Text("Pairing payload") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            enabled = !busy && payload.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    message = bridgeManager.pair(payload.trim(), Build.MODEL ?: "phone").fold(
                        onSuccess = { messageIsError = false; paired = bridgeManager.pairedBridge(); "Paired with $it" },
                        onFailure = { messageIsError = true; it.message ?: "pairing failed" }
                    )
                    busy = false
                }
            }
        ) { Text(if (busy) "Pairing…" else "Pair with payload") }
    }

    message?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Read-only browser for the paired desktop's workspace roots (P6b).
 * Directories descend; files open a size-capped utf-8 preview. Every row the
 * daemon served is shown — escapes and sensitive names never reach the list.
 */
@Composable
fun BridgeWorkspaceBrowser(bridgeManager: DesktopBridgeManager) {
    var active by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var roots by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<DesktopBridgeManager.BridgeFileEntry>>(emptyList()) }
    var listTruncated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<DesktopBridgeManager.BridgeFileContent?>(null) }
    val scope = rememberCoroutineScope()

    fun load(path: String) {
        scope.launch {
            busy = true
            error = null
            bridgeManager.listFiles(path).fold(
                onSuccess = {
                    currentPath = it.path
                    entries = it.entries
                    listTruncated = it.truncated
                },
                onFailure = { error = it.message ?: "list failed" }
            )
            busy = false
        }
    }

    TextButton(
        enabled = !busy,
        onClick = {
            if (!active) {
                active = true
                scope.launch {
                    busy = true
                    roots = bridgeManager.status().getOrNull()?.workspaceRoots ?: emptyList()
                    busy = false
                    load("")
                }
            } else {
                active = false
                preview = null
                error = null
            }
        }
    ) {
        Text(if (active) "Hide workspace browser" else "Browse workspace (read-only)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    }

    if (active) {
        if (roots.isEmpty()) {
            Text(
                "No workspace roots declared on the desktop — add them to ~/.meowmba-bridge/config.json",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD29922)
            )
        }
        Text(currentPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))

        val floor = roots.firstOrNull()
        if (floor != null && currentPath != floor) {
            TextButton(onClick = { preview = null; load(currentPath.substringBeforeLast('/', "/")) }) {
                Text(".. (up)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) {
                        preview = null
                        if (entry.dir) {
                            load("$currentPath/${entry.name}")
                        } else {
                            scope.launch {
                                busy = true
                                error = null
                                bridgeManager.readFile("$currentPath/${entry.name}", maxBytes = 8192).fold(
                                    onSuccess = { preview = it },
                                    onFailure = { error = it.message ?: "read failed" }
                                )
                                busy = false
                            }
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (entry.dir) "[dir] ${entry.name}" else "${entry.name} · ${entry.size / 1024} KB",
                    color = if (entry.dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = if (entry.dir) FontFamily.Default else FontFamily.Monospace
                )
            }
        }
        if (listTruncated) {
            Text("…listing capped by the desktop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        preview?.let { file ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            file.path.substringAfterLast('/'),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { preview = null }) { Text("Close", fontSize = 11.sp) }
                    }
                    Text(
                        file.content.take(4000),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (file.truncated || file.content.length > 4000) {
                        Text(
                            "preview capped — full file is ${file.size / 1024} KB on the desktop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenRouterModelPicker(
    label: String,
    selectedId: String,
    models: List<OpenRouterModel>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = models.firstOrNull { it.id == selectedId }
    val display = selected?.let { "${it.name}${if (it.free) " · free" else ""}" } ?: selectedId
    val shown = remember(models, query) {
        val q = query.trim()
        val base = if (q.isEmpty()) models else models.filter {
            it.id.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true)
        }
        base.take(80)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextField(
            value = if (expanded) query else display,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            placeholder = { Text("Type to search", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded && (shown.isNotEmpty() || query.isNotBlank()),
            onDismissRequest = { expanded = false; query = "" },
        ) {
            if (query.isNotBlank() && models.none { it.id == query.trim() }) {
                DropdownMenuItem(text = { Text("Use model ID: ${query.trim()}") }, onClick = {
                    onSelect(query.trim()); query = ""; expanded = false
                })
            }
            shown.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                model.id + if (model.free) " · free" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    onClick = {
                        onSelect(model.id)
                        query = ""
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun KeyStatusBadge(configured: Boolean, providerName: String) {
    Surface(
        color = if (configured) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Text(
            text = if (configured) "\u2713 Connected" else "Not set",
            color = if (configured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CopyKeyButton(key: String, label: String = "Copy") {
    val clipboard = LocalClipboardManager.current
    var justCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(justCopied) {
        if (justCopied) {
            kotlinx.coroutines.delay(1500)
            justCopied = false
        }
    }
    OutlinedButton(
        onClick = {
            if (key.isNotBlank()) {
                clipboard.setText(AnnotatedString(key))
                justCopied = true
            }
        },
    ) {
        Text(if (justCopied) "Copied!" else label, fontSize = 12.sp)
    }
}

@Composable
private fun BeginnerHelp(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun PlainStep(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun ApiKeyLink(url: String, label: String) {
    val context = LocalContext.current
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
    )
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * API-key text field that hides the secret behind dots until the user taps the
 * eye toggle. Mask visually, never replace the editable value with bullets:
 * doing that corrupts existing keys when the user edits a masked field.
 */
@Composable
internal fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    show: Boolean,
    onToggleShow: () -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    imageVector = if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (show) "Hide key" else "Show key",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Secondary endpoint/base-URL field with a gray helper line underneath. */
@Composable
internal fun ProviderEndpointField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helper: String,
) {
    SettingsTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = helper,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}


/** Route-id → display label for the "Default brain" selector. */
private fun brainLabel(id: String): String = BRAIN_CHOICES.firstOrNull { it.first == id }?.second ?: "Auto"

private data class SettingsCategory(val title: String, val summary: String, val icon: ImageVector)
private val SETTINGS_CATEGORIES = listOf(
    SettingsCategory("Voice & behavior", "Wake phrase, conversation and listening", Icons.Default.Mic),
    SettingsCategory("AI & models", "LLM settings, agent routing and downloads", Icons.Default.Psychology),
    SettingsCategory("Images", "AI Horde, Cloudflare and on-phone images", Icons.Default.Image),
    SettingsCategory("Music & video", "Media-generation services", Icons.Default.Movie),
    SettingsCategory("Phone & device", "App control, access and device behavior", Icons.Default.PhoneAndroid),
    SettingsCategory("Appearance", "Dark/light mode, themes and avatars", Icons.Default.Palette),
    SettingsCategory("Neural / Lumen", "Lumen reflex and nervous-system toggles", Icons.Default.Bolt),
    SettingsCategory("Connections & memory", "Desktop bridge, servers and shared memory", Icons.Default.Link),
    SettingsCategory("Driving", "Drive mode and emergency contact", Icons.Default.DirectionsCar),
    SettingsCategory("Help & feedback", "Beta help, testing and support", Icons.Default.HelpOutline),
)

/** Category cards navigate to a focused page, never expand a long form in the overview. */
@Composable
private fun SettingsCategoryCard(category: SettingsCategory, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(category.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val BRAIN_CHOICES = listOf(
    "auto" to "Auto (Omniroute)",
    "bigpickle" to "OpenCode Zen",
    "openrouter" to "OpenRouter",
    "openai" to "OpenAI",
    "anthropic" to "Claude / Anthropic",
    "custom" to "Custom API",
    "local" to "On-device",
)
