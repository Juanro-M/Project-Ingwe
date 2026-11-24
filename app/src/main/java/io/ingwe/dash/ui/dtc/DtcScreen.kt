package io.ingwe.dash.ui.dtc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Diagnostic Trouble Code (DTC) severity level.
 */
enum class DtcSeverity(val label: String, val color: Color) {
    CONFIRMED("CONFIRMED", IngweError),
    PENDING("PENDING", IngweWarning),
    PERMANENT("PERMANENT", Color(0xFFFF5252)),
    INFO("INFO", Color(0xFF00E5FF))
}

/**
 * Represents a single Diagnostic Trouble Code with ECU context and freeze-frame parameters.
 */
data class DtcItem(
    val code: String,
    val title: String,
    val system: String,
    val description: String,
    val severity: DtcSeverity,
    val ecu: String = "0x7E0 (ECM)",
    val freezeFrame: Map<String, String> = emptyMap(),
    val commonCauses: List<String> = emptyList()
)

/**
 * Comprehensive Diagnostic Trouble Code (DTC) screen for VW Amarok.
 * Provides ECU fault code scanning (Mode 03/07), code clearing (Mode 04),
 * freeze frame inspection, and troubleshooting hints.
 */
@Composable
fun DtcScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var expandedCode by remember { mutableStateOf<String?>(null) }

    // Initial realistic Amarok TDI diagnostic codes
    val troubleCodes = remember {
        mutableStateListOf(
            DtcItem(
                code = "P0299",
                title = "Turbocharger Underboost Condition",
                system = "Boost & Induction",
                description = "Manifold absolute pressure is significantly lower than requested boost target by ECM.",
                severity = DtcSeverity.CONFIRMED,
                freezeFrame = mapOf(
                    "Engine Speed" to "2,840 RPM",
                    "Target Boost" to "2.10 BAR (30.5 PSI)",
                    "Actual Boost" to "1.42 BAR (20.6 PSI)",
                    "Engine Load" to "78 %",
                    "Coolant Temp" to "88 °C"
                ),
                commonCauses = listOf(
                    "Split or loose turbo intercooler boost hose",
                    "Faulty N75 boost control solenoid valve or vacuum line leak",
                    "Sticky variable turbine geometry (VTG/VNT) vanes or actuator",
                    "Exhaust gas recirculation (EGR) stuck partially open"
                )
            ),
            DtcItem(
                code = "P0401",
                title = "EGR Flow Insufficient Detected",
                system = "Exhaust Gas Recirculation",
                description = "Exhaust gas recirculation flow rate measured across differential pressure is below specification.",
                severity = DtcSeverity.CONFIRMED,
                freezeFrame = mapOf(
                    "Engine Speed" to "1,750 RPM",
                    "Vehicle Speed" to "68 km/h",
                    "MAF Actual" to "480 mg/stroke",
                    "EGR Duty" to "65 %"
                ),
                commonCauses = listOf(
                    "Carbon buildup inside EGR valve passage or cooler",
                    "EGR cooler bypass flap stuck or leaking",
                    "Faulty MAF or EGR differential pressure sensor"
                )
            ),
            DtcItem(
                code = "P2002",
                title = "DPF Efficiency Below Threshold Bank 1",
                system = "Exhaust Aftertreatment",
                description = "Diesel particulate filter differential pressure indicates insufficient soot trapping or ash saturation.",
                severity = DtcSeverity.PENDING,
                freezeFrame = mapOf(
                    "Soot Mass (Calc)" to "24.5 g",
                    "Differential Press" to "14 mbar",
                    "Exhaust Gas Temp" to "310 °C"
                ),
                commonCauses = listOf(
                    "Incomplete active regeneration cycles due to frequent short trips",
                    "Faulty DPF differential pressure sensor hoses (pinched or cracked)",
                    "High ash accumulation nearing DPF service limit"
                )
            )
        )
    }

    val filteredCodes = when (selectedFilter) {
        "STORED" -> troubleCodes.filter { it.severity == DtcSeverity.CONFIRMED || it.severity == DtcSeverity.PERMANENT }
        "PENDING" -> troubleCodes.filter { it.severity == DtcSeverity.PENDING }
        else -> troubleCodes
    }

    // Confirmation dialog before clearing codes
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "CLEAR ALL FAULT CODES?",
                    color = IngweError,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will issue OBD-II Mode 04 to reset stored ECU trouble codes, freeze-frame data, and extinguish the Check Engine Light (MIL). Are you sure?",
                    color = IngweOnSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        troubleCodes.clear()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IngweError)
                ) {
                    Text("YES, CLEAR ALL", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("CANCEL", color = IngweOnSurface)
                }
            },
            containerColor = Color(0xFF181B22)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IngweBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ── Top Bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Back to dashboard
                    Surface(
                        color = Color(0xFF1E222A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { navController.popBackStack() }
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "BACK TO DASHBOARD",
                            color = IngwePrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "DIAGNOSTIC TROUBLE CODES (DTC)",
                            color = IngweOnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "ECU: 0x7E0 (Bosch EDC17 / MD1 Amarok TDI)",
                            color = IngweOnSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Check engine status badge
                val hasErrors = troubleCodes.isNotEmpty()
                Surface(
                    color = if (hasErrors) IngweError.copy(alpha = 0.2f) else IngweSecondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (hasErrors) IngweError else IngweSecondary
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (hasErrors) IngweError else IngweSecondary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasErrors) "MIL ACTIVE: ${troubleCodes.size} FAULTS" else "MIL OFF: NO FAULTS",
                            color = if (hasErrors) IngweError else IngweSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = IngweOutline)
            Spacer(Modifier.height(12.dp))

            // ── Scanning & Control Bar ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Scan button
                    Button(
                        onClick = {
                            if (!isScanning) {
                                isScanning = true
                                scanProgress = 0f
                                coroutineScope.launch {
                                    for (i in 1..10) {
                                        delay(120)
                                        scanProgress = i / 10f
                                    }
                                    isScanning = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IngwePrimary),
                        enabled = !isScanning,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isScanning) "SCANNING ECU... ${(scanProgress * 100).toInt()}%" else "SCAN ECU FOR CODES",
                            color = IngweOnPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Clear codes button
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        enabled = troubleCodes.isNotEmpty() && !isScanning,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IngweError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IngweError.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CLEAR DTCs (MODE 04)", fontWeight = FontWeight.Bold)
                    }
                }

                // Filter tabs
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL", "STORED", "PENDING").forEach { tab ->
                        val selected = selectedFilter == tab
                        Surface(
                            color = if (selected) IngwePrimary.copy(alpha = 0.25f) else Color(0xFF16181D),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) IngwePrimary else Color(0xFF262A33)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { selectedFilter = tab }
                        ) {
                            Text(
                                text = tab,
                                color = if (selected) IngwePrimary else IngweOnSurface.copy(alpha = 0.7f),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = IngwePrimary,
                    trackColor = Color(0xFF1E222A)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── DTC List ───────────────────────────────────────────────────────
            if (filteredCodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111317)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ALL SYSTEMS NORMAL",
                            color = IngweSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "No diagnostic trouble codes detected in ECU memory.",
                            color = IngweOnSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCodes) { item ->
                        DtcCard(
                            dtc = item,
                            isExpanded = expandedCode == item.code,
                            onToggleExpand = {
                                expandedCode = if (expandedCode == item.code) null else item.code
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcCard(
    dtc: DtcItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282D39)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // DTC Code badge
                    Surface(
                        color = dtc.severity.color.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, dtc.severity.color),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = dtc.code,
                            color = dtc.severity.color,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = dtc.title,
                            color = IngweOnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${dtc.system} • ${dtc.ecu}",
                            color = IngweOnSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = dtc.severity.color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = dtc.severity.label,
                        color = dtc.severity.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = dtc.description,
                color = IngweOnSurface.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Expanded view with Freeze Frame & Diagnostics
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color(0xFF262B36))
                    Spacer(Modifier.height(10.dp))

                    // Freeze Frame Data
                    if (dtc.freezeFrame.isNotEmpty()) {
                        Text(
                            text = "SNAPSHOT FREEZE-FRAME DATA:",
                            color = IngwePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dtc.freezeFrame.entries.forEach { (key, value) ->
                                Surface(
                                    color = Color(0xFF0F1116),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF20242D)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = key,
                                            color = IngweOnSurface.copy(alpha = 0.5f),
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = value,
                                            color = IngweOnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Common Causes
                    if (dtc.commonCauses.isNotEmpty()) {
                        Text(
                            text = "COMMON AMAROK CAUSES & REMEDIES:",
                            color = IngweWarning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        dtc.commonCauses.forEach { cause ->
                            Text(
                                text = "• $cause",
                                color = IngweOnSurface.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
