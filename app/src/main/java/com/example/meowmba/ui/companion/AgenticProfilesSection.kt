package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import com.example.meowmba.ui.theme.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileType
import java.util.UUID

/**
 * Settings affordance for agentic SSH connection profiles.
 * Credential **values** go through [onSaveCredential] → SecureStorage; never log them.
 */
@Composable
fun AgenticProfilesSection(
    profiles: List<ConnectionProfile>,
    onSaveProfile: (ConnectionProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveCredential: (credentialRef: String, secret: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var credentialRef by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fingerprint by remember { mutableStateOf("") }
    var pinOnFirst by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    fun load(p: ConnectionProfile?) {
        editingId = p?.id
        displayName = p?.displayName.orEmpty()
        host = p?.host.orEmpty()
        port = (p?.port ?: 22).toString()
        username = p?.username.orEmpty()
        credentialRef = p?.credentialRef.orEmpty()
        fingerprint = p?.hostKeyFingerprint.orEmpty()
        pinOnFirst = p?.pinHostKeyOnFirstConnect ?: true
        password = ""
        status = null
    }

    fun clearForm() = load(null)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Agentic connection profiles", style = MaterialTheme.typography.titleMedium)
        Text(
            "SSH targets for agentic runtime (preferred day-to-day path). Passwords are stored " +
                "under a credential ref in encrypted prefs — never in Room or event logs. " +
                "HTTPS_RUNTIME needs a reachable base URL (not 127.0.0.1 on meow); worker port 18766.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Text("No profiles yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            profiles.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${p.username}@${p.host}:${p.port} · ref=${p.credentialRef}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val fp = p.hostKeyFingerprint
                        if (!fp.isNullOrBlank()) {
                            Text(
                                "hostKey=${fp.take(24)}…",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    OutlinedButton(onClick = { load(p) }) { Text("Edit") }
                    OutlinedButton(onClick = { onDeleteProfile(p.id); if (editingId == p.id) clearForm() }) {
                        Text("Del")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (editingId == null) "Add SSH profile" else "Edit profile",
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = credentialRef,
            onValueChange = { credentialRef = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
            label = { Text("Credential ref name") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (SecureStorage)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = fingerprint,
            onValueChange = { fingerprint = it },
            label = { Text("Host key fingerprint (optional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pinOnFirst, onCheckedChange = { pinOnFirst = it })
            Text("Pin host key on first connect (TOFU)")
        }

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    val ref = credentialRef.trim()
                    if (displayName.isBlank() || host.isBlank() || username.isBlank() || ref.isBlank()) {
                        status = "Name, host, user, and credential ref are required."
                        return@Button
                    }
                    val profile = ConnectionProfile(
                        id = editingId ?: UUID.randomUUID().toString(),
                        displayName = displayName.trim(),
                        type = ConnectionProfileType.SSH,
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 22,
                        username = username.trim(),
                        credentialRef = ref,
                        hostKeyFingerprint = fingerprint.trim().ifBlank { null },
                        pinHostKeyOnFirstConnect = pinOnFirst,
                    )
                    onSaveProfile(profile)
                    if (password.isNotEmpty()) {
                        onSaveCredential(ref, password)
                        password = ""
                    }
                    status = "Saved profile ${profile.displayName}."
                    clearForm()
                },
            ) { Text("Save profile") }
            Spacer(Modifier.padding(8.dp))
            OutlinedButton(onClick = { clearForm() }) { Text("Clear") }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
