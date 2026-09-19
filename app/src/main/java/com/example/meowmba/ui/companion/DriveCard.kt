package com.example.meowmba.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset as GeomOffset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.meowmba.core.car.TripStats
import com.example.meowmba.ui.theme.Surface
import java.util.Locale

/**
 * Compact driving HUD. Source diagnostics and trip/camera details are deliberate
 * inspection, rather than a large permanent panel covering the avatar. Real OBD
 * warnings stay visible while collapsed; crash and road-hazard alerts keep their
 * separate existing presentation and are not classified as source availability.
 */
@Composable
fun DriveCard(
    speedMph: Float,
    gForce: Float,
    reason: String,
    manual: Boolean,
    trip: TripStats?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    roadStatus: String? = null,
    driverCamera: (@Composable () -> Unit)? = null,
    obdLine: String? = null,
    vehicleLine: String? = null,
    cameraLabels: List<String> = emptyList(),
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val sourceSummary = driveRoadSourceSummary(roadStatus)
    val warning = driveVehicleWarning(obdLine)
    Column(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(18.dp),
            )
            .testTag("drive-hud")
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BoxWithConstraints {
            val showLandscapeSource = maxWidth >= 440.dp && LocalDensity.current.fontScale <= 1.3f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (landscape) {
                    DriveSpeed(speedMph, Modifier.weight(1f))
                    if (showLandscapeSource) {
                        Text(sourceSummary, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        DriveSpeed(speedMph)
                        Text(sourceSummary, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { detailsOpen = true }, modifier = Modifier.testTag("drive-details-action")) {
                    Icon(Icons.Default.Info, contentDescription = "Drive details. $sourceSummary",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("drive-dismiss-action")) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss driving display",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        warning?.let {
            Text(it, modifier = Modifier.testTag("drive-vehicle-warning"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    if (detailsOpen) {
        DriveDetails(
            speedMph, gForce, reason, manual, trip, roadStatus, driverCamera, obdLine,
            vehicleLine, cameraLabels, onClose = { detailsOpen = false },
        )
    }
}

@Composable
private fun DriveSpeed(speedMph: Float, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append(driveSpeedLabel(speedMph).substringBefore(' '))
            withStyle(SpanStyle(fontSize = 12.sp)) { append(" mph") }
        },
        modifier = modifier.testTag("drive-speed"),
        fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveDetails(
    speedMph: Float, gForce: Float, reason: String, manual: Boolean, trip: TripStats?,
    roadStatus: String?, driverCamera: (@Composable () -> Unit)?, obdLine: String?,
    vehicleLine: String?, cameraLabels: List<String>, onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight).testTag("drive-details-panel"), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Drive details", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close drive details")
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (manual) "Drive mode · Manual" else "Driving · ${reason.ifBlank { "detected" }}",
                            style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DriveStat("Speed", driveSpeedLabel(speedMph))
                            DriveStat("G-force", "%.1f g".format(Locale.US, gForce))
                            trip?.let {
                                DriveStat("Trip", "%.1f mi".format(Locale.US, it.distanceMiles))
                                DriveStat("Time", "${it.durationMinutes}m")
                                DriveStat("Top", driveSpeedLabel(it.maxSpeedMph))
                            } ?: DriveStat("Trip", "just started")
                        }
                        roadStatus?.takeIf { it.isNotBlank() }?.let {
                            Text("Road sources", style = MaterialTheme.typography.titleSmall)
                            Text(it, modifier = Modifier.testTag("drive-road-source-details"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        vehicleLine?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        obdLine?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (cameraLabels.isNotEmpty()) {
                            Text("Cameras: ${cameraLabels.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium)
                        }
                        driverCamera?.let { camera ->
                            Box(Modifier.fillMaxWidth().height(110.dp).background(Color.Black, RoundedCornerShape(16.dp))) { camera() }
                        }
                    }
                }
            }
        }
    }
}

/** Red-tinted alert card shown when the detector thinks there was a collision. */
@Composable
fun CrashAlertCard(
    message: String,
    onTextContact: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF7F1D1D).copy(alpha = 0.92f),
                        Color(0xFF7F1D1D).copy(alpha = 0.72f),
                        Color(0xFF7F1D1D).copy(alpha = 0.0f),
                    ),
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Possible crash",
                    color = Color(0xFFFFE4E6),
                    style = shadowed(20.sp, FontWeight.SemiBold),
                )
                Text(
                    text = message,
                    color = Color(0xFFFFE4E6).copy(alpha = 0.92f),
                    style = shadowed(13.sp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("I'm OK", color = Color(0xFFFFE4E6), fontSize = 13.sp)
            }
            TextButton(onClick = onTextContact) {
                Text("Text my contact", color = Color(0xFFFFE4E6), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DriveStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

private fun shadowed(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight? = null) =
    TextStyle(
        fontSize = size,
        fontWeight = weight,
        shadow = Shadow(
            color = Color(0xCC000000),
            offset = GeomOffset(0f, 1.5f),
            blurRadius = 6f,
        ),
    )
