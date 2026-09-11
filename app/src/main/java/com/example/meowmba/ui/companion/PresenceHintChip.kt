package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color(0xCC1B1F24),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Text(
            text = hint,
            color = Color.White,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 2,
        )
    }
}
