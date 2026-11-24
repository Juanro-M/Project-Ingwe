package io.ingwe.dash.ui.dtc

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import io.ingwe.dash.ui.theme.IngweAlert
import io.ingwe.dash.ui.theme.IngweCaution
import io.ingwe.dash.ui.theme.IngweTextSecondary

/** DTC status as reported by Mode 03 (stored), 07 (pending) and 0A (permanent). */
enum class DtcSeverity(val label: String) {
    CONFIRMED("Stored"),
    PENDING("Pending"),
    PERMANENT("Permanent"),
    INFO("Info");

    /** A getter (not a constructor value) so it follows the light/dark theme. */
    val color: Color get() = when (this) {
        CONFIRMED, PERMANENT -> IngweAlert
        PENDING -> IngweCaution
        INFO -> IngweTextSecondary
    }
}

/** A single Diagnostic Trouble Code with ECU context and freeze-frame parameters. */
data class DtcItem(
    val code: String,
    val title: String,
    val system: String,
    val description: String,
    val severity: DtcSeverity,
    val ecu: String = "7E0 · Engine",
    val freezeFrame: List<Pair<String, String>> = emptyList(),
    val commonCauses: List<String> = emptyList(),
)

/**
 * Shared fault-code state for the dashboard banner and the fault screen.
 * Lives at nav-host level so clearing codes on one screen is reflected on the other.
 *
 * M1: seeded with representative Amarok TDI codes. M5 replaces the seed with
 * Mode 03/07/0A results from the ELM driver.
 */
class FaultStore(initial: List<DtcItem> = SAMPLE_CODES) {
    val codes: SnapshotStateList<DtcItem> = mutableStateListOf<DtcItem>().apply { addAll(initial) }

    fun clearAll() = codes.clear()

    companion object {
        val SAMPLE_CODES = listOf(
            DtcItem(
                code = "P0299",
                title = "Turbocharger underboost",
                system = "Boost & induction",
                description = "Manifold pressure stayed well below the boost target requested by the ECM.",
                severity = DtcSeverity.CONFIRMED,
                freezeFrame = listOf(
                    "Engine speed" to "2,840 rpm",
                    "Target boost" to "2.10 bar",
                    "Actual boost" to "1.42 bar",
                    "Engine load" to "78 %",
                    "Coolant" to "88 °C",
                ),
                commonCauses = listOf(
                    "Split or loose intercooler boost hose",
                    "Faulty N75 boost control solenoid or vacuum line leak",
                    "Sticking VTG vanes or actuator",
                    "EGR valve stuck partially open",
                ),
            ),
            DtcItem(
                code = "P0401",
                title = "EGR flow insufficient",
                system = "Exhaust gas recirculation",
                description = "Measured EGR flow across the differential pressure sensor is below specification.",
                severity = DtcSeverity.CONFIRMED,
                freezeFrame = listOf(
                    "Engine speed" to "1,750 rpm",
                    "Vehicle speed" to "68 km/h",
                    "MAF actual" to "480 mg/str",
                    "EGR duty" to "65 %",
                ),
                commonCauses = listOf(
                    "Carbon build-up in the EGR valve or cooler",
                    "EGR cooler bypass flap stuck or leaking",
                    "Faulty MAF or EGR differential pressure sensor",
                ),
            ),
            DtcItem(
                code = "P0671",
                title = "Cylinder 1 glow plug circuit",
                system = "Glow system",
                description = "The glow plug control module saw an open circuit on cylinder 1 during pre-glow.",
                severity = DtcSeverity.PENDING,
                freezeFrame = listOf(
                    "Coolant" to "4 °C",
                    "Battery" to "12.1 V",
                    "Engine speed" to "0 rpm",
                ),
                commonCauses = listOf(
                    "Glow plug element burnt out (check resistance, normally ~0.6 Ω)",
                    "Loose or corroded glow plug connector",
                    "Wiring fault between plug and glow control module",
                ),
            ),
        )
    }
}
