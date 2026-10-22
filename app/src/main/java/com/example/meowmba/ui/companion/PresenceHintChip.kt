package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.example.meowmba.ui.theme.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small on-screen presence / reflex banner. Shown when [hint] is non-null and
 * the caller has already checked feature flags.
 */
@Composable
fun PresenceHintChip(
    hint: String,
    modifier: Modifier = Modifier,
    onReview: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color(0xCC1B1F24),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = hint,
            color = Color.White,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 2,
        )
        onReview?.let { TextButton(onClick = it) { Text("Review", color = Color.White) } }
        onDismiss?.let { TextButton(onClick = it) { Text("Dismiss", color = Color.White) } }
        }
    }
}
