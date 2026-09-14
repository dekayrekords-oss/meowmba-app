package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Studio screen (opened from the Studio tile): saved recurring characters and
 * every comic/script/ad the agent has made. Tapping an artifact opens it in
 * the browser panel — same display path a finished tool call takes, so what
 * you proofread here is exactly what was shown when it was created.
 */
@Composable
fun StudioScreen(
    viewModel: CompanionViewModel,
    onClose: () -> Unit
) {
    val characters by viewModel.studioCharacters.collectAsState(initial = emptyList())
    val artifacts by viewModel.studioArtifacts.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Studio", fontSize = 22.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item { Text("Characters", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary) }
                if (characters.isEmpty()) {
                    item {
                        Text(
                            "No saved characters yet. Say \"remember this character\" and describe them — " +
                                "comics and cartoons will then reuse the exact same design.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(characters, key = { it.name }) { c ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(c.name, fontSize = 16.sp)
                                Text(
                                    c.sheet.take(140) + if (c.sheet.length > 140) "…" else "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)); Text("Made for you", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary) }
                if (artifacts.isEmpty()) {
                    item {
                        Text(
                            "Nothing made yet. Try \"make a comic about…\" or \"write a YouTube script for…\".",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(artifacts, key = { it.absolutePath }) { f ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.showFileInBrowser(f) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(f.nameWithoutExtension.replace('_', ' '), fontSize = 15.sp)
                                Text(
                                    "${f.parentFile?.name ?: ""} · ${File(f.path).length() / 1024} KB",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
