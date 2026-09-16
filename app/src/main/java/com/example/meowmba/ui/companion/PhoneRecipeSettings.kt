package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.OutlinedButton

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import com.example.meowmba.core.agent.PhoneRecipeStore

@Composable
internal fun PhoneRecipeSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phone_recipe_preferences", 0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val file = remember { File(context.filesDir, "phone_recipes/recipes.json") }
    val counts = remember(revision) { PhoneRecipeStore(file).counts() }
    Spacer(androidx.compose.ui.Modifier.height(8.dp))
    VoiceToggle(label = "Learn search workflows", checked = enabled,
        description = "Learn navigation from verified searches such as ‘use my phone to search for birds in YouTube’. After three matching verified runs, reuse the steps with a new query. Search terms are not saved in recipes. App changes or failed checks stop replay.",
        onCheckedChange = {
            enabled = it
            prefs.edit().putBoolean("enabled", it).putInt("epoch", prefs.getInt("epoch", 0) + 1).apply()
        })
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("${counts.first} learning · ${counts.second} ready", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { revision++ }) { Text("Refresh workflows") }
        OutlinedButton(onClick = {
            prefs.edit().putInt("epoch", prefs.getInt("epoch", 0) + 1).apply()
            if (!file.exists() || file.delete()) { error = null; revision++ }
            else error = "Could not remove the saved workflows. Try again."
        }) { Text("Forget workflows") }
    }
}
