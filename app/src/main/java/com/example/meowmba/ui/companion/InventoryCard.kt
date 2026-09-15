package com.example.meowmba.ui.companion

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.inventory.InventoryItem

/**
 * The item the conversation is about, with what you can do to it.
 *
 * This exists because "which one?" is the real failure mode of talking to a computer about
 * your own possessions. The companion pulls the record up, you can see it is the right
 * thing, and the next action is a button rather than another sentence that might be
 * misheard. Renaming and pricing open a field in place — those are the two that need exact
 * values, and dictating "SM58" to a speech recognizer is a losing game.
 */
@Composable
fun InventoryCard(
    item: InventoryItem,
    onAction: (op: String, args: Map<String, String>) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which inline editor is open, if any: "name", "price", or null.
    var editing by remember(item.id) { mutableStateOf<String?>(null) }
    var draft by remember(item.id) { mutableStateOf("") }
    var confirmingDelete by remember(item.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerLow),
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle(item),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                StatusPill(item)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val money = listOfNotNull(
                item.askingPrice?.let { "Asking $${InventoryItem.trimNumber(it)}" },
                item.marketValue?.let { "Worth ~$${InventoryItem.trimNumber(it)}" },
                item.pricePaid?.let { "Paid $${InventoryItem.trimNumber(it)}" },
            )
            if (money.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(money.joinToString("  ·  "), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }

            when (editing) {
                "name" -> InlineField(
                    label = "New name",
                    value = draft,
                    onValue = { draft = it },
                    onCancel = { editing = null },
                    onCommit = {
                        if (draft.isNotBlank()) {
                            onAction("update", mapOf("id" to item.id, "new_name" to draft))
                        }
                        editing = null
                    },
                )

                "price" -> InlineField(
                    label = "Asking price",
                    value = draft,
                    numeric = true,
                    onValue = { draft = it },
                    onCancel = { editing = null },
                    onCommit = {
                        if (draft.isNotBlank()) {
                            onAction(
                                "list_for_sale",
                                mapOf("id" to item.id, "price" to draft),
                            )
                        }
                        editing = null
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            if (confirmingDelete) {
                // Deleting a record of something you own is not undoable from here, so it
                // asks once, in place, instead of relying on the model to have asked.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Delete ${item.name} from the inventory?",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmingDelete = false }) {
                        Text("Keep", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        confirmingDelete = false
                        onAction("delete", mapOf("id" to item.id))
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChip("Rename") { draft = item.name; editing = "name" }
                    if (item.status == "sold") {
                        ActionChip("Un-sell") {
                            onAction("update", mapOf("id" to item.id, "status" to "inventory"))
                        }
                    } else if (item.forSale || item.status == "listed") {
                        ActionChip("Sold") { onAction("mark_sold", mapOf("id" to item.id)) }
                        ActionChip("Unlist") { onAction("unlist", mapOf("id" to item.id)) }
                    } else {
                        ActionChip("Sell") {
                            draft = item.marketValue?.let { InventoryItem.trimNumber(it) } ?: ""
                            editing = "price"
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChip("Open app", onClick = onOpenApp)
                    ActionChip("Delete", danger = true) { confirmingDelete = true }
                }
            }
        }
    }
}

private fun subtitle(item: InventoryItem): String = buildList {
    if (item.category.isNotBlank()) add(item.category)
    if (item.condition.isNotBlank()) add(item.condition)
    if (item.location.isNotBlank()) add("in ${item.location}")
    if (item.quantity != 1.0) add("×${InventoryItem.trimNumber(item.quantity)}")
}.joinToString(" · ")

@Composable
private fun StatusPill(item: InventoryItem) {
    val (label, colour) = when {
        item.status == "sold" -> "SOLD" to MaterialTheme.colorScheme.onSurfaceVariant
        item.forSale || item.status == "listed" -> "FOR SALE" to MaterialTheme.colorScheme.primary
        else -> "OWNED" to MaterialTheme.colorScheme.secondary
    }
    Text(
        text = label,
        color = colour,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colour.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActionChip(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Text(
        text = label,
        color = tint,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun InlineField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    numeric: Boolean = false,
) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        TextButton(onClick = onCommit) {
            Text("Save", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
        }
    }
}
