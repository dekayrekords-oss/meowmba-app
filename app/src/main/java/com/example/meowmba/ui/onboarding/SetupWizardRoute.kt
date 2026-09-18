package com.example.meowmba.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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

/**
 * First-run and Settings → Start setup walkthrough.
 *
 * Shown by [com.example.meowmba.MainActivity] when
 * [SetupWiring.needsOnboarding] is true, or on demand from Settings.
 *
 * One idea per screen. Large type. Kid-friendly words. Computer (phone) first,
 * then OpenCode in the in-app Terminal, then an AI key (with a clear stop),
 * then optional avatar + background. Neural / AgentBrain / connectors stay hidden.
 */
private enum class FirstRunPage {
    Hi,
    GetTermux,
    CopyCommands,
    IDidThat,
    OpenCode,
    Mic,
    OpenRouter,
    ApiKey,
    GitHub,
    Avatar,
    Background,
    OptionalServer,
    ServerFields,
    Done,
}

@Composable
fun SetupWizardRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onEnablePermissions: () -> Unit = {},
    onOpenChat: () -> Unit = onFinished,
    onOpenTerminal: () -> Unit = onFinished,
    /** Connect Computer PTY and auto-install/launch OpenCode (may peek Terminal). */
    onStartOpenCode: () -> Unit = {},
    onAvatarPicked: (AvatarStyle) -> Unit = {},
    onBackgroundPicked: (BackgroundSelection) -> Unit = {},
    /** Persist GitHub connector token the same way Connections does. */
    onGitHubTokenSaved: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(FirstRunPage.Hi.name) }
    val current = remember(page) {
        runCatching { FirstRunPage.valueOf(page) }.getOrDefault(FirstRunPage.Hi)
    }
    var copied by rememberSaveable { mutableStateOf(false) }
    var termuxOk by remember { mutableStateOf(termuxInstalled(context)) }
    var openCodeStarted by rememberSaveable { mutableStateOf(false) }
    val storage = remember { SecureStorageManager(context) }
    var host by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(storage.getApiKey("opencode").orEmpty()) }
    var githubToken by remember { mutableStateOf(storage.getApiKey(GitHubProjectSetup.STORAGE_KEY).orEmpty()) }
    var githubRepoName by rememberSaveable { mutableStateOf("meowmba-projects") }
    var githubLinkUrl by rememberSaveable { mutableStateOf("") }
    var githubStatus by rememberSaveable { mutableStateOf("") }
    var githubBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var avatarChoice by rememberSaveable { mutableStateOf(AvatarStyle.DEFAULT.key) }
    var backgroundChoice by rememberSaveable {
        mutableStateOf(BackgroundAnimation.AURORA.id)
    }

    fun go(next: FirstRunPage) {
        page = next.name
    }

    fun finish() {
        SetupWiring.markOnboardingDone(context)
        onFinished()
    }

    fun back() {
        when (current) {
            FirstRunPage.Hi -> finish()
            FirstRunPage.GetTermux -> go(FirstRunPage.Hi)
            FirstRunPage.CopyCommands -> go(FirstRunPage.GetTermux)
            FirstRunPage.IDidThat -> go(FirstRunPage.CopyCommands)
            FirstRunPage.OpenCode -> go(FirstRunPage.IDidThat)
            FirstRunPage.Mic -> go(FirstRunPage.OpenCode)
            FirstRunPage.OpenRouter -> go(FirstRunPage.Mic)
            FirstRunPage.ApiKey -> go(FirstRunPage.OpenRouter)
            FirstRunPage.GitHub -> go(FirstRunPage.ApiKey)
            FirstRunPage.Avatar -> go(FirstRunPage.GitHub)
            FirstRunPage.Background -> go(FirstRunPage.Avatar)
            FirstRunPage.OptionalServer -> go(FirstRunPage.Background)
            FirstRunPage.ServerFields -> go(FirstRunPage.OptionalServer)
            FirstRunPage.Done -> go(FirstRunPage.Background)
        }
    }

    BackHandler { back() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
                    TextButton(onClick = { finish() }) { Text("Skip") }
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
                        title = "Hi.",
                        body = "This is your helper. It lives on this phone.",
                        primary = "Let’s go",
                        onPrimary = { go(FirstRunPage.GetTermux) },
                    )
                    FirstRunPage.GetTermux -> GetTermuxPage(
                        termuxOk = termuxOk,
                        onRefresh = { termuxOk = termuxInstalled(context) },
                        onGetFdroid = { openUrl(context, TermuxComputerLinks.TERMUX_FDROID) },
                        onGetGithub = { openUrl(context, TermuxComputerLinks.TERMUX_GITHUB_RELEASES) },
                        onGetNetBird = { openUrl(context, TermuxComputerLinks.TERMUX_NETBIRD) },
                        onOpenTermux = {
                            termuxOk = termuxInstalled(context)
                            if (!openTermux(context)) {
                                openAppLauncher(context)
                            }
                        },
                        onInstalled = {
                            termuxOk = termuxInstalled(context)
                            openTermux(context)
                            go(FirstRunPage.CopyCommands)
                        },
                        onShowApps = { openAppLauncher(context) },
                    )
                    FirstRunPage.CopyCommands -> CopyCommandsPage(
                        copied = copied,
                        onCopy = {
                            copyText(context, TermuxComputerLinks.BOOTSTRAP_COMMANDS)
                            copied = true
                        },
                        onOpenTermux = {
                            termuxOk = termuxInstalled(context)
                            if (!openTermux(context)) openAppLauncher(context)
                        },
                        onNext = { go(FirstRunPage.IDidThat) },
                    )
                    FirstRunPage.IDidThat -> KidPage(
                        title = if (termuxOk) "Termux is on this phone." else "Almost done.",
                        body = if (termuxOk) {
                            "Nice. We found Termux. Next we start OpenCode in Terminal."
                        } else {
                            "Open Termux, paste the words, then come back. Or open your apps if you still need to install it."
                        },
                        primary = "I did that",
                        onPrimary = {
                            termuxOk = termuxInstalled(context)
                            go(FirstRunPage.OpenCode)
                        },
                        secondary = TermuxComputerLinks.BUTTON_OPEN_TERMUX,
                        onSecondary = {
                            termuxOk = termuxInstalled(context)
                            if (!openTermux(context)) openAppLauncher(context)
                        },
                        tertiary = TermuxComputerLinks.BUTTON_SHOW_APPS,
                        onTertiary = { openAppLauncher(context) },
                    )
                    FirstRunPage.OpenCode -> {
                        // Auto-start once when Computer/Termux is present. Never trap without Termux.
                        LaunchedEffect(Unit) {
                            termuxOk = termuxInstalled(context)
                            if (termuxOk && !openCodeStarted) {
                                openCodeStarted = true
                                onStartOpenCode()
                            }
                        }
                        OpenCodePage(
                            termuxOk = termuxOk,
                            alreadyStarted = openCodeStarted,
                            onStartAgain = {
                                termuxOk = termuxInstalled(context)
                                if (termuxOk) {
                                    openCodeStarted = true
                                    onStartOpenCode()
                                } else {
                                    openAppLauncher(context)
                                }
                            },
                            onOpenTermux = {
                                termuxOk = termuxInstalled(context)
                                if (!openTermux(context)) openAppLauncher(context)
                            },
                            onShowApps = { openAppLauncher(context) },
                            onGetNetBird = { openUrl(context, TermuxComputerLinks.TERMUX_NETBIRD) },
                            onNext = { go(FirstRunPage.Mic) },
                        )
                    }
                    FirstRunPage.Mic -> KidPage(
                        title = "Can we use the mic and camera?",
                        body = "Talking needs the mic. Face-following needs the camera. You can say no.",
                        primary = "Allow",
                        onPrimary = {
                            onEnablePermissions()
                            go(FirstRunPage.OpenRouter)
                        },
                        secondary = "Not now",
                        onSecondary = { go(FirstRunPage.OpenRouter) },
                    )
                    FirstRunPage.OpenRouter -> OpenRouterGuidePage(
                        onNext = { go(FirstRunPage.ApiKey) },
                        onSkip = { go(FirstRunPage.ApiKey) },
                    )
                    FirstRunPage.ApiKey -> ApiKeyPage(
                        apiKey = apiKey,
                        onApiKey = { apiKey = it },
                        onGetKey = { openUrl(context, TermuxComputerLinks.OPENCODE_ZEN_KEY_URL) },
                        onSaveAndDone = {
                            saveOpenCodeKey(storage, apiKey)
                            go(FirstRunPage.Done)
                        },
                        onSaveAndKeepGoing = {
                            saveOpenCodeKey(storage, apiKey)
                            go(FirstRunPage.GitHub)
                        },
                        onSkipDone = { go(FirstRunPage.Done) },
                        onSkipKeepGoing = { go(FirstRunPage.GitHub) },
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
                                    go(FirstRunPage.Avatar)
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
                            if (result.ok) go(FirstRunPage.Avatar)
                        },
                        onSkip = {
                            GitHubProjectSetup.markSkipped(storage)
                            go(FirstRunPage.Avatar)
                        },
                    )
                    FirstRunPage.Avatar -> AvatarPickPage(
                        selectedKey = avatarChoice,
                        onSelect = { style ->
                            avatarChoice = style.key
                            storage.savePreference("avatar_style", style.key)
                            onAvatarPicked(style)
                        },
                        onNext = { go(FirstRunPage.Background) },
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
                        onNext = { finish() },
                        onMore = { go(FirstRunPage.OptionalServer) },
                    )
                    FirstRunPage.OptionalServer -> KidPage(
                        title = "Have a computer on the internet?",
                        body = "You do not need one. This phone is enough.",
                        primary = "No, I’m done",
                        onPrimary = { go(FirstRunPage.Done) },
                        secondary = "Yes",
                        onSecondary = { go(FirstRunPage.ServerFields) },
                    )
                    FirstRunPage.ServerFields -> ServerFieldsPage(
                        host = host,
                        user = user,
                        password = password,
                        onHost = { host = it },
                        onUser = { user = it },
                        onPassword = { password = it },
                        onSave = {
                            if (host.isNotBlank()) storage.savePreference("onboard_remote_host", host.trim())
                            if (user.isNotBlank()) storage.savePreference("onboard_remote_user", user.trim())
                            if (password.isNotBlank()) storage.saveApiKey("onboard_remote", password)
                            go(FirstRunPage.Done)
                        },
                        onLater = { go(FirstRunPage.Done) },
                    )
                    FirstRunPage.Done -> KidPage(
                        title = "You’re ready.",
                        body = "Chat with your helper, or open Terminal.",
                        primary = "Chat",
                        onPrimary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenChat()
                        },
                        secondary = "Terminal",
                        onSecondary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenTerminal()
                        },
                    )
                }
            }
        }
    }
}

private fun saveOpenCodeKey(storage: SecureStorageManager, raw: String) {
    val key = raw.trim()
    if (key.isEmpty()) return
    // Same store Settings / CloudModelSettings use for OpenCode Zen.
    storage.saveApiKey("opencode", key)
    storage.saveModelName("opencode", "big-pickle")
    storage.savePreference("brain_provider", "bigpickle")
    storage.savePreference("default_brain", "bigpickle")
    storage.savePreference("selected_model", "Omniroute (Local router)")
}

@Composable
private fun OpenCodePage(
    termuxOk: Boolean,
    alreadyStarted: Boolean,
    onStartAgain: () -> Unit,
    onOpenTermux: () -> Unit,
    onShowApps: () -> Unit,
    onGetNetBird: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "OpenCode in Terminal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (termuxOk) {
                if (alreadyStarted) {
                    "Terminal is installing OpenCode if needed, then starting it. You can peek, then continue."
                } else {
                    "We’ll open your in-app Terminal and run a short install, then start OpenCode."
                }
            } else {
                "Termux isn’t ready yet. Get it first — you won’t get stuck. You can still continue for an AI key."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (termuxOk) {
            KidPrimaryButton(
                if (alreadyStarted) "Show Terminal again" else "Start OpenCode",
                onStartAgain,
            )
        } else {
            KidPrimaryButton(TermuxComputerLinks.BUTTON_OPEN_TERMUX, onOpenTermux)
            OutlinedButton(
                onClick = onGetNetBird,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(TermuxComputerLinks.BUTTON_NETBIRD, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onShowApps) { Text(TermuxComputerLinks.BUTTON_SHOW_APPS) }
        }
        KidPrimaryButton("Continue", onNext)
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
private fun ApiKeyPage(
    apiKey: String,
    onApiKey: (String) -> Unit,
    onGetKey: () -> Unit,
    onSaveAndDone: () -> Unit,
    onSaveAndKeepGoing: () -> Unit,
    onSkipDone: () -> Unit,
    onSkipKeepGoing: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "AI needs a key",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Chat uses an OpenCode Zen key (same place as Settings). Tap Get a key, copy it, paste here.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        KidPrimaryButton("Get a free key", onGetKey)
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKey,
            label = { Text("Paste your key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        KidPrimaryButton(
            if (apiKey.isBlank()) "Start using Meow" else "Save key · Start using Meow",
            onClick = if (apiKey.isBlank()) onSkipDone else onSaveAndDone,
        )
        OutlinedButton(
            onClick = if (apiKey.isBlank()) onSkipKeepGoing else onSaveAndKeepGoing,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                if (apiKey.isBlank()) "Keep going" else "Save key · Keep going",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "Done stops here. Keep going can add GitHub, then a face and a background.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvatarPickPage(
    selectedKey: String,
    onSelect: (AvatarStyle) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Pick a face",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Lumen is the default. You can change this later.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AvatarStyle.entries.forEach { style ->
            val selected = style.key == selectedKey
            OutlinedButton(
                onClick = { onSelect(style) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(
                        if (selected) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(12.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    if (selected) "✓ ${style.label}" else style.label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        KidPrimaryButton("Next", onNext)
    }
}

@Composable
private fun BackgroundPickPage(
    selectedId: String,
    onSelect: (BackgroundAnimation) -> Unit,
    onNone: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit = {},
) {
    // Gallery subset with live preview (full catalog remains in Backgrounds settings).
    val choices = listOf(
        BackgroundAnimation.AURORA,
        BackgroundAnimation.STARFIELD,
        BackgroundAnimation.NEBULA,
        BackgroundAnimation.EMBERS,
        BackgroundAnimation.WOODLAND,
        BackgroundAnimation.LAGOON,
        BackgroundAnimation.RAINY_LOFT,
        BackgroundAnimation.CLEAR_SKY,
        BackgroundAnimation.KITCHEN,
        BackgroundAnimation.CARTOON_MOON_GARDEN,
        BackgroundAnimation.CARTOON_ALIEN_AQUARIUM,
    )
    val previewAnim = choices.firstOrNull { it.id == selectedId }
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
            "Tap a scene for a live preview. More habitats live in Settings later.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            if (previewAnim != null) {
                com.example.meowmba.ui.companion.AppBackground(
                    selection = BackgroundSelection(
                        kind = BackgroundKind.ANIMATION,
                        value = previewAnim.id,
                        dimPercent = 20,
                    ),
                    weather = null,
                    modifier = Modifier.fillMaxSize(),
                    paused = false,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No background", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        OutlinedButton(
            onClick = onNone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                if (selectedId.isEmpty()) "✓ None" else "None",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        choices.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { anim ->
                    val selected = anim.id == selectedId
                    OutlinedButton(
                        onClick = { onSelect(anim) },
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp),
                                    )
                                } else Modifier,
                            ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            com.example.meowmba.ui.companion.AnimationThumbnail(
                                anim,
                                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Text(
                                if (selected) "✓ ${anim.label}" else anim.label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        KidPrimaryButton("Finish setup", onNext)
        TextButton(onClick = onMore) { Text("Optional: add a remote computer") }
    }
}

@Composable
private fun GetTermuxPage(
    termuxOk: Boolean,
    onRefresh: () -> Unit,
    onGetFdroid: () -> Unit,
    onGetGithub: () -> Unit,
    onGetNetBird: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstalled: () -> Unit,
    onShowApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Your Computer is this phone.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (termuxOk) {
                "Termux is ready. Tap Open Termux, then come back."
            } else {
                "First, get a free app called Termux. Use a big button — not the Play Store. After you install it, open it from here."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        KidPrimaryButton(TermuxComputerLinks.BUTTON_GET_TERMUX, onGetFdroid)
        OutlinedButton(
            onClick = onGetGithub,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(TermuxComputerLinks.BUTTON_GITHUB_DOWNLOAD, style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = onGetNetBird,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(TermuxComputerLinks.BUTTON_NETBIRD, style = MaterialTheme.typography.titleMedium) }
        KidPrimaryButton(TermuxComputerLinks.BUTTON_OPEN_TERMUX) {
            onRefresh()
            onOpenTermux()
        }
        OutlinedButton(
            onClick = onInstalled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(TermuxComputerLinks.BUTTON_I_INSTALLED, style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onShowApps) { Text(TermuxComputerLinks.BUTTON_SHOW_APPS) }
        TextButton(onClick = {
            onRefresh()
            onInstalled()
        }) { Text("I already have it") }
    }
}

@Composable
private fun CopyCommandsPage(
    copied: Boolean,
    onCopy: () -> Unit,
    onOpenTermux: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Open Termux and paste this.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "This makes a tiny Linux on your phone.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            TermuxComputerLinks.BOOTSTRAP_COMMANDS,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                lineHeight = 28.sp,
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        KidPrimaryButton(TermuxComputerLinks.BUTTON_OPEN_TERMUX, onOpenTermux)
        KidPrimaryButton(if (copied) "Copied!" else "Copy", onCopy)
        TextButton(onClick = onNext) { Text("Next") }
    }
}

@Composable
private fun ServerFieldsPage(
    host: String,
    user: String,
    password: String,
    onHost: (String) -> Unit,
    onUser: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSave: () -> Unit,
    onLater: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Add that computer later if you want.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Name, user, and secret. Or skip.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            label = { Text("Computer name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user,
            onValueChange = onUser,
            label = { Text("Your name on it") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("Secret word") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        KidPrimaryButton("Save", onSave)
        TextButton(onClick = onLater) { Text("Add later") }
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
