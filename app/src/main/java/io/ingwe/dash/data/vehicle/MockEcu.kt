package io.ingwe.dash.data.vehicle

import kotlin.math.roundToInt

/**
 * The reverse of the signal formulas: turns a [VehicleState] into the raw Mode 01
 * bytes a real engine ECU would send. Feeds [io.ingwe.dash.data.transport.MockTransport]
 * in the app's "Mock ELM" mode so the demo throttle drives a pretend ECU, and every
 * value on screen has made the full round trip through the real polling pipeline.
 */
object MockEcu {
    fun encode(s: VehicleState, pid: Int): ByteArray? = when (pid) {
        0x04 -> u8(s.engineLoadPct * 255f / 100f)
        0x05 -> u8(s.coolantC + 40f)
        0x0B -> u8(s.boostKPa)
        0x0C -> u16(s.rpm * 4f)
        0x0D -> u8(s.speedKmH)
        0x0F -> u8(s.intakeC + 40f)
        0x10 -> u16(s.mafGs * 100f)
        0x23 -> u16(s.railBar * 10f)
        0x2C -> u8(s.egrPct * 255f / 100f)
        0x2F -> u8(s.fuelLevelPct * 255f / 100f)
        0x33 -> u8(s.baroKPa)
        0x42 -> u16(s.batteryV * 1000f)
        0x46 -> u8(s.ambientC + 40f)
        0x5C -> u8(s.oilC + 40f)
        0x5E -> u16(s.fuelRateLh / 0.05f)
        0x62 -> u8(s.torqueNm / 5.8f + 125f)
        0x70 -> u16(s.targetBoostKPa / 0.03125f)?.let { byteArrayOf(0x03, it[0], it[1], 0, 0, 0, 0, 0, 0, 0) }
        0x71 -> u8(s.vgtPct * 255f / 100f)?.let { byteArrayOf(0x03, it[0], it[0], 0, 0, 0) }
        0x77 -> u8(s.chargeAirC + 40f)?.let { byteArrayOf(0x01, it[0], 0, 0, 0) }
        0x78 -> u16((s.egtC + 40f) * 10f)?.let { byteArrayOf(0x01, it[0], it[1], 0, 0, 0, 0, 0, 0) }
        0xA6 -> if (s.odometerKm.isNaN()) null else (s.odometerKm * 10f).toLong().let { v -> byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()) }
        else -> null
    }

    private fun u8(v: Float): ByteArray? = if (v.isNaN()) null else byteArrayOf(v.roundToInt().coerceIn(0, 255).toByte())
    private fun u16(v: Float): ByteArray? {
        if (v.isNaN()) return null
        val i = v.roundToInt().coerceIn(0, 0xFFFF)
        return byteArrayOf((i ushr 8).toByte(), i.toByte())
    }
}
