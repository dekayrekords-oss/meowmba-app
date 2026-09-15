package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    reflexGain: Float = 1f,
    onReflexGainChange: (Float) -> Unit = {},
    avatarFpsTarget: Int = 30,
    onAvatarFpsTargetChange: (Int) -> Unit = {},
    showNeuralCoreControls: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenComputerTools: () -> Unit = {},
    maleCnsServer: MaleCnsServerUiState = MaleCnsServerUiState(),
    onPairMaleCnsServer: (String) -> Unit = {},
    onUsePairedComputerForMaleCns: () -> Unit = {},
    onTestMaleCnsServer: () -> Unit = {},
    onPairComputer: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onOpenComputerTools, modifier = Modifier.fillMaxWidth()) {
            Text(COMPUTER_TOOLS_TITLE)
        }
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
            maleCnsServer, onPairMaleCnsServer, onUsePairedComputerForMaleCns,
            onTestMaleCnsServer, onPairComputer,
        )
    }
}
