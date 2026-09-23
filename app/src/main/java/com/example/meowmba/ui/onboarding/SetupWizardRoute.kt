package com.example.meowmba.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import com.example.meowmba.ui.theme.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.example.meowmba.ui.theme.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.core.computer.TermuxComputerLinks
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.setup.GitHubProjectSetup
import com.example.meowmba.core.setup.SetupWiring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.example.meowmba.ui.companion.AvatarStyle
import com.example.meowmba.ui.companion.AvatarAnimal
import com.example.meowmba.ui.companion.autoAvatarScale
import androidx.compose.foundation.layout.BoxWithConstraints

/**
 * First-run and Settings → Start setup walkthrough.
 *
 * Shown by [com.example.meowmba.MainActivity] when
 * [SetupWiring.needsOnboarding] is true, or on demand from Settings.
 *
 * Feature tour, explicit avatar choice and automatic display fitting, followed
 * by optional Computer, permissions, providers, GitHub and verified pairing.
 * Completing setup is not a claim that an AI or external service is connected.
 */
private enum class FirstRunPage {
    Hi,
    DeviceCheck,
    Features,
    AvatarSize,
    GetTermux,
    CopyCommands,
    IDidThat,
    OpenCode,
    Mic,
    ApiKey,
    GitHub,
    Avatar,
    Background,
    OptionalServer,
    Done,
}

@Composable
fun SetupWizardRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onEnablePermissions: () -> Unit = {},
    onSkipPermissions: () -> Unit = {},
    onOpenChat: () -> Unit = onFinished,
    onOpenTerminal: () -> Unit = onFinished,
    onOpenConnectionsSetup: () -> Unit = onFinished,
    onOpenNeuralSetup: () -> Unit = onFinished,
    /** Legacy callback retained for callers; setup no longer auto-starts a sandbox shell. */
    onStartOpenCode: () -> Unit = {},
    onAvatarPicked: (AvatarStyle) -> Unit = {},
    onAnimalPicked: (AvatarAnimal) -> Unit = {},
    onAvatarSized: (Float) -> Unit = {},
    onBackgroundPicked: (BackgroundSelection) -> Unit = {},
    /** Persist GitHub connector token the same way Connections does. */
    onGitHubTokenSaved: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(FirstRunPage.Hi.name) }
    val current = remember(page) {
        runCatching { FirstRunPage.valueOf(page) }.getOrDefault(FirstRunPage.Hi)
    }
    var history by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val storage = remember { SecureStorageManager(context) }
    var githubToken by remember { mutableStateOf(storage.getApiKey(GitHubProjectSetup.STORAGE_KEY).orEmpty()) }
    var githubRepoName by rememberSaveable { mutableStateOf("meowmba-projects") }
    var githubLinkUrl by rememberSaveable { mutableStateOf("") }
    var githubStatus by rememberSaveable { mutableStateOf("") }
    var githubBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var avatarChoice by rememberSaveable { mutableStateOf(AvatarStyle.fromKey(storage.getPreference("avatar_style")).key) }
    var animalChoice by rememberSaveable { mutableStateOf(storage.getPreference("avatar_animal") ?: AvatarAnimal.NONE.key) }
    var avatarConfirmed by rememberSaveable { mutableStateOf(false) }
    var sizeConfirmed by rememberSaveable { mutableStateOf(false) }
    var manualSize by rememberSaveable { mutableStateOf<Float?>(null) }
    var adjustingSize by rememberSaveable { mutableStateOf(false) }
    val selectedStyle = AvatarStyle.fromKey(avatarChoice)
    val selectedAnimal = AvatarAnimal.entries.firstOrNull { it.key == animalChoice } ?: AvatarAnimal.NONE
    var backgroundChoice by rememberSaveable {
        mutableStateOf(storage.getPreference("background_value").orEmpty())
    }

    fun go(next: FirstRunPage) {
        if (page != next.name) {
            history = ArrayList(history + page)
            page = next.name
        }
    }

    fun finish() {
        // Even the quick path includes an explicit avatar choice and size review.
        go(when {
            !avatarConfirmed -> FirstRunPage.Avatar
            !sizeConfirmed -> FirstRunPage.AvatarSize
            else -> FirstRunPage.Done
        })
    }

    fun back() {
        history.lastOrNull()?.let { previous ->
            page = previous
            history = ArrayList(history.dropLast(1))
        }
    }

    BackHandler { back() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        val fittedSize = autoAvatarScale(screenWidth, screenHeight, selectedStyle, selectedAnimal)
        val shownSize = manualSize ?: fittedSize
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            if (current != FirstRunPage.Done) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { back() }, enabled = current != FirstRunPage.Hi) {
                        Text("Back")
                    }
                    if (current == FirstRunPage.Avatar || current == FirstRunPage.AvatarSize) {
                        Text("Avatar setup", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else TextButton(onClick = { finish() }) { Text("Finish later") }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (current) {
                    FirstRunPage.Hi -> KidPage(
                        title = "Meet your Meow.",
                        body = "Your companion, creative space and personal assistant. Let’s make it yours.",
                        primary = "Let’s go",
                        onPrimary = { go(FirstRunPage.DeviceCheck) },
                    )
                    FirstRunPage.DeviceCheck -> {
                        val profile = remember { com.example.meowmba.core.setup.SetupDeviceScan.profile(context) }
                        LaunchedEffect(Unit) {
                            com.example.meowmba.core.setup.SetupDeviceScan.applyDefaults(profile, storage)
                            avatarChoice = AvatarStyle.fromKey(storage.getPreference("avatar_style")).key
                            onAvatarPicked(AvatarStyle.fromKey(avatarChoice))
                        }
                        KidPage(
                            title = "Tuned for your phone",
                            body = "${android.os.Build.MODEL} · ${String.format(java.util.Locale.US, "%.1f", profile.ramGb)} GB RAM · ${profile.cores} processor cores\n\n${profile.summary}\n\nMeow checks the phone directly. No AI download or cloud upload is needed. Saved choices are kept; animation and Computer settings remain adjustable.",
                            primary = "Continue", onPrimary = { go(FirstRunPage.Features) },
                        )
                    }
                    FirstRunPage.Features -> SetupFeaturesPage { go(FirstRunPage.Avatar) }
                    FirstRunPage.AvatarSize -> AvatarFitPage(
                        style = selectedStyle, animal = selectedAnimal,
                        scale = shownSize, automaticScale = fittedSize,
                        screenWidth = screenWidth, screenHeight = screenHeight,
                        adjusting = adjustingSize, onAdjust = { adjustingSize = true },
                        onScale = { manualSize = it },
                        onAuto = { manualSize = null; adjustingSize = false },
                        onNext = {
                            storage.savePreference("avatar_scale", shownSize.toString())
                            onAvatarSized(shownSize)
                            sizeConfirmed = true
                            go(FirstRunPage.Background)
                        },
                    )
                    FirstRunPage.GetTermux -> {
                        val observed by com.example.meowmba.ui.companion.rememberTermuxSetupSupport()
                        var refreshed by remember { mutableStateOf<com.example.meowmba.core.computer.TermuxSetupSupport?>(null) }
                        LaunchedEffect(observed) { refreshed = null }
                        val support = refreshed ?: observed
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Set up your phone Computer", style = MaterialTheme.typography.headlineMedium)
                            val profile = remember { com.example.meowmba.core.setup.SetupDeviceScan.profile(context) }
                            if (!profile.recommendPhoneComputer) {
                                Text(profile.computerReason, style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { go(FirstRunPage.Mic) }, modifier = Modifier.fillMaxWidth()) { Text("Use lighter setup (recommended)") }
                                Text("You can still choose phone Computer below, or connect another computer later.", style = MaterialTheme.typography.bodySmall)
                            }
                            com.example.meowmba.ui.companion.TermuxInstallGuidance(support) {
                                refreshed = com.example.meowmba.core.computer.TermuxSetupSupport.detect(context)
                            }
                            Button(onClick = { go(FirstRunPage.OpenCode) }, enabled = support != com.example.meowmba.core.computer.TermuxSetupSupport.MISSING,
                                modifier = Modifier.fillMaxWidth()) { Text("Continue with installed Termux") }
                            TextButton(onClick = { go(FirstRunPage.Mic) }) { Text("Set up Computer later") }
                        }
                    }
                    // Old saved wizard positions resume into the same capability-aware connection flow.
                    FirstRunPage.CopyCommands, FirstRunPage.IDidThat, FirstRunPage.OpenCode -> {
                        var verified by remember { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Connect Ubuntu & OpenCode", style = MaterialTheme.typography.headlineMedium)
                            com.example.meowmba.ui.companion.OpenCodeLocalConnectionCard(onVerified = { verified = true })
                            TextButton(onClick = { go(FirstRunPage.Mic) }) { Text(if (verified) "Continue" else "Set up Computer later") }
                        }
                    }
                    FirstRunPage.Mic -> KidPage(
                        title = "Can we use the mic and camera?",
                        body = "Talking needs the mic. Face-following needs the camera. You can say no.",
                        primary = "Allow",
                        onPrimary = {
                            onEnablePermissions()
                            go(FirstRunPage.ApiKey)
                        },
                        secondary = "Not now",
                        onSecondary = { onSkipPermissions(); go(FirstRunPage.ApiKey) },
                    )
                    FirstRunPage.ApiKey -> SetupProvidersPage(
                        storage = storage,
                        onNext = { go(FirstRunPage.GitHub) },
                        onLater = { go(FirstRunPage.Done) },
                    )
                    FirstRunPage.GitHub -> GitHubPage(
                        token = githubToken,
                        onToken = { githubToken = it },
                        repoName = githubRepoName,
                        onRepoName = { githubRepoName = it },
                        linkUrl = githubLinkUrl,
                        onLinkUrl = { githubLinkUrl = it },
                        status = githubStatus,
                        busy = githubBusy,
                        onGetToken = { openUrl(context, GitHubProjectSetup.NEW_TOKEN_CLASSIC) },
                        onCreate = {
                            if (githubBusy) return@GitHubPage
                            githubBusy = true
                            githubStatus = "Making a private repo…"
                            val tok = githubToken
                            val name = githubRepoName
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (tok.isNotBlank()) GitHubProjectSetup.saveToken(storage, tok)
                                    GitHubProjectSetup.createPrivateRepo(storage, name, tok)
                                }
                                githubBusy = false
                                githubStatus = result.message
                                if (result.ok) {
                                    val saved = tok.trim().ifBlank {
                                        storage.getApiKey(GitHubProjectSetup.STORAGE_KEY).orEmpty()
                                    }
                                    if (saved.isNotBlank()) onGitHubTokenSaved(saved)
                                    go(FirstRunPage.OptionalServer)
                                }
                            }
                        },
                        onLink = {
                            if (githubToken.isNotBlank()) {
                                GitHubProjectSetup.saveToken(storage, githubToken)
                                onGitHubTokenSaved(githubToken.trim())
                            }
                            val result = GitHubProjectSetup.saveLinkedRepo(storage, githubLinkUrl)
                            githubStatus = result.message
                            if (result.ok) go(FirstRunPage.OptionalServer)
                        },
                        onSkip = {
                            GitHubProjectSetup.markSkipped(storage)
                            go(FirstRunPage.OptionalServer)
                        },
                    )
                    FirstRunPage.Avatar -> SetupAvatarPage(
                        style = selectedStyle, animal = selectedAnimal,
                        confirmed = avatarConfirmed,
                        onStyle = { style ->
                            avatarChoice = style.key
                            avatarConfirmed = true; sizeConfirmed = false; manualSize = null; adjustingSize = false
                            storage.savePreference("generated_avatar_id", "")
                            storage.savePreference("avatar_style", style.key)
                            onAvatarPicked(style)
                        },
                        onAnimal = { animal ->
                            avatarChoice = AvatarStyle.LUMEN.key
                            animalChoice = animal.key
                            avatarConfirmed = true; sizeConfirmed = false; manualSize = null; adjustingSize = false
                            storage.saveAvatarForm(animal.key)
                            onAnimalPicked(animal)
                        },
                        onNext = { go(FirstRunPage.AvatarSize) },
                    )
                    FirstRunPage.Background -> BackgroundPickPage(
                        selectedId = backgroundChoice,
                        onSelect = { anim ->
                            backgroundChoice = anim.id
                            val selection = BackgroundSelection(
                                kind = BackgroundKind.ANIMATION,
                                value = anim.id,
                            )
                            storage.savePreference("background_kind", selection.kind.name)
                            storage.savePreference("background_value", selection.value)
                            storage.savePreference("background_dim", selection.dimPercent.toString())
                            onBackgroundPicked(selection)
                        },
                        onNone = {
                            backgroundChoice = ""
                            val selection = BackgroundSelection(kind = BackgroundKind.NONE)
                            storage.savePreference("background_kind", selection.kind.name)
                            storage.savePreference("background_value", "")
                            onBackgroundPicked(selection)
                        },
                        onNext = { go(if (com.example.meowmba.core.setup.SetupDeviceScan.profile(context).recommendPhoneComputer)
                            FirstRunPage.GetTermux else FirstRunPage.Mic) },
                    )
                    FirstRunPage.OptionalServer -> KidPage(
                        title = "Connect a computer or neural server",
                        body = "Optional. Pair a desktop for remote tools, or connect a full fly-graph server for the avatar. The setup screen verifies the connection before it is used.",
                        primary = "Finish setup",
                        onPrimary = { go(FirstRunPage.Done) },
                        secondary = "Pair a computer",
                        onSecondary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenConnectionsSetup()
                        },
                        tertiary = "Pair a neural server",
                        onTertiary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenNeuralSetup()
                        },
                    )
                    FirstRunPage.Done -> KidPage(
                        title = "Make yourself at home.",
                        body = "Swipe down for the menu, right for chat, up for your apps. AI, Computer and connected services work after their setup and connection checks. You can continue setup in Settings anytime.",
                        primary = "Meet my avatar",
                        onPrimary = {
                            SetupWiring.markOnboardingDone(context)
                            onFinished()
                        },
                        secondary = "Chat",
                        onSecondary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenChat()
                        },
                        tertiary = "Terminal",
                        onTertiary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenTerminal()
                        },
                    )
                }
            }
        }
    }
    }
}



@Composable
private fun GitHubPage(
    token: String,
    onToken: (String) -> Unit,
    repoName: String,
    onRepoName: (String) -> Unit,
    linkUrl: String,
    onLinkUrl: (String) -> Unit,
    status: String,
    busy: Boolean,
    onGetToken: () -> Unit,
    onCreate: () -> Unit,
    onLink: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "GitHub for projects",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Optional. Make a private repo, or link one you already have. OpenCode and Computer can use it later. You can skip.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        KidPrimaryButton("Get a GitHub token", onGetToken)
        OutlinedTextField(
            value = token,
            onValueChange = onToken,
            label = { Text("Paste token here") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        Text(
            "Need repo + private boxes checked when GitHub makes the token.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = repoName,
            onValueChange = onRepoName,
            label = { Text("New private repo name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        KidPrimaryButton(
            if (busy) "Working…" else "Create private repo",
            onClick = onCreate,
        )
        Text("— or —", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = linkUrl,
            onValueChange = onLinkUrl,
            label = { Text("Link existing repo (owner/name or URL)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        OutlinedButton(
            onClick = onLink,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Link this repo", style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onSkip, enabled = !busy) { Text("Skip for now") }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BackgroundPickPage(
    selectedId: String,
    onSelect: (BackgroundAnimation) -> Unit,
    onNone: () -> Unit,
    onNext: () -> Unit,
) {
    // A short kid-friendly subset of the full BackgroundScreen catalog.
    val choices = listOf(
        BackgroundAnimation.AURORA,
        BackgroundAnimation.STARFIELD,
        BackgroundAnimation.NEBULA,
        BackgroundAnimation.WOODLAND,
        BackgroundAnimation.LAGOON,
        BackgroundAnimation.CLEAR_SKY,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Pick a background",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "What sits behind your helper. More choices live in Settings later.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onNone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(
                if (selectedId.isEmpty()) "✓ None" else "None",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        choices.forEach { anim ->
            val selected = anim.id == selectedId
            OutlinedButton(
                onClick = { onSelect(anim) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(
                    if (selected) "✓ ${anim.label}" else anim.label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        KidPrimaryButton("Next", onNext)
    }
}



@Composable
private fun KidPage(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        KidPrimaryButton(primary, onPrimary)
        if (secondary != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(secondary, style = MaterialTheme.typography.titleMedium) }
        }
        if (tertiary != null && onTertiary != null) {
            TextButton(onClick = onTertiary) { Text(tertiary) }
        }
    }
}

@Composable
private fun KidPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Launch Termux if installed. Returns true when a launch intent was fired. */
private fun openTermux(context: Context): Boolean {
    val launch = context.packageManager
        .getLaunchIntentForPackage(TermuxComputerLinks.TERMUX_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
        return true
    }
    val explicit = Intent(Intent.ACTION_MAIN).apply {
        setPackage(TermuxComputerLinks.TERMUX_PACKAGE)
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(explicit)
        true
    }.getOrDefault(false)
}

/** Leave setup for the home/app list so the user is never trapped. */
private fun openAppLauncher(context: Context) {
    val home = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(home) }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("setup", text))
}

private fun termuxInstalled(context: Context): Boolean {
    return runCatching {
        context.packageManager.getPackageInfo(TermuxComputerLinks.TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)
}
