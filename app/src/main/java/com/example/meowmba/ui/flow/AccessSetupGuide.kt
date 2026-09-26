package com.example.meowmba.ui.flow

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.meowmba.BuildConfig
import com.example.meowmba.core.access.MeowAccessibilityService
import com.example.meowmba.core.flow.sources.UsageSource

internal data class AccessSetupState(val calendarRead: Boolean, val calendarWrite: Boolean,
    val notifications: Boolean, val automation: Boolean, val usage: Boolean, val gmailInstalled: Boolean) {
    val calendarStatus get() = when {
        calendarRead && calendarWrite -> "Ready to read and change calendars"
        calendarRead -> "Read only — changes are not enabled"
        calendarWrite -> "Read access is still needed"
        else -> "Not enabled"
    }
    companion object {
        fun read(context: Context): AccessSetupState {
            fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            val listener = ComponentName(context, com.example.meowmba.core.alerts.RoadAlertListener::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                .orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).contains(listener)
            return AccessSetupState(granted(Manifest.permission.READ_CALENDAR), granted(Manifest.permission.WRITE_CALENDAR),
                enabled, MeowAccessibilityService.isEnabled(), UsageSource(context) { emptySet() }.hasAccess(),
                context.packageManager.getLaunchIntentForPackage("com.google.android.gm") != null)
        }
    }
}

/** Optional, user-driven setup. Returning from Android always rechecks real permission state. */
@Composable
internal fun AccessSetupGuide(onClose: () -> Unit, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AccessSetupState.read(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val refresh = { state = AccessSetupState.read(context); onChanged() }
    val currentRefresh by rememberUpdatedState(refresh)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        if (!state.calendarRead || !state.calendarWrite)
            message = "Calendar access is incomplete. If Android no longer asks, open App permissions below, choose Permissions → Calendar → Allow, then return here."
        else message = "Calendar access is enabled. Only writable calendars can be changed; an account or work profile may still restrict an event."
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) currentRefresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message = "That screen is unavailable on this phone. Open Android Settings manually and follow the steps above." }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("access_setup_guide"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Connect apps & tools", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text("Done") }
        }
        Text("Choose only what you want Meow to use. Each step explains its access; you approve Android's screens yourself. Return here to check the result. You can skip any step and revoke access later.")
        Text("1 · Calendar", style = MaterialTheme.typography.titleMedium)
        Text(state.calendarStatus, Modifier.testTag("calendar_access_status"))
        Text("Tap Allow calendar access, then allow Calendar in Android's prompt. Meow needs both read and write access to find the event and change it when you ask. In Flow, Comment → ‘this event is canceled’ → Cancel this occurrence changes only that event and checks the result.")
        Button(onClick = { message = null; permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
            enabled = !state.calendarRead || !state.calendarWrite) { Text("Allow calendar access") }
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) {
            Text("App permissions / denied access")
        }
        Text("If an event is missing: open your calendar app, select the right account/calendar and turn on sync. Read-only/shared calendars may not allow edits. Calendar access does not grant Gmail access.")
        HorizontalDivider()
        Text("2 · Gmail and email", style = MaterialTheme.typography.titleMedium)
        Text(if (state.gmailInstalled) "Gmail is installed; account sign-in is not verified." else "Gmail is not available to open on this phone.")
        Text("Open Gmail and sign in yourself. Allow Gmail notifications if you want new-mail Flow cards. Meow can open a prepared email draft; you review it and tap Send in the email app. Notification access below sees notification content, not your whole inbox. Optional on-screen control uses Accessibility; this is not a Gmail account/API connection.")
        OutlinedButton(enabled = state.gmailInstalled, onClick = {
            context.packageManager.getLaunchIntentForPackage("com.google.android.gm")?.let(::open)
        }) { Text("Open Gmail to sign in") }
        HorizontalDivider()
        Text("3 · Message notifications", style = MaterialTheme.typography.titleMedium)
        Text(if (state.notifications) "Meow notification access is enabled" else "Not enabled")
        Text("Open Notification access → choose Meow/Memba → Allow. Android may list the preview and Play apps separately: choose this copy. This lets Meow read incoming notification content, including messages and email previews. Reply opens the originating conversation; Comment stays with the Flow item and is not sent to the sender.")
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Open notification access") }
        HorizontalDivider()
        Text("4 · On-screen app tools (optional)", style = MaterialTheme.typography.titleMedium)
        Text(if (!BuildConfig.PHONE_AUTOMATION_ENABLED) "Unavailable in this build" else if (state.automation) "Meow's accessibility service is connected" else "Not connected")
        Text("Open Accessibility → Installed apps / Downloaded apps → Meow → enable the service and review Android's warning. It can read visible screen content and tap/type/scroll for approved phone tasks. It does not grant access to every app or account. Sign-ins, payments, permission prompts and protected actions still need you; sending and other consequential actions retain their confirmation.")
        if (BuildConfig.PHONE_AUTOMATION_ENABLED) OutlinedButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
            Text("Open accessibility settings")
        }
        HorizontalDivider()
        Text("5 · App context (optional)", style = MaterialTheme.typography.titleMedium)
        Text(if (state.usage) "Usage access enabled" else "Not enabled")
        Text("Open Usage access → Meow → Allow usage access. This helps Flow know which app you are using; it does not read Gmail messages or let Meow control apps.")
        OutlinedButton(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open usage access") }
        message?.let { Text(it, Modifier.testTag("access_setup_result")) }
        TextButton(onClick = { refresh(); message = "Access status refreshed. No task was run." }) { Text("Check access again") }
    }
}
