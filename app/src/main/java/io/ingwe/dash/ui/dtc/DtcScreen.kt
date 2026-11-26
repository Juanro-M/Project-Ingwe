package io.ingwe.dash.ui.dtc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.ingwe.dash.ui.components.ActionButton
import io.ingwe.dash.ui.components.ScreenHeader
import io.ingwe.dash.ui.components.gauges.SegmentedControl
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DtcFilter(val label: String) { ALL("All"), STORED("Stored"), PENDING("Pending") }

/**
 * Fault codes for the engine ECU: scan (Mode 03/07/0A), inspect freeze frames,
 * and clear (Mode 04, behind a confirmation).
 *
 * Read-only: Ingwe reads fault codes but never clears them (Mode 04 / UDS 0x14 are
 * permanently blocked). Clearing codes is left to a workshop tool.
 * M1: scan operates on [FaultStore] only.
 */
@Composable
fun DtcScreen(navController: NavController, faultStore: FaultStore) {
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var hasScanned by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DtcFilter.ALL) }
    var expandedCode by remember { mutableStateOf<String?>(null) }

    val codes = faultStore.codes
    val visible = when (filter) {
        DtcFilter.ALL -> codes.toList()
        DtcFilter.STORED -> codes.filter { it.severity == DtcSeverity.CONFIRMED || it.severity == DtcSeverity.PERMANENT }
        DtcFilter.PENDING -> codes.filter { it.severity == DtcSeverity.PENDING }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IngweBackground)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = "Fault codes",
            subtitle = "Engine ECU 7E0 · Bosch EDC17",
            onBack = { navController.popBackStack() },
        ) {
            ActionButton(
                text = if (isScanning) "Scanning… ${(scanProgress * 100).toInt()}%" else "Scan",
                primary = true,
                enabled = !isScanning,
                onClick = {
                    isScanning = true
                    scope.launch {
                        for (i in 1..10) {
                            delay(120)
                            scanProgress = i / 10f
                        }
                        isScanning = false
                        hasScanned = true
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SegmentedControl(
                options = DtcFilter.entries.map { it to it.label },
                selected = filter,
                onSelect = { filter = it },
            )
            Spacer(Modifier.width(16.dp))
            Text("Read-only · clear codes with a workshop tool", style = LabelStyle, color = IngweTextTertiary)
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    codes.isEmpty() -> "Check-engine light off"
                    else -> "Check-engine light on · ${codes.size} code${if (codes.size == 1) "" else "s"}" +
                        if (hasScanned) " · scanned just now" else ""
                },
                style = LabelStyle,
                color = if (codes.isEmpty()) IngweTextSecondary else IngweAlert,
            )
        }

        Spacer(Modifier.height(10.dp))
        if (isScanning) {
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = IngweAccent,
                trackColor = IngweHairline,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (codes.isEmpty()) "No fault codes" else "No ${filter.label.lowercase()} codes",
                        style = MaterialTheme.typography.headlineMedium,
                        color = IngweTextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (codes.isEmpty()) "The engine ECU reports nothing stored or pending." else "Try another filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IngweTextSecondary,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(IngweSurface),
            ) {
                items(visible, key = { it.code }) { dtc ->
                    DtcRow(
                        dtc = dtc,
                        expanded = expandedCode == dtc.code,
                        onToggle = { expandedCode = if (expandedCode == dtc.code) null else dtc.code },
                    )
                    if (dtc != visible.last()) HorizontalDivider(color = IngweHairline, modifier = Modifier.padding(horizontal = 18.dp))
                }
            }
        }
    }
}

@Composable
private fun DtcRow(dtc: DtcItem, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dtc.code, style = ReadoutMedium.copy(fontSize = 18.sp), color = IngweTextPrimary, modifier = Modifier.width(84.dp))
            Column(Modifier.weight(1f)) {
                Text(dtc.title, style = MaterialTheme.typography.titleMedium, color = IngweTextPrimary)
                Text("${dtc.system} · ${dtc.ecu}", style = LabelStyle, color = IngweTextTertiary)
            }
            Box(Modifier.size(6.dp).clip(CircleShape).background(dtc.severity.color))
            Spacer(Modifier.width(6.dp))
            Text(dtc.severity.label, style = LabelStyle, color = IngweTextSecondary)
            Spacer(Modifier.width(16.dp))
            Text(if (expanded) "Hide" else "Details", style = LabelStyle, color = IngweTextTertiary)
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 84.dp, top = 12.dp)) {
                Text(dtc.description, style = MaterialTheme.typography.bodyMedium, color = IngweTextSecondary)

                if (dtc.freezeFrame.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Freeze frame", style = LabelStyle, color = IngweTextTertiary)
                    Spacer(Modifier.height(8.dp))
                    dtc.freezeFrame.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            row.forEach { (label, value) ->
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = LabelStyle, color = IngweTextTertiary)
                                    Text(value, style = ReadoutSmall, color = IngweTextPrimary)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                if (dtc.commonCauses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Common causes", style = LabelStyle, color = IngweTextTertiary)
                    Spacer(Modifier.height(6.dp))
                    dtc.commonCauses.forEachIndexed { i, cause ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("${i + 1}", style = ReadoutSmall.copy(fontSize = 13.sp), color = IngweTextTertiary, modifier = Modifier.width(20.dp))
                            Text(cause, style = MaterialTheme.typography.bodyMedium, color = IngweTextSecondary)
                        }
                    }
                }
            }
        }
    }
}
