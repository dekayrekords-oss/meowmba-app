package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.workflows.BusinessReadiness

/** Pure snapshot UI: rendering this card cannot create agents, jobs or grants. */
@Composable internal fun BusinessModeOverview(
    readiness: BusinessReadiness,
    onConfigureBrief: () -> Unit,
    onSchedulesPaused: (Boolean) -> Unit,
    onOpenFiles: (() -> Unit)? = null,
    onOpenBugReports: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your business workspace", style = MaterialTheme.typography.titleMedium)
        Text("${readiness.enabledAgents} enabled agents · ${readiness.activeJobs} active jobs · ${readiness.pendingApprovals} awaiting approval")
        Text("Local files: reports, project plans, exact CSV totals and reply drafts. Choose and test a model below before running jobs.", style = MaterialTheme.typography.bodySmall)
        Text(if (readiness.configuredServices == 0) "Service access: connect a service and grant its tools to an agent."
            else "Service access: ${readiness.configuredServices} configured services · ${readiness.grantedServiceTools} service tools granted · ${readiness.automaticReads} automatic reads.", style = MaterialTheme.typography.bodySmall)
        Text("Connection settings describe saved access; a job must still verify each live result.", style = MaterialTheme.typography.bodySmall)
        Text(if (readiness.configuredSchedules == 0) "Proactive work: no schedules configured."
            else "Proactive work: ${readiness.configuredSchedules} configured schedules · ${if (readiness.schedulesPaused) "paused" else "enabled"}.", style = MaterialTheme.typography.bodySmall)
        if (readiness.configuredSchedules > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pause new scheduled runs", modifier = Modifier.weight(1f))
                Switch(checked = readiness.schedulesPaused, onCheckedChange = onSchedulesPaused)
            }
            Text("Existing jobs keep their status. Stop an active job in Job history. Android controls background timing; the minimum interval is 15 minutes.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Every job keeps action results and approval decisions in Job history. Reports, review drafts and archived traces are in Files → business.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onConfigureBrief, modifier = Modifier.fillMaxWidth()) { Text("Configure daily briefing") }
        onOpenFiles?.let { open -> TextButton(onClick = open) { Text("Open business files") } }
        onOpenBugReports?.let { open -> TextButton(onClick = open) { Text("Review possible bug reports") } }
    } }
}
