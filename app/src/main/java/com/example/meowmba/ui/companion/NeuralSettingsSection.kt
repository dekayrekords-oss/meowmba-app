package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agentic.AgenticGoal
import com.example.meowmba.core.agentic.AgenticModelConfig
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.computer.ComputerSetupStatus
import com.example.meowmba.core.config.ComputerPowerState
import com.example.meowmba.core.neural.NeuralDiagnostics
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralSubstrate

/**
 * Neural controller + Lumen nervous-system settings.
 *
 * Lumen reflex / agentic runtime toggles are wired to [com.example.meowmba.core.config.FeatureFlags]
 * (defaults OFF). Neural mode/substrate callbacks remain optional until that layer is mounted.
 */
@Composable
fun NeuralSettingsSection(
    mode: NeuralMode = NeuralMode.SHADOW,
    substrate: NeuralSubstrate = NeuralSubstrate.RATE,
    traceConsent: Boolean = false,
    diagnostics: NeuralDiagnostics? = null,
    onModeChanged: (NeuralMode) -> Unit = {},
    onSubstrateChanged: (NeuralSubstrate) -> Unit = {},
    onTraceConsentChanged: (Boolean) -> Unit = {},
    onReset: () -> Unit = {},
    lumenReflexV2: Boolean = false,
    onLumenReflexV2Change: (Boolean) -> Unit = {},
    agenticRuntimeV1: Boolean = false,
    onAgenticRuntimeV1Change: (Boolean) -> Unit = {},
    reflexGain: Float = 1f,
    onReflexGainChange: (Float) -> Unit = {},
    avatarFpsTarget: Int = 30,
    onAvatarFpsTargetChange: (Int) -> Unit = {},
    computerPower: com.example.meowmba.core.config.ComputerPowerState =
        com.example.meowmba.core.config.ComputerPowerState.Idle,
    onComputerPowerChange: (com.example.meowmba.core.config.ComputerPowerState) -> Unit = {},
    computerIdleMinutes: Int = 5,
    onComputerIdleMinutesChange: (Int) -> Unit = {},
    showNeuralCoreControls: Boolean = false,
    connectionProfiles: List<ConnectionProfile> = emptyList(),
    onSaveConnectionProfile: (ConnectionProfile) -> Unit = {},
    onDeleteConnectionProfile: (String) -> Unit = {},
    onSaveAgenticCredential: (String, String) -> Unit = { _, _ -> },
    agenticGoals: List<AgenticGoal> = emptyList(),
    agenticModelConfig: AgenticModelConfig.Snapshot = AgenticModelConfig.Snapshot(
        baseUrl = "",
        apiKeyRef = "",
        modelName = AgenticModelConfig.DEFAULT_MODEL,
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
    // Your Computer (phone-first; optional server)
    computerPhoneProbeSummary: String = "Checking this phone…",
    computerPhoneReady: Boolean = false,
    computerSetupStatus: ComputerSetupStatus = ComputerSetupStatus.NotSetUp,
    computerStatusDetail: String = "",
    computerInstallLog: String = "",
    computerPowerState: ComputerPowerState = ComputerPowerState.Idle,
    computerVncUrl: String? = null,
    computerSavedProfile: ConnectionProfile? = null,
    computerShowInstallPrompt: Boolean = false,
    onComputerSaveProfile: (ConnectionProfile, String) -> Unit = { _, _ -> },
    onComputerTestConnection: (ConnectionProfile, String) -> Unit = { _, _ -> },
    onComputerInstall: (ConnectionProfile, String) -> Unit = { _, _ -> },
    onComputerOpenTerminal: () -> Unit = {},
    onComputerOpenDesktop: (String) -> Unit = {},
    onComputerConfirmInstallPrompt: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Neural setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose how your companion reacts, then adjust sensitivity and animation speed. Changes save as you make them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NeuralSetupControls(
            mode, substrate, traceConsent, diagnostics, showNeuralCoreControls,
            onModeChanged, onSubstrateChanged, onTraceConsentChanged, onReset,
            lumenReflexV2, onLumenReflexV2Change, reflexGain, onReflexGainChange,
            avatarFpsTarget, onAvatarFpsTargetChange,
        )
        var advanced by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Hide computer & task tools" else "Computer & task tools")
        }
        Text("Optional task automation and computer connections. Avatar reactions work without these tools.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (advanced) {
        YourComputerSettingsSection(
            phoneProbeSummary = computerPhoneProbeSummary,
            phoneReady = computerPhoneReady,
            status = computerSetupStatus,
            statusDetail = computerStatusDetail,
            installLog = computerInstallLog,
            computerPower = computerPower,
            vncUrl = computerVncUrl,
            savedComputerProfile = computerSavedProfile,
            showInstallPrompt = computerShowInstallPrompt,
            onSaveComputerProfile = onComputerSaveProfile,
            onTestConnection = onComputerTestConnection,
            onInstallComputer = onComputerInstall,
            onOpenTerminal = onComputerOpenTerminal,
            onOpenDesktop = onComputerOpenDesktop,
            onPowerChange = onComputerPowerChange,
            onConfirmInstallPrompt = onComputerConfirmInstallPrompt,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Computer", style = MaterialTheme.typography.titleSmall)
            Text(
                com.example.meowmba.core.config.ComputerPowerStore.COPY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Power ${computerPower.name.lowercase()} — On keeps the guest; Idle sleeps it after " +
                    "${computerIdleMinutes.coerceIn(1, 30)} min unused; Off holds no Termux/PRoot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.meowmba.core.config.ComputerPowerState.entries.forEach { st ->
                    Button(
                        onClick = { onComputerPowerChange(st) },
                        enabled = st != computerPower,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(st.name)
                    }
                }
            }
            Text(
                "Idle after ${computerIdleMinutes.coerceIn(1, 30)} minutes unused (1–30).",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = computerIdleMinutes.coerceIn(1, 30).toFloat(),
                onValueChange = { onComputerIdleMinutesChange(it.toInt().coerceIn(1, 30)) },
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Task automation", style = MaterialTheme.typography.titleSmall)
            Text("Enables the task planner, connection profiles and optional model adapter. Tasks may request approval before using a remote computer.", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agenticRuntimeV1, onCheckedChange = onAgenticRuntimeV1Change)
                Text("Enable agentic (tasks / SSH / model)")
            }
        }

        if (agenticRuntimeV1) {
            AgenticTasksSection(
                goals = agenticGoals,
                modelConfig = agenticModelConfig,
                sshCapabilitySummary = sshCapabilitySummary,
                onCreateGoal = onCreateAgenticGoal,
                onTickGoal = onTickAgenticGoal,
                onTickOpenGoals = onTickOpenAgenticGoals,
                onApproveGoal = onApproveAgenticGoal,
                onRejectGoal = onRejectAgenticGoal,
                onRefresh = onRefreshAgenticTasks,
                onSaveModelConfig = onSaveAgenticModelConfig,
            )
            AgenticProfilesSection(
                profiles = connectionProfiles,
                onSaveProfile = onSaveConnectionProfile,
                onDeleteProfile = onDeleteConnectionProfile,
                onSaveCredential = onSaveAgenticCredential,
            )
        }

        } // Computer and task tools.
    }
}
