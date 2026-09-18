package com.example.meowmba.ui.companion

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

/**
 * Local wall-clock chip for companion backgrounds. Uses the device clock
 * (so America/Los_Angeles users see whatever the phone is set to) and opens
 * the system Clock app on tap, with Samsung-friendly fallbacks.
 */
@Composable
fun CompanionWallClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val msToNextMinute = ((60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND))
                .coerceIn(250L, 60_000L)
            delay(msToNextMinute)
        }
    }
    val label = remember(now) {
        DateFormat.getTimeFormat(context).format(Date(now))
    }
    Box(
        modifier = modifier
            .testTag("companion-wall-clock")
            .semantics { contentDescription = "Current time $label. Open Clock app." }
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { openSystemClockApp(context) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            fontSize = 14.sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun openSystemClockApp(context: Context): Boolean {
    val attempts = buildList {
        add(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        add(Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        // Common clock packages (Samsung first).
        listOf(
            "com.sec.android.app.clockpackage",
            "com.samsung.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.oneplus.deskclock",
            "com.coloros.alarmclock",
        ).forEach { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                add(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        add(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage("com.sec.android.app.clockpackage")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    for (intent in attempts) {
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    return false
}
