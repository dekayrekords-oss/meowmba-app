package com.example.meowmba.ui.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Checkbox whose label toggles it too (and reads as one control); a bare Checkbox ignored label taps. */
@Composable
fun CheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier, label: @Composable RowScope.() -> Unit) {
    Row(modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        label()
    }
}
