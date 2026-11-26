package io.ingwe.dash.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.data.service.ServiceCatalog
import io.ingwe.dash.data.service.ServiceEntry
import io.ingwe.dash.data.service.epochDay
import io.ingwe.dash.data.service.todayIso
import io.ingwe.dash.ui.theme.*

/**
 * "Log a service" / "Edit service" sheet, drawn over the dashboard.
 * Pick a package (pre-ticks the usual items), tick or untick anything, add your own jobs.
 * Delete takes two taps instead of a pop-up. No prices anywhere.
 */
@Composable
fun LogSheet(
    entry: ServiceEntry?,
    odometerKm: Int,
    onSave: (ServiceEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val editing = entry != null
    var pkg by remember { mutableStateOf(entry?.title?.takeIf { it in ServiceCatalog.PACKAGES } ?: if (editing) "Custom" else "Minor service") }
    val items = remember { mutableStateListOf<String>().apply { addAll(entry?.items ?: ServiceCatalog.PACKAGES.getValue("Minor service")) } }
    val custom = remember { mutableStateListOf<String>().apply { addAll(entry?.custom ?: emptyList()) } }
    var date by remember { mutableStateOf(entry?.date ?: todayIso()) }
    var km by remember { mutableStateOf((entry?.km ?: odometerKm).toString()) }
    var shop by remember { mutableStateOf(entry?.shop ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var customText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun addCustom() { customText.trim().takeIf { it.isNotEmpty() }?.let { custom.add(it); customText = "" } }

    // Backdrop: tap outside to close
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(16.dp))
                .background(IngweSurface)
                .border(1.dp, IngweHairline, RoundedCornerShape(16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // swallow taps
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Edit service" else "Log a service", style = LabelStyle.copy(fontSize = 17.sp), color = IngweTextPrimary, modifier = Modifier.weight(1f))
                SmallButton("Cancel", onClick = onClose)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Date", Modifier.width(150.dp)) { InputField(date, { date = it.take(10) }, "yyyy-mm-dd", Modifier.fillMaxWidth()) }
                Field("Odometer", Modifier.width(130.dp)) { InputField(km, { km = it.filter(Char::isDigit).take(7) }, "km", Modifier.fillMaxWidth(), KeyboardType.Number) }
                Field("Workshop", Modifier.weight(1f)) { InputField(shop, { shop = it.take(60) }, "e.g. VW Menlyn, or DIY", Modifier.fillMaxWidth()) }
            }
            Field("Package") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ServiceCatalog.PACKAGES.keys.forEach { p ->
                        Chip(p, selected = p == pkg) { pkg = p; items.clear(); items.addAll(ServiceCatalog.PACKAGES.getValue(p)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Field("Standard items", Modifier.weight(1.1f)) {
                    ServiceCatalog.ITEMS.chunked(2).forEach { pair ->
                        Row {
                            pair.forEach { item ->
                                val on = item.id in items
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                        .clickable { if (on) items.remove(item.id) else items.add(item.id) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                            .background(if (on) IngweAccent else Color.Transparent)
                                            .border(1.5.dp, if (on) IngweAccent else IngweTextTertiary, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) { if (on) Text("✓", style = LabelStyle.copy(fontSize = 11.sp), color = IngweOnAccent) }
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.label, style = LabelStyle.copy(fontSize = 13.sp), color = IngweTextPrimary)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Your own items") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            InputField(customText, { customText = it.take(80) }, "e.g. Tappet cover gaskets both banks", Modifier.weight(1f), onDone = ::addCustom)
                            SmallButton("Add", onClick = ::addCustom)
                        }
                        Spacer(Modifier.height(6.dp))
                        if (custom.isEmpty()) {
                            Text("Repairs, upgrades, one-offs — anything not in the list.", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                custom.forEachIndexed { i, t ->
                                    Row(
                                        Modifier.clip(RoundedCornerShape(999.dp)).background(IngweSurfaceHigh).padding(start = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(t, style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextPrimary)
                                        Text("✕", style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary,
                                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { custom.removeAt(i) }.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                    Field("Notes") {
                        InputField(notes, { notes = it.take(300) }, "Oil brand & litres, part numbers, anything worth remembering", Modifier.fillMaxWidth(), singleLine = false, minHeight = 64)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editing) {
                    Text(
                        if (confirmDelete) "Tap again to delete" else "Delete",
                        style = LabelStyle.copy(fontSize = 13.sp),
                        color = if (confirmDelete) IngweOnAccent else IngweAlert,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (confirmDelete) IngweAlert else IngweSurfaceHigh)
                            .clickable { if (confirmDelete) onDelete(entry!!.id) else confirmDelete = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                val n = items.size + custom.size
                Text(
                    error ?: if (n > 0) "$n item${if (n == 1) "" else "s"} · ticked items update the timeline" else "Pick a package, tick items or add your own",
                    style = LabelStyle.copy(fontSize = 12.sp),
                    color = if (error != null) IngweAlert else IngweTextTertiary,
                    modifier = Modifier.weight(1f),
                )
                SmallButton(if (editing) "Save changes" else "Save to log", primary = true) {
                    addCustom()
                    val kmInt = km.toIntOrNull()
                    when {
                        kmInt == null || kmInt <= 0 || items.size + custom.size == 0 -> error = "Add the odometer and at least one item"
                        date.isNotBlank() && epochDay(date) == null -> error = "Date as yyyy-mm-dd, or leave it empty"
                        else -> onSave(
                            ServiceEntry(
                                id = entry?.id ?: System.currentTimeMillis(),
                                date = date.trim().ifBlank { null },
                                km = kmInt,
                                title = if (pkg == "Custom") custom.firstOrNull() ?: "Custom work" else pkg,
                                items = items.toList(),
                                custom = custom.toList(),
                                shop = shop.trim(),
                                notes = notes.trim(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Text(label, style = LabelStyle.copy(fontSize = 12.sp), color = IngweTextTertiary)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = LabelStyle.copy(fontSize = 13.sp),
        color = if (selected) IngweOnAccent else IngweTextSecondary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) IngweAccent else IngweSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
