package io.ingwe.dash.data.signals

import kotlinx.serialization.Serializable

/**
 * A single OBD/UDS signal definition loaded from assets/signals.json.
 *
 * Adding a new signal requires ZERO code changes — just add an entry to
 * signals.json.
 *
 * Fields:
 *   id        — unique snake_case identifier, used as a key in the DB
 *   label     — human-readable name shown in the UI
 *   ecu       — target ECU address (e.g. "7E0")
 *   service   — OBD/UDS service hex byte (e.g. "01", "22")
 *   pid       — PID or DID hex string (e.g. "0C", "1000")
 *   formula   — arithmetic expression with A/B/C/D as byte variables
 *   unit      — display unit string (e.g. "rpm", "°C", "kPa")
 *   min/max   — gauge range for display
 *   warnMin/warnMax — optional threshold values that trigger a warning colour
 *   fastHz    — if true, scheduler polls at the fast rate (~10 Hz); else slow (~0.5 Hz)
 *   verified  — false = unverified (shown with a ⚠ badge in UI), true = confirmed on real ECU
 */
@Serializable
data class SignalDefinition(
    val id: String,
    val label: String,
    val ecu: String,
    val service: String,
    val pid: String,
    val formula: String,
    val unit: String,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val warnMin: Double? = null,
    val warnMax: Double? = null,
    val fastHz: Boolean = false,
    val verified: Boolean = false,
)
