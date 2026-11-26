package io.ingwe.dash.data.service

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Calendar

/** One visit to the workshop (or a DIY job). Dates are ISO "yyyy-MM-dd"; null = not entered yet. */
@Immutable
@Serializable
data class ServiceEntry(
    val id: Long,
    val date: String? = null,
    val km: Int,
    val title: String,
    val items: List<String> = emptyList(),   // ids from [ServiceCatalog.ITEMS]
    val custom: List<String> = emptyList(),  // free-text jobs
    val shop: String = "",
    val notes: String = "",
)

/** Everything the Service page stores — one small JSON file. */
@Immutable
@Serializable
data class ServiceData(
    val entries: List<ServiceEntry> = ServiceCatalog.DEFAULT_LOG,
    /** Odometer typed in by the driver; used when the car can't report one (or was corrected). */
    val manualKm: Int = 134_000,
    val useManualKm: Boolean = false,
    /** content:// URI of the driver's own owner's-manual PDF, picked once. */
    val manualPdfUri: String? = null,
)

data class ScheduleItem(val id: String, val name: String, val km: Int?, val months: Int?, val confirmed: Boolean)
data class StdItem(val id: String, val label: String, val sched: String? = null)

enum class DueStatus { OK, SOON, OVER, NONE }

/** Where one scheduled job stands right now. */
data class ScheduleStatus(
    val item: ScheduleItem,
    val last: ServiceEntry?,
    val kmLeft: Int?,
    val monthsLeft: Float?,
    val used: Float,
    val status: DueStatus,
) {
    val dueKm: Int? get() = if (last != null && item.km != null) last.km + item.km else null
}

object ServiceCatalog {
    /** Intervals: oil & brake fluid are well established; the rest are "check the booklet" (confirmed = false). */
    val SCHEDULE = listOf(
        ScheduleItem("oil", "Oil service", 15_000, 12, true),
        ScheduleItem("fuel", "Diesel filter", 30_000, null, false),
        ScheduleItem("air", "Air filter", 45_000, null, false),
        ScheduleItem("atf", "ZF 8-speed fluid", 60_000, null, false),
        ScheduleItem("transfer", "Transfer case fluid", 60_000, null, false),
        ScheduleItem("diffs", "Front & rear diffs", 60_000, null, false),
        ScheduleItem("brake", "Brake fluid", null, 24, true),
    )

    val ITEMS = listOf(
        StdItem("oil", "Engine oil", "oil"), StdItem("oilfilter", "Oil filter"),
        StdItem("air", "Air filter", "air"), StdItem("fuel", "Diesel filter", "fuel"),
        StdItem("cabin", "Cabin filter"), StdItem("brake", "Brake fluid", "brake"),
        StdItem("atf", "ZF gearbox fluid & filter", "atf"), StdItem("transfer", "Transfer case fluid", "transfer"),
        StdItem("fdiff", "Front diff oil", "diffs"), StdItem("rdiff", "Rear diff oil", "diffs"),
        StdItem("coolant", "Coolant"), StdItem("pads", "Brake pads / discs"),
        StdItem("tyres", "Tyres / rotation"), StdItem("wipers", "Wiper blades"),
    )

    val PACKAGES: Map<String, List<String>> = linkedMapOf(
        "Minor service" to listOf("oil", "oilfilter"),
        "Major service" to listOf("oil", "oilfilter", "air", "fuel", "cabin", "brake"),
        "Gearbox" to listOf("atf"),
        "4×4 drivetrain" to listOf("transfer", "fdiff", "rdiff"),
        "Brakes" to listOf("brake", "pads"),
        "Custom" to emptyList(),
    )

    /** The truck's real history (dates not known yet — tap an entry to add them). */
    val DEFAULT_LOG = listOf(
        ServiceEntry(1, null, 134_000, "Major service", listOf("oil", "oilfilter", "air", "atf", "transfer"), listOf("Tappet cover gaskets, both banks")),
        ServiceEntry(2, null, 120_000, "Minor service", listOf("oil", "oilfilter", "fuel"), notes = "Filters incl. diesel filter"),
    )

    fun label(id: String) = ITEMS.firstOrNull { it.id == id }?.label ?: id
    private fun schedOf(id: String) = ITEMS.firstOrNull { it.id == id }?.sched

    /** Remaining = whichever of distance or time runs out first. */
    fun status(entries: List<ServiceEntry>, odometerKm: Int, today: Long = todayEpochDay()): List<ScheduleStatus> =
        SCHEDULE.map { item ->
            val last = entries.filter { e -> e.items.any { schedOf(it) == item.id } }
                .sortedWith(compareByDescending<ServiceEntry> { it.km }.thenByDescending { it.date ?: "" })
                .firstOrNull()
                ?: return@map ScheduleStatus(item, null, null, null, 0f, DueStatus.NONE)
            val lastDay = last.date?.let { epochDay(it) }
            val kmLeft = item.km?.let { last.km + it - odometerKm }
            val monthsLeft = if (item.months != null && lastDay != null) item.months - (today - lastDay) / 30.44f else null
            val fracKm = if (kmLeft != null && item.km != null) 1f - kmLeft.toFloat() / item.km else 0f
            val fracT = if (monthsLeft != null && item.months != null) 1f - monthsLeft / item.months else 0f
            val used = maxOf(fracKm, fracT)
            ScheduleStatus(item, last, kmLeft, monthsLeft, used, when {
                used >= 1f -> DueStatus.OVER
                used >= 0.85f -> DueStatus.SOON
                else -> DueStatus.OK
            })
        }
}

/*
 * Dates without java.time (that needs Android 8+; minSdk is 7). Epoch-day maths, Howard Hinnant's
 * civil-calendar algorithm: exact for any Gregorian date, no allocation, no time zones involved.
 */
fun epochDay(iso: String): Long? {
    val p = iso.trim().split('-')
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = p[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    val yy = if (m <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

fun todayIso(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun todayEpochDay(): Long = epochDay(todayIso()) ?: 0L

/** "26 Nov 2026" for display. */
fun prettyDate(iso: String?): String? {
    val p = iso?.split('-')?.takeIf { it.size == 3 } ?: return null
    val month = p[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return null
    return "${p[2].trimStart('0')} $month ${p[0]}"
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
