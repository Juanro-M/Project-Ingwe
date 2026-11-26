package io.ingwe.dash.ui.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.data.service.*
import io.ingwe.dash.ui.components.gauges.Panel
import io.ingwe.dash.ui.components.gauges.SegmentedControl
import io.ingwe.dash.ui.pages.SourceTag
import io.ingwe.dash.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ServiceTab { OVERVIEW, SPECS }

private val Card = RoundedCornerShape(14.dp)
private fun fmtKm(n: Int) = "%,d".format(n)
private fun statusColor(s: DueStatus) = when (s) {
    DueStatus.OVER -> IngweAlert
    DueStatus.SOON -> IngweCaution
    DueStatus.NONE -> IngweTextTertiary
    DueStatus.OK -> IngweTeal
}

/**
 * Service page: what's due (oil hero + timeline), the odometer, the log, specs & fluids.
 *
 * Light by design: it reads only the odometer from the live state, so the 20 Hz data
 * stream doesn't recompose it; everything else changes only when you save something.
 */
@Composable
fun ServicePage(odometerFromCar: Float, store: ServiceStore, onEdit: (ServiceEntry?) -> Unit) {
    val data by store.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(ServiceTab.OVERVIEW) }

    val carKm = odometerFromCar.takeUnless { it.isNaN() || it <= 0f }?.roundToInt()
    LaunchedEffect(carKm) { carKm?.let(store::rememberCarOdometer) }
    val odo = if (!data.useManualKm && carKm != null) carKm else data.manualKm
    val fromCar = !data.useManualKm && carKm != null
    val status = remember(data.entries, odo) { ServiceCatalog.status(data.entries, odo) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SegmentedControl(
                options = listOf(ServiceTab.OVERVIEW to "Overview", ServiceTab.SPECS to "Specs & fluids"),
                selected = tab,
                onSelect = { tab = it },
            )
            Spacer(Modifier.weight(1f))
            SourceTag("Amarok 3.0 TDI · engine DDXA · 2017–2019")
        }
        when (tab) {
            ServiceTab.OVERVIEW -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OilHero(status.first { it.item.id == "oil" })
                    Panel(Modifier.weight(1f).fillMaxWidth(), title = "Coming up", trailing = { Legend() }) {
                        Timeline(status, odo, Modifier.weight(1f).fillMaxWidth())
                    }
                }
                Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OdometerCard(odo, fromCar, onCorrect = store::correctOdometer)
                    Panel(Modifier.weight(1f).fillMaxWidth(), title = "Service log", trailing = { SmallButton("+ Log", primary = true) { onEdit(null) } }) {
                        LogList(data.entries, onEdit, Modifier.weight(1f))
                    }
                    ManualRow(data.manualPdfUri, store::setManualPdf)
                }
            }
            ServiceTab.SPECS -> SpecsGrid(Modifier.weight(1f))
        }
    }
}

/* ── Overview pieces ─────────────────────────────────────────────────────── */

@Composable
private fun OilHero(s: ScheduleStatus) {
    Row(
        Modifier.fillMaxWidth().clip(Card).background(IngweSurfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val over = (s.kmLeft ?: 0) < 0
        Column {
            Text("Next oil service", style = LabelStyle, color = IngweTextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.kmLeft?.let { fmtKm(abs(it)) } ?: "–", style = ReadoutMedium.copy(fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light), color = if (over) IngweAlert else IngweTextPrimary)
                Spacer(Modifier.width(6.dp))
                Text(if (over) "km overdue" else "km to go", style = LabelStyle, color = IngweTextTertiary, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            if (s.last == null) {
                Text("Log your last oil change to start the countdown", style = LabelStyle, color = IngweTextTertiary)
            } else {
                val months = when {
                    s.monthsLeft != null -> " · or in ${s.monthsLeft.roundToInt().coerceAtLeast(0)} months"
                    else -> " · or 12 months, add the date"
                }
                Row {
                    Text("Due at ${fmtKm(s.dueKm ?: 0)} km$months", style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
                    Text(
                        when (s.status) { DueStatus.OVER -> "Overdue"; DueStatus.SOON -> "Due soon"; else -> "On track" },
                        style = LabelStyle, color = statusColor(s.status),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val frac = s.used.coerceIn(0f, 1f)
                val col = statusColor(s.status)
                val track = IngweHairline
                Box(Modifier.fillMaxWidth().height(6.dp).drawBehind {
                    drawRoundRect(track, cornerRadius = CornerRadius(3.dp.toPx()))
                    if (frac > 0f) drawRoundRect(col, size = Size(size.width * frac, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                })
                Spacer(Modifier.height(6.dp))
                Text("Last done at ${fmtKm(s.last.km)} km · 5W-30 / 5W-40 to VW 505 01, 8.0 L with filter", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary)
            }
        }
    }
}

@Composable
private fun Legend() {
    val teal = IngweTeal
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(teal))
        Text("  driven since last   ", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary)
        Box(Modifier.size(3.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(teal))
        Text("  due", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary)
    }
}

/**
 * One row per job on a shared odometer axis: bar from "last done" to "due", filled up to
 * today; a tick at the due point; the orange NOW line runs through every row.
 * Each row draws its own slice of the NOW line, so it reads as one continuous line.
 */
@Composable
private fun Timeline(status: List<ScheduleStatus>, odo: Int, modifier: Modifier) {
    val from = odo - 20_000f
    val to = odo + 50_000f
    fun pos(km: Float) = ((km - from) / (to - from)).coerceIn(0f, 1f)
    val accent = IngweAccent
    val trackBg = IngweSurfaceHigh
    val nowX = pos(odo.toFloat())

    Column(modifier) {
        status.forEach { s ->
            val col = statusColor(s.status)
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append(s.item.name)
                        if (!s.item.confirmed) withStyle(SpanStyle(color = IngweTextTertiary, fontSize = 11.sp)) { append(" ?") }
                    },
                    style = LabelStyle, color = IngweTextPrimary, modifier = Modifier.width(150.dp), maxLines = 1,
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().drawBehind {
                        val h = 10.dp.toPx()
                        val y = (size.height - h) / 2f
                        val r = CornerRadius(h / 2f)
                        drawRoundRect(trackBg, Offset(0f, y), Size(size.width, h), r)
                        val due = s.dueKm
                        if (s.last != null && due != null) {
                            val a = pos(s.last.km.toFloat()) * size.width
                            val b = pos(due.toFloat()) * size.width
                            if (b > a) {
                                drawRoundRect(col.copy(alpha = 0.18f), Offset(a, y), Size(b - a, h), r)
                                val fillEnd = (nowX * size.width).coerceIn(a, b)
                                if (fillEnd > a) drawRoundRect(col.copy(alpha = 0.85f), Offset(a, y), Size(fillEnd - a, h), r)
                            }
                            if (due <= to) drawRoundRect(col, Offset(b - 1.5.dp.toPx(), size.height / 2f - 8.dp.toPx()), Size(3.dp.toPx(), 16.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }
                        drawRect(accent, Offset(nowX * size.width - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val msg = when {
                        s.status == DueStatus.NONE -> "No record yet — log it when done"
                        s.kmLeft == null -> if (s.monthsLeft != null) "Time-based" else "Time-based · add the date to your log entry"
                        else -> null
                    }
                    if (msg != null) Text(msg, style = LabelStyle.copy(fontSize = 11.sp), color = IngweTextTertiary, modifier = Modifier.padding(start = 10.dp))
                }
                Text(
                    buildAnnotatedString {
                        when {
                            s.status == DueStatus.NONE -> withStyle(SpanStyle(color = IngweTextTertiary)) { append("–") }
                            s.kmLeft == null && s.monthsLeft != null -> { append("${s.monthsLeft.roundToInt().coerceAtLeast(0)}"); withStyle(SpanStyle(color = IngweTextTertiary, fontSize = 11.sp)) { append(" months") } }
                            s.kmLeft == null -> withStyle(SpanStyle(color = IngweTextTertiary)) { append("every 2 yrs") }
                            s.kmLeft < 0 -> { append(fmtKm(-s.kmLeft)); withStyle(SpanStyle(color = IngweTextTertiary, fontSize = 11.sp)) { append(" km over") } }
                            else -> { append(fmtKm(s.kmLeft)); withStyle(SpanStyle(color = IngweTextTertiary, fontSize = 11.sp)) { append(" km") } }
                        }
                    },
                    style = LabelStyle.copy(fontFeatureSettings = "tnum"), color = col,
                    modifier = Modifier.width(128.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1,
                )
            }
        }
        // Axis
        Row(Modifier.height(18.dp).fillMaxWidth()) {
            Spacer(Modifier.width(150.dp))
            val labelColor = IngweTextTertiary
            Box(Modifier.weight(1f).fillMaxHeight().drawBehind {
                drawRect(accent, Offset(nowX * size.width - 1.dp.toPx(), 0f), Size(2.dp.toPx(), 4.dp.toPx()))
            }) {
                var k = (Math.ceil(from / 10_000.0) * 10_000).toInt()
                while (k <= to) {
                    val f = pos(k.toFloat())
                    Text(
                        "${k / 1000}k", style = LabelStyle.copy(fontSize = 11.sp), color = labelColor,
                        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start).layoutAtFraction(f).padding(top = 4.dp),
                    )
                    k += 10_000
                }
            }
            Spacer(Modifier.width(128.dp))
        }
    }
}

/** Places a child centred on a horizontal fraction of the parent's width. */
private fun Modifier.layoutAtFraction(f: Float) = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0))
        layout(constraints.maxWidth, p.height) {
            p.place((constraints.maxWidth * f - p.width / 2f).roundToInt(), 0)
        }
    },
)

@Composable
private fun OdometerCard(odo: Int, fromCar: Boolean, onCorrect: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(Card).background(IngweSurface).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row {
            Text("Odometer", style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
            Text(if (fromCar) "From car · OBD-II" else "Entered by you", style = LabelStyle, color = IngweTextSecondary)
        }
        Spacer(Modifier.height(2.dp))
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${fmtKm(odo)} km", style = ReadoutMedium.copy(fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light), color = IngweTextPrimary, modifier = Modifier.weight(1f))
                SmallButton("Correct") { text = odo.toString(); editing = true }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(text, { text = it.filter(Char::isDigit).take(7) }, "km", Modifier.width(130.dp), KeyboardType.Number)
                Text("km", style = LabelStyle, color = IngweTextSecondary)
                Spacer(Modifier.weight(1f))
                SmallButton("Save", primary = true) { text.toIntOrNull()?.takeIf { it > 0 }?.let(onCorrect); editing = false }
            }
        }
    }
}

@Composable
private fun LogList(entries: List<ServiceEntry>, onEdit: (ServiceEntry) -> Unit, modifier: Modifier) {
    val rows = remember(entries) { entries.sortedWith(compareByDescending<ServiceEntry> { it.km }.thenByDescending { it.date ?: "" }) }
    if (rows.isEmpty()) {
        Text("No services logged yet. Tap “+ Log” after each visit.", style = LabelStyle, color = IngweTextTertiary, modifier = modifier)
        return
    }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(rows, key = { it.id }) { e ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEdit(e) }.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${fmtKm(e.km)} km", style = ReadoutSmall.copy(fontSize = 15.sp), color = IngweTextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(prettyDate(e.date) ?: "add date", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary, modifier = Modifier.weight(1f))
                    Text("Edit ›", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary)
                }
                Text(
                    e.title + if (e.shop.isNotBlank()) " · ${e.shop}" else "",
                    style = LabelStyle.copy(fontSize = 15.sp), color = IngweTextPrimary,
                )
                val what = (e.items.map(ServiceCatalog::label) + e.custom).joinToString(" · ") + if (e.notes.isNotBlank()) " — ${e.notes}" else ""
                Text(what, style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Opens the driver's own owner's-manual PDF. Picked once with the system file picker; nothing is bundled. */
@Composable
private fun ManualRow(uri: String?, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val open: (String) -> Unit = { u ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u), "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            note = null
        } catch (e: ActivityNotFoundException) {
            note = "No PDF viewer installed"
        } catch (e: SecurityException) {
            note = "File moved — pick it again"
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPicked(picked.toString())
            open(picked.toString())
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(Card).background(IngweSurface)
            .clickable { if (uri != null && note == null) open(uri) else picker.launch(arrayOf("application/pdf")) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Owner's manual", style = LabelStyle.copy(fontSize = 14.sp), color = IngweTextSecondary, modifier = Modifier.weight(1f))
        Text(note ?: if (uri == null) "Choose PDF ›" else "Open PDF ›", style = LabelStyle.copy(fontSize = 12.sp), color = IngweAccent)
    }
}

/* ── Specs & fluids (HaynesPro data for the DDXA, summarised) ─────────────── */

private data class SpecCard(val title: String, val rows: List<Pair<String, String>>, val note: String, val highlightFirst: Boolean = false)

private val SPECS = listOf(
    SpecCard("Engine oil", listOf("Spec" to "VW 505 01", "Grade" to "5W-30 or 5W-40", "Also fine" to "VW 507 00 oils", "Capacity" to "8.0 L incl. filter", "Drain plug" to "30 Nm", "Oil filter cap" to "35 Nm"),
        "507 00 oils also meet 505 01. Motul 8100 X-clean 5W-40 or any VW-approved 505 01 oil fits.", highlightFirst = true),
    SpecCard("Coolant", listOf("Up to 25 Nov 2019" to "G13 · TL-VW 774 J", "From 26 Nov 2019" to "G12evo · TL-VW 774 L", "System" to "8.0 L", "Mix 40 %" to "to −25 °C", "Mix 50 %" to "to −35 °C", "Cap opens" to "1.4–1.6 bar"),
        "Use deionised water. Don't mix coolant types."),
    SpecCard("Gearbox & 4Motion", listOf("Transfer case" to "ATF G 052 533 A2 · 1.25 L", "Transfer plugs" to "27 Nm, replace", "Front diff" to "75W-90 G 052 145 · 0.8 L", "Front fill plug" to "35 Nm", "Rear diff" to "G 052 539 A2 · 2.0 L", "Rear plugs" to "33 Nm, replace"),
        "ZF 8HP fluid isn't listed on these sheets. Use the ZF Lifeguard 8 spec from the workshop."),
    SpecCard("Brakes", listOf("Fluid" to "VW 501 14 (DOT 4 ok)", "Capacity" to "≈1.0 L", "Front disc" to "303×28 → min 26 mm", "  or" to "332×30 → min 27 mm", "Rear disc" to "300×22 → min 19 mm", "Pads" to "min 2 mm"),
        "Brake fluid every 2 years regardless of distance."),
    SpecCard("Wheels & tyres", listOf("Front" to "2.0–2.2 bar", "Rear" to "2.0–3.0 bar (by load)", "Spare" to "4.5 bar", "Bolt pattern" to "5 × 120", "Wheel bolts" to "180 Nm", "TPMS" to "direct sensors"),
        "Tighten wheel bolts by hand with a torque wrench. No impact gun, no grease on the threads."),
    SpecCard("Other fluids & checks", listOf("Power steering" to "G 004 000 M2", "A/C gas" to "R134a · 575–625 g", "A/C oil" to "ISO 100 · 130–150 ml", "Glow plugs" to "12 Nm", "Compression" to "28–33 bar (min 21)", "Oil pressure" to ">1.2 bar idle · >3.4 at 2500"),
        "Timing chain (no belt to replace). Hydraulic valve lifters, no clearance to set."),
)

@Composable
private fun SpecsGrid(modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SPECS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card ->
                    Column(Modifier.weight(1f).fillMaxHeight().clip(Card).background(IngweSurface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(card.title, style = LabelStyle.copy(fontSize = 14.sp), color = IngweTextSecondary)
                        Spacer(Modifier.height(8.dp))
                        card.rows.forEachIndexed { i, (k, v) ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(k, style = LabelStyle, color = IngweTextTertiary, modifier = Modifier.weight(1f))
                                Text(v, style = LabelStyle.copy(fontFeatureSettings = "tnum"), color = if (card.highlightFirst && i == 0) IngweAccent else IngweTextPrimary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(card.note, style = LabelStyle.copy(fontSize = 12.sp, lineHeight = 17.sp), color = IngweTextSecondary)
                    }
                }
            }
        }
    }
}

/* ── Small shared bits ──────────────────────────────────────────────────── */

@Composable
internal fun SmallButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = LabelStyle.copy(fontSize = 13.sp),
        color = if (primary) IngweOnAccent else IngweTextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) IngweAccent else IngweSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
internal fun InputField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: Int = 0,
    onDone: (() -> Unit)? = null,
) {
    val textColor = IngweTextPrimary
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = singleLine,
        textStyle = LabelStyle.copy(fontSize = 14.sp, color = textColor),
        cursorBrush = SolidColor(IngweAccent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(IngweSurfaceHigh)
            .heightIn(min = minHeight.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = LabelStyle.copy(fontSize = 14.sp), color = IngweTextTertiary)
                inner()
            }
        },
    )
}

internal val TransparentColor = Color.Transparent
