package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.material3.Slider
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Movie
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface
import java.io.File

private enum class VendingPage {
    HUB, AVATAR_SIMPLE, AVATAR_WORKSHOP, PROP_GENERATE, PROP_LIBRARY, ANIMATION_GENERATE, BACKGROUND_GENERATE, STUDIO_ARCHIVE
}

/**
 * Replaces the old Studio tile: a playful vending machine that dispenses
 * avatar forms and companion props, while keeping comics/characters reachable.
 */
@Composable
fun VendingMachineScreen(
    viewModel: CompanionViewModel,
    onClose: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val characters by viewModel.studioCharacters.collectAsState(initial = emptyList())
    val artifacts by viewModel.studioArtifacts.collectAsState()
    var page by rememberSaveable { mutableStateOf(VendingPage.HUB.name) }
    val current = runCatching { VendingPage.valueOf(page) }.getOrDefault(VendingPage.HUB)

    Surface(modifier = Modifier.fillMaxSize().testTag("vending-machine"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (current != VendingPage.HUB) {
                    IconButton(onClick = { page = VendingPage.HUB.name }, modifier = Modifier.testTag("vending-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to vending hub")
                    }
                } else {
                    Icon(Icons.Default.Storefront, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        when (current) {
                            VendingPage.HUB -> "Vending Machine"
                            VendingPage.AVATAR_SIMPLE -> "Become anything"
                            VendingPage.AVATAR_WORKSHOP -> "Avatar workshop"
                            VendingPage.PROP_GENERATE -> "Prop dispenser"
                            VendingPage.PROP_LIBRARY -> "Prop library"
                            VendingPage.ANIMATION_GENERATE -> "Animation generator"
                            VendingPage.BACKGROUND_GENERATE -> "Background studio"
                            VendingPage.STUDIO_ARCHIVE -> "Comics & characters"
                        },
                        fontSize = 22.sp,
                    )
                    if (current == VendingPage.HUB) {
                        Text(
                            "Insert an idea · dispense a creation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close vending machine")
                }
            }
            Spacer(Modifier.height(12.dp))
            when (current) {
                VendingPage.HUB -> VendingHub(
                    onAvatarsSimple = { page = VendingPage.AVATAR_SIMPLE.name },
                    onAvatarsWorkshop = { page = VendingPage.AVATAR_WORKSHOP.name },
                    onPropsGenerate = { page = VendingPage.PROP_GENERATE.name },
                    onPropsLibrary = { page = VendingPage.PROP_LIBRARY.name },
                    onAnimationGenerate = { page = VendingPage.ANIMATION_GENERATE.name },
                    onStudioArchive = { page = VendingPage.STUDIO_ARCHIVE.name },
                    onBackground = { page = VendingPage.BACKGROUND_GENERATE.name },
                )
                VendingPage.BACKGROUND_GENERATE -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ReferencePhotoControls(uiState, viewModel)
                        BackgroundCreationControls(uiState, viewModel)
                    }
                }
                VendingPage.AVATAR_SIMPLE -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ReferencePhotoControls(uiState, viewModel)
                        GeneratedAvatarControls(
                            current = uiState.generatedAvatarForm,
                            saved = uiState.savedGeneratedForms,
                            busy = uiState.avatarFormGenerating,
                            message = uiState.avatarFormMessage,
                            isError = uiState.avatarFormError,
                            onGenerate = viewModel::createAvatarForm,
                            onCancel = viewModel::cancelAvatarFormGeneration,
                            onSelect = viewModel::selectGeneratedAvatarForm,
                            onDelete = viewModel::deleteGeneratedAvatarForm,
                            onRefine = viewModel::refineAvatarForm,
                        )
                    }
                }
                VendingPage.AVATAR_WORKSHOP -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ReferencePhotoControls(uiState, viewModel)
                        AvatarWorkshopPanel(
                            busy = uiState.avatarFormGenerating,
                            message = uiState.avatarFormMessage,
                            isError = uiState.avatarFormError,
                            wearing = uiState.generatedAvatarForm,
                            props = uiState.savedProps,
                            onInvent = viewModel::createAvatarForm,
                            onRefine = viewModel::refineAvatarForm,
                            onInventFromCurrent = viewModel::inventFromCurrentAvatar,
                            onCancel = viewModel::cancelAvatarFormGeneration,
                            onPlaceProp = viewModel::openPropPlacement,
                        )
                    }
                }
                VendingPage.PROP_GENERATE -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ReferencePhotoControls(uiState, viewModel)
                        PropGeneratePanel(
                            busy = uiState.propInventGenerating,
                            message = uiState.propInventMessage,
                            isError = uiState.propInventError,
                            onInvent = viewModel::inventProp,
                            onCancel = viewModel::cancelPropInvent,
                            onOpenLibrary = { page = VendingPage.PROP_LIBRARY.name },
                        )
                    }
                }
                VendingPage.PROP_LIBRARY -> {
                    PropLibraryPanel(
                        props = uiState.savedProps,
                        equippedId = uiState.equippedProp?.id,
                        onUse = { viewModel.equipProp(it); if (!viewModel.uiState.value.propInventError) onClose() },
                        onPutAway = { viewModel.equipProp(null) },
                        onDelete = viewModel::deleteProp,
                        onPlace = viewModel::openPropPlacement,
                        modifier = Modifier.weight(1f),
                    )
                }
                VendingPage.ANIMATION_GENERATE -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        AnimationGeneratePanel(
                            busy = uiState.animInventGenerating,
                            message = uiState.animInventMessage,
                            isError = uiState.animInventError,
                            props = uiState.savedProps,
                            savedNames = uiState.savedAnimationNames,
                            onInvent = viewModel::inventAnimationFromVending,
                            onCancel = viewModel::cancelAnimInvent,
                            onPlay = viewModel::playSavedAnimation,
                            onDelete = viewModel::deleteSavedAnimation,
                            onRefresh = viewModel::refreshSavedAnimations,
                        )
                    }
                }
                VendingPage.STUDIO_ARCHIVE -> {
                    StudioArchivePanel(
                        characters = characters,
                        artifacts = artifacts,
                        onOpenArtifact = { viewModel.showFileInBrowser(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VendingHub(
    onAvatarsSimple: () -> Unit,
    onAvatarsWorkshop: () -> Unit,
    onPropsGenerate: () -> Unit,
    onPropsLibrary: () -> Unit,
    onAnimationGenerate: () -> Unit,
    onStudioArchive: () -> Unit,
    onBackground: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("vending-hub"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                "Pick a lane. The machine keeps your simple “become a …” path and adds a detailed workshop plus prop dispenser.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            VendingLaneCard(
                title = "Avatars · simple",
                subtitle = "Type or say “become a …” — same invent path as before.",
                icon = Icons.Default.Face,
                onClick = onAvatarsSimple,
                tag = "vending-lane-avatar-simple",
            )
        }
        item {
            VendingLaneCard(
                title = "Avatars · workshop",
                subtitle = "Dial look, parts, style, and freeform detail into invent / refine.",
                icon = Icons.Default.AutoAwesome,
                onClick = onAvatarsWorkshop,
                tag = "vending-lane-avatar-workshop",
            )
        }
        item {
            VendingLaneCard(
                title = "Props · generate",
                subtitle = "Dispense a new prop the companion can hold, wear, or place.",
                icon = Icons.Default.Toys,
                onClick = onPropsGenerate,
                tag = "vending-lane-prop-generate",
            )
        }
        item {
            VendingLaneCard(
                title = "Props · library",
                subtitle = "Browse saved dispensed props.",
                icon = Icons.Default.Inventory2,
                onClick = onPropsLibrary,
                tag = "vending-lane-prop-library",
            )
        }
        item {
            VendingLaneCard(
                title = "Animations · generate",
                subtitle = "Invent a short performance with selectable props and editable timing.",
                icon = Icons.Default.Movie,
                onClick = onAnimationGenerate,
                tag = "vending-lane-animation-generate",
            )
        }
        item {
            VendingLaneCard(title = "Backgrounds · create", subtitle = "Create a scene, use a photo, or build from its visual description.",
                icon = Icons.Default.Palette, onClick = onBackground, tag = "vending-lane-background")
        }
        item {
            VendingLaneCard(
                title = "Comics & characters",
                subtitle = "Former Studio archive — recurring characters and made-for-you pages.",
                icon = Icons.Default.Palette,
                onClick = onStudioArchive,
                tag = "vending-lane-studio-archive",
            )
        }
    }
}

@Composable
private fun VendingLaneCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AvatarWorkshopPanel(
    busy: Boolean,
    message: String?,
    isError: Boolean,
    wearing: GeneratedAvatarForm?,
    props: List<GeneratedPropForm> = emptyList(),
    onInvent: (String, InventReasonLevel) -> Unit,
    onRefine: (String, InventReasonLevel) -> Unit,
    onInventFromCurrent: (String, InventReasonLevel) -> Unit = { _, _ -> },
    onCancel: () -> Unit,
    onPlaceProp: (String) -> Unit = {},
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var look by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("cartoon") }
    var head by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var arms by rememberSaveable { mutableStateOf("") }
    var legs by rememberSaveable { mutableStateOf("") }
    var tail by rememberSaveable { mutableStateOf("") }
    var freeform by rememberSaveable { mutableStateOf("") }
    var reasonKey by rememberSaveable { mutableStateOf(InventReasonLevel.BALANCED.name) }
    val reason = InventReasonLevel.fromKey(reasonKey)
    val composed = AvatarWorkshopPrompt.build(
        subject = subject, look = look, style = style,
        head = head, body = body, arms = arms, legs = legs, tail = tail, freeform = freeform,
    )
    val canGo = composed.isNotBlank() && !busy

    var selectedPropIds by remember { mutableStateOf(setOf<String>()) }
    val avatarPainter = remember { GeneratedAvatarPainter() }
    Column(Modifier.testTag("avatar-workshop"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Workshop builds one invent prompt from structured parts, then uses the same AvatarFormInventor + reason levels as Become-anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Preview", style = MaterialTheme.typography.titleSmall)
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("avatar-workshop-preview"),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val placement = generatedAvatarPlacement(size.width, size.height, LumenFrame(), scale = 1.2f)
                if (wearing != null) {
                    avatarPainter.draw(this, wearing, LumenFrame(), 0f, placement.center, placement.unit)
                } else {
                    drawCircle(Color(0xFFE8D5B9).copy(alpha = .85f), placement.unit * .36f, placement.center)
                    drawCircle(Color(0xFF182032), placement.unit * .055f, placement.center + Offset(-placement.unit * .11f, -placement.unit * .05f))
                    drawCircle(Color(0xFF182032), placement.unit * .055f, placement.center + Offset(placement.unit * .11f, -placement.unit * .05f))
                }
            }
        }
        if (wearing != null) {
            OutlinedButton(
                onClick = { onInventFromCurrent(composed, reason) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("workshop-from-current"),
            ) { Text("Generate from current avatar (${wearing.name})") }
        }
        if (props.isNotEmpty()) {
            Text("Attachable props (optional)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                props.take(12).forEach { prop ->
                    val selected = prop.id in selectedPropIds
                    FilterChip(
                        selected = selected,
                        enabled = !busy,
                        onClick = {
                            selectedPropIds = if (selected) selectedPropIds - prop.id else selectedPropIds + prop.id
                        },
                        label = { Text(prop.name.take(24)) },
                    )
                }
            }
            if (selectedPropIds.isNotEmpty()) {
                OutlinedButton(
                    onClick = { selectedPropIds.firstOrNull()?.let(onPlaceProp) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("workshop-place-selected-prop"),
                ) { Text("Place selected prop on avatar") }
            }
        }
        Text("Reason level", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InventReasonLevel.entries.forEach { level ->
                FilterChip(
                    selected = reason == level,
                    enabled = !busy,
                    onClick = { reasonKey = level.name },
                    label = { Text(level.label) },
                )
            }
        }
        OutlinedTextField(
            value = subject, onValueChange = { subject = it.take(120) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("workshop-subject"),
            label = { Text("Subject") }, placeholder = { Text("fox barista") },
        )
        OutlinedTextField(
            value = look, onValueChange = { look = it.take(200) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("workshop-look"),
            label = { Text("Overall look / colors") }, placeholder = { Text("peach fur, teal apron") },
        )
        Text("Style", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("cartoon", "chibi", "stylized", "toy-like").forEach { option ->
                FilterChip(
                    selected = style == option, enabled = !busy,
                    onClick = { style = option }, label = { Text(option) },
                )
            }
        }
        Text("Parts", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = head, onValueChange = { head = it.take(120) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("Head") }, placeholder = { Text("round muzzle, soft ears") })
        OutlinedTextField(value = body, onValueChange = { body = it.take(120) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("Body") }, placeholder = { Text("compact torso, apron") })
        OutlinedTextField(value = arms, onValueChange = { arms = it.take(120) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("Arms") }, placeholder = { Text("short arms, mitten paws") })
        OutlinedTextField(value = legs, onValueChange = { legs = it.take(120) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("Legs") }, placeholder = { Text("sturdy legs, soft boots") })
        OutlinedTextField(value = tail, onValueChange = { tail = it.take(120) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("Tail / trailing") }, placeholder = { Text("fluffy curling tail") })
        OutlinedTextField(
            value = freeform, onValueChange = { freeform = it.take(GeneratedAvatarForms.MAX_REQUEST) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("workshop-freeform"),
            label = { Text("Freeform detail") }, minLines = 2, maxLines = 5,
            supportingText = { Text("${composed.length}/${GeneratedAvatarForms.MAX_REQUEST} composed") },
        )
        Button(
            onClick = { onInvent(composed, reason) },
            enabled = canGo,
            modifier = Modifier.fillMaxWidth().testTag("workshop-invent"),
        ) { Text(if (busy) "Dispensing form…" else "Dispense new form") }
        if (wearing != null) {
            OutlinedButton(
                onClick = { onRefine(composed, reason) },
                enabled = canGo,
                modifier = Modifier.fillMaxWidth().testTag("workshop-refine"),
            ) { Text("Refine current (${wearing.name})") }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButtonish(onClick = onCancel, label = "Cancel")
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TextButtonish(onClick: () -> Unit, label: String) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

object AvatarWorkshopPrompt {
    fun build(
        subject: String,
        look: String,
        style: String,
        head: String,
        body: String,
        arms: String,
        legs: String,
        tail: String,
        freeform: String,
    ): String {
      if (listOf(subject, look, head, body, arms, legs, tail, freeform).all { it.isBlank() }) return ""
      return buildString {
        val who = subject.trim().ifBlank { "companion form" }
        append(who)
        if (style.isNotBlank()) append(", ").append(style.trim()).append(" style")
        if (look.isNotBlank()) append(", ").append(look.trim())
        fun part(label: String, value: String) {
            value.trim().takeIf { it.isNotEmpty() }?.let {
                append(", ").append(label.lowercase()).append(" ").append(it)
            }
        }
        part("Head", head); part("Body", body); part("Arms", arms); part("Legs", legs); part("Tail", tail)
        freeform.trim().takeIf { it.isNotEmpty() }?.let { append(", ").append(it) }
      }.take(GeneratedAvatarForms.MAX_REQUEST).trimEnd(',', ' ')
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropGeneratePanel(
    busy: Boolean,
    message: String?,
    isError: Boolean,
    onInvent: (prompt: String, category: String, detail: String, reason: InventReasonLevel) -> Unit,
    onCancel: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(GeneratedPropCategory.HELD.name) }
    var reasonKey by rememberSaveable { mutableStateOf(InventReasonLevel.BALANCED.name) }
    val reason = InventReasonLevel.fromKey(reasonKey)
    val canGo = prompt.isNotBlank() && !busy

    Column(Modifier.testTag("prop-generate"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Describe a prop for Lumen. The machine invents a small vector drawing and saves it to your prop library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Category", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeneratedPropCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category == cat.name, enabled = !busy,
                    onClick = { category = cat.name },
                    label = { Text(cat.name.lowercase()) },
                )
            }
        }
        Text("Reason level", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InventReasonLevel.entries.forEach { level ->
                FilterChip(
                    selected = reason == level, enabled = !busy,
                    onClick = { reasonKey = level.name },
                    label = { Text(level.label) },
                )
            }
        }
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it.take(GeneratedPropForms.MAX_REQUEST) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("prop-prompt"),
            label = { Text("What prop should we dispense?") },
            placeholder = { Text("A tiny paper umbrella") },
            minLines = 2, maxLines = 4,
            supportingText = { Text("${prompt.length}/${GeneratedPropForms.MAX_REQUEST}") },
        )
        OutlinedTextField(
            value = detail, onValueChange = { detail = it.take(240) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("prop-detail"),
            label = { Text("Optional detail") },
            placeholder = { Text("Peach paper, thin wooden handle") },
        )
        Button(
            onClick = { onInvent(prompt.trim(), category, detail.trim(), reason) },
            enabled = canGo,
            modifier = Modifier.fillMaxWidth().testTag("prop-dispense"),
        ) { Text(if (busy) "Dispensing prop…" else "Dispense prop") }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) { Text("Open prop library") }
    }
}

@Composable
private fun PropLibraryPanel(
    props: List<GeneratedPropForm>,
    equippedId: String?,
    onUse: (String) -> Unit,
    onPutAway: () -> Unit,
    onDelete: (String) -> Unit,
    onPlace: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("prop-library"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (props.isEmpty()) {
            item {
                Text(
                    "No props yet. Dispense one from the prop machine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(props, key = { it.id }) { prop ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val painter = remember { GeneratedAvatarPainter() }
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(140.dp).testTag("prop-preview:${prop.id}")) {
                            painter.drawProp(this, prop, LumenFrame(), 0f, center, minOf(size.width, size.height), preview = true)
                        }
                        Text(prop.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${prop.category.name.lowercase()} · ${prop.description.take(120)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (prop.useHint.isNotBlank()) {
                            Text("Use: ${prop.useHint}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (equippedId == prop.id) {
                            OutlinedButton(onClick = onPutAway) { Text("Put away") }
                        } else {
                            Button(onClick = { onUse(prop.id) }) { Text("Use with floating avatar") }
                        }
                        OutlinedButton(onClick = { onDelete(prop.id) }) { Text("Remove from library") }
                        Text(
                            "Attach @ (${"%.2f".format(prop.anchorX)}, ${"%.2f".format(prop.anchorY)}) · scale ${"%.2f".format(prop.attachScale)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onPlace(prop.id) }) { Text("Place on avatar") }
                            OutlinedButton(onClick = { onDelete(prop.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioArchivePanel(
    characters: List<com.example.meowmba.core.database.CharacterEntity>,
    artifacts: List<File>,
    onOpenArtifact: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item { Text("Characters", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary) }
        if (characters.isEmpty()) {
            item {
                Text(
                    "No saved characters yet. Say \"remember this character\" and describe them — comics will reuse the design.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(characters, key = { it.name }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, fontSize = 16.sp)
                        Text(
                            c.sheet.take(140) + if (c.sheet.length > 140) "…" else "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Made for you", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
        if (artifacts.isEmpty()) {
            item {
                Text(
                    "Nothing made yet. Try \"make a comic about…\" or \"write a YouTube script for…\".",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(artifacts, key = { it.absolutePath }) { f ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { onOpenArtifact(f) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(f.nameWithoutExtension.replace('_', ' '), fontSize = 15.sp)
                        Text(
                            "${f.parentFile?.name ?: ""} · ${File(f.path).length() / 1024} KB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimationGeneratePanel(
    busy: Boolean,
    message: String?,
    isError: Boolean,
    props: List<GeneratedPropForm>,
    savedNames: List<String>,
    onInvent: (prompt: String, selectedPropNames: List<String>, durationHintSec: Int, intensity: Float, fresh: Boolean) -> Unit,
    onCancel: () -> Unit,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var durationSec by rememberSaveable { mutableStateOf(6f) }
    var intensity by rememberSaveable { mutableStateOf(0.7f) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val canGo = prompt.isNotBlank() && !busy

    Column(Modifier.testTag("animation-generate"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Compose a short native animation with your configured AI. Library props guide the idea; only supported native prop shapes are animated, not exact copies of custom props.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(500) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("animation-prompt"),
            label = { Text("What should Lumen perform?") },
            placeholder = { Text("A tiny fishing trip") },
            minLines = 2,
            maxLines = 4,
            supportingText = { Text("${prompt.length}/500") },
        )
        Text("Duration ~${durationSec.toInt()}s", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = durationSec,
            onValueChange = { durationSec = it },
            valueRange = 4f..10f,
            steps = 5,
            modifier = Modifier.testTag("animation-duration"),
        )
        Text("Motion intensity ${"%.0f".format(intensity * 100)}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = intensity,
            onValueChange = { intensity = it },
            valueRange = 0.2f..1f,
            modifier = Modifier.testTag("animation-intensity"),
        )
        Text("Props to include", style = MaterialTheme.typography.titleSmall)
        if (props.isEmpty()) {
            Text(
                "No dispensed props yet — invent still works with native kinds (ball, star, fishing…).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                props.take(16).forEach { prop ->
                    val on = prop.name in selected
                    FilterChip(
                        selected = on,
                        enabled = !busy,
                        onClick = {
                            selected = if (on) selected - prop.name else selected + prop.name
                        },
                        label = { Text(prop.name.take(22)) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = fresh, onCheckedChange = { fresh = it }, enabled = !busy)
            Text("Force fresh invent (skip saved reuse)", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                onInvent(prompt.trim(), selected.toList(), durationSec.toInt(), intensity, fresh)
            },
            enabled = canGo,
            modifier = Modifier.fillMaxWidth().testTag("animation-dispense"),
        ) { Text(if (busy) "Inventing animation…" else "Invent animation") }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refresh library") }
        }
        if (savedNames.isNotEmpty()) {
            Text("Saved animations", style = MaterialTheme.typography.titleSmall)
            savedNames.forEach { name ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { onPlay(name) }) { Text("Play") }
                        OutlinedButton(onClick = { onDelete(name) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
