package com.example.meowmba.ui.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.northstar.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun NorthStarLine(view: StarView?, onOpen: () -> Unit) {
    val c = LocalFlowColors.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).clickable(onClick = onOpen).padding(vertical = 6.dp).testTag("north_star_summary")) {
        BasicText(view?.let { "NORTH STAR → ${it.star.title}" } ?: "NORTH STAR · What are you working toward?",
            style = FlowType.label.copy(color = c.accent))
        if (view != null) {
            BasicText(view.nextStep, Modifier.padding(top = 5.dp), style = FlowType.itemDetail.copy(color = c.ink), maxLines = 2)
            BasicText(view.momentum, Modifier.padding(top = 4.dp), style = FlowType.itemDetail.copy(color = c.ink2), maxLines = 1)
        }
    }
}

/** Goal, evidence, edit and delete all have one visible home. No hidden autonomous action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NorthStarSheet(runtime: NorthStarRuntime, onClose: () -> Unit, onOpenSource: (String) -> Unit = {}) {
    val views by runtime.views.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var horizon by remember { mutableStateOf("Someday") }
    var steps by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<NorthStar?>(null) }
    fun edit(star: NorthStar?) {
        editId = star?.id; title = star?.title.orEmpty(); why = star?.why.orEmpty()
        horizon = star?.horizon ?: "Someday"; steps = star?.ingredients()?.joinToString("\n") { it.text }.orEmpty()
        error = null; editing = true
    }
    fun action(block: suspend () -> Unit) { scope.launch {
        try { block(); error = null } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "That change couldn't be saved. Please try again." }
    } }
    Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
        .imePadding().padding(24.dp).testTag("north_star_sheet")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("North Stars", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text("The life you're building, one step at a time. Goals and connections stay on this phone.", style = MaterialTheme.typography.bodyMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (editing) {
            OutlinedTextField(title, { title = it.take(240) }, label = { Text("What do you want to achieve?") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("north_star_title"))
            OutlinedTextField(why, { why = it.take(600) }, label = { Text("Why it matters to you (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(horizon, { horizon = it.take(120) }, label = { Text("Horizon · this year, someday…") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(steps, { steps = it.take(4000) }, label = { Text("Ingredients / small steps · one per line") },
                minLines = 3, modifier = Modifier.fillMaxWidth().testTag("north_star_steps"))
            TextButton(onClick = { steps = NorthStarPolicy.suggestedIngredients(title).joinToString("\n") { it.text } }) { Text("Suggest starting steps") }
            Text("These are starting ideas. Edit them to fit your goal.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !saving && title.trim().length >= 3, onClick = {
                    saving = true
                    action { try { runtime.save(editId, title, why, horizon, steps.lines()); editing = false } finally { saving = false } }
                }, modifier = Modifier.testTag("north_star_save")) { Text("Save goal") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else Button(onClick = { edit(null) }, modifier = Modifier.padding(top = 12.dp).testTag("north_star_add")) { Text("Add a North Star") }
        if (views.isEmpty() && !editing) Text("Try “My dream is to play in Europe with my band”, or add a goal above.", Modifier.padding(vertical = 16.dp))
        views.forEachIndexed { index, view ->
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(view.star.title, style = MaterialTheme.typography.titleLarge)
            Text(view.star.horizon, style = MaterialTheme.typography.labelLarge)
            if (view.star.why.isNotBlank()) Text(view.star.why, Modifier.padding(top = 4.dp))
            Text(view.momentum, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
            Text("One next step: ${view.nextStep}", Modifier.padding(top = 8.dp))
            Text("This week", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            Text(view.weeklyReview, style = MaterialTheme.typography.bodyMedium)
            Text("What makes it real", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            view.star.ingredients().forEach { ingredient ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(ingredient.doneAt != null, onCheckedChange = { done -> action { runtime.markIngredient(view.star.id, ingredient.text, done) } })
                    Text(ingredient.text, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { edit(view.star) }) { Text("Edit") }
                if (index != 0) TextButton(onClick = { action { runtime.pin(view.star.id) } }) { Text("Pin on Desk") }
                else Text("Pinned on Desk", Modifier.padding(vertical = 13.dp), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { delete = view.star }) { Text("Delete") }
            }
            Text("Recent connections · up to 500 across your goals", style = MaterialTheme.typography.titleMedium)
            if (view.links.isEmpty()) Text("No connections yet. Relevant messages, events and things you tell Meow can appear here.", style = MaterialTheme.typography.bodySmall)
            view.links.forEach { link ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(DateTimeFormatter.ofPattern("MMM d · h:mm a").format(Instant.ofEpochMilli(link.at).atZone(ZoneId.systemDefault())) +
                        " · " + when (link.kind) { "PROGRESS" -> "Reported progress"; "OPPORTUNITY" -> "Possible opening"; "MISSED" -> "Reported missed opening"; else -> "Connection" }, style = MaterialTheme.typography.labelSmall)
                    Text(link.text, style = MaterialTheme.typography.bodyMedium)
                    Text(link.reason, style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (link.sourceKey.startsWith("flow:") || link.sourceKey.startsWith("task:")) TextButton(onClick = {
                            onOpenSource(if (link.sourceKey.startsWith("flow:")) link.sourceKey.removePrefix("flow:") else link.sourceKey)
                        }) { Text(if (link.sourceKey.startsWith("task:")) "Open task result" else "Open item") }
                        TextButton(onClick = { action { runtime.dismiss(link.id) } }) { Text("Remove connection") }
                    }
                }
            }
        }
    }
    delete?.let { star -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete this North Star?") },
        text = { Text("“${star.title}” and its saved connections will be removed. Your original messages and other app data stay saved.") },
        confirmButton = { TextButton(onClick = { action { runtime.remove(star.id); delete = null } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Keep") } }) }
}
