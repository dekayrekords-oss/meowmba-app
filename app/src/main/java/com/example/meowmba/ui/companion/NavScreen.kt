package com.example.meowmba.ui.companion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.meowmba.core.nav.CurrentLocation
import com.example.meowmba.core.nav.NavigationLauncher
import com.example.meowmba.core.nav.OfflinePoiSource
import com.example.meowmba.core.nav.SavedPlaceStore
import com.example.meowmba.core.security.SecureStorageManager

/**
 * The NAV card: offline region packs, the online fallback's key, and saved places.
 *
 * Everything here is local to the phone — no server involved — so the whole card
 * keeps working standalone.
 */
@Composable
fun NavScreen(
    secureStorage: SecureStorageManager? = null,
    state: CompanionState = CompanionState(),
    drivingControls: @Composable () -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val poiSource = remember { OfflinePoiSource(context) }
    val savedStore = remember { SavedPlaceStore(context) }
    val savedPlaces by savedStore.places.collectAsState()

    // installedPacks() hits the disk, so only recompute when something changed.
    var packChanges by remember { mutableStateOf(0) }
    val packs = remember(packChanges) { poiSource.installedPacks() }

    var newPlaceName by remember { mutableStateOf("") }
    var geoapifyKey by remember {
        mutableStateOf(secureStorage?.getApiKey("geoapify").orEmpty())
    }
    var status by remember { mutableStateOf<String?>(null) }

    val packPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        var ok = 0
        uris.forEach { uri ->
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "region.sqlite"
            val installed = poiSource.install(name) {
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("cannot read $name")
            }
            if (installed) ok++
        }
        packChanges++
        status = "Installed $ok of ${uris.size} pack(s)"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Live direction animation while the car is moving: the arrow leans
            // with real turns/braking, speed shown live. Hidden when parked.
            DriveDirectionStrip(state = state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Auto",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("CLOSE") }
            }
            drivingControls()
            Text("Navigation", style = MaterialTheme.typography.titleLarge)
            Text(
                "Offline-first. Region packs are searched before the online fallback, " +
                    "so this works with no signal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )

            // ── Offline packs ───────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Text("Offline region packs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            if (packs.isEmpty()) {
                Text(
                    "None installed — offline search will find nothing. Build one with " +
                        "scripts/build_poi_pack.py from an OpenStreetMap extract, then import it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            } else {
                packs.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.region,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${"%,d".format(p.poiCount)} places · ${p.sizeBytes / 1_000_000}MB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                        TextButton(onClick = {
                            if (poiSource.uninstall(p.fileName)) {
                                packChanges++
                                status = "Removed ${p.region}"
                            }
                        }) { Text("✕") }
                    }
                }
                Text(
                    "${"%,d".format(packs.sumOf { it.poiCount })} places across " +
                        "${packs.size} region(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { packPicker.launch(arrayOf("*/*")) }) { Text("Import packs") }
                OutlinedButton(onClick = {
                    poiSource.refresh()
                    packChanges++
                    status = "Rescanned"
                }) { Text("Rescan") }
            }

            // ── Online fallback ─────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Text("Online fallback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional Geoapify key, used only when the offline packs have no match. " +
                    "Free tier is about 3,000 lookups a day.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Get a free API key →",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    // Opens the Geoapify projects dashboard, where the key is created.
                    // The manifest already declares an https VIEW intent in <queries>,
                    // so this resolves on API 30+.
                    status = if (openUrl(context, GEOAPIFY_SIGNUP_URL)) {
                        "Opened geoapify.com — create a project, then paste the key below"
                    } else {
                        "No browser available. Visit $GEOAPIFY_SIGNUP_URL"
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            SettingsTextField(
                value = geoapifyKey,
                onValueChange = { geoapifyKey = it },
                label = { Text("Geoapify API key (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                secureStorage?.saveApiKey("geoapify", geoapifyKey.trim())
                status = if (geoapifyKey.isBlank()) "Key cleared" else "Key saved"
            }) { Text("Save key") }

            // ── Saved places ────────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Text("Saved places", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            if (savedPlaces.isEmpty()) {
                Text(
                    "None yet. Name this spot below, or just say \"remember this as home\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            } else {
                savedPlaces.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%.4f, %.4f".format(p.lat, p.lon),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                        TextButton(onClick = {
                            NavigationLauncher.navigateTo(context, p.toPlace())
                        }) { Text("GO") }
                        TextButton(onClick = { savedStore.delete(p.name) }) { Text("✕") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsTextField(
                value = newPlaceName,
                onValueChange = { newPlaceName = it },
                label = { Text("Name this location (home, work, gym…)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                val name = newPlaceName.trim()
                when {
                    name.isBlank() -> status = "Give it a name first"
                    !CurrentLocation.hasPermission(context) ->
                        status = "Location permission needed"
                    else -> {
                        val fix = CurrentLocation.last(context)
                        if (fix == null) {
                            status = "No location fix yet"
                        } else {
                            savedStore.save(name, fix.lat, fix.lon)
                            status = "Saved \"${name.lowercase()}\""
                            newPlaceName = ""
                        }
                    }
                }
            }) { Text("Save current location") }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val GEOAPIFY_SIGNUP_URL = "https://myprojects.geoapify.com/"

/**
 * Opens [url] in a browser. Returns false if nothing on the device can handle it, so
 * the caller can show the URL as text instead of failing silently.
 */
private fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: Exception) {
    Log.w("NavScreen", "Could not open $url: ${e.message}")
    false
}
