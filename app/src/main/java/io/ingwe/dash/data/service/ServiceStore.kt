package io.ingwe.dash.data.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The service log: a few kilobytes of JSON in app-private storage. No database.
 *
 * - Read once at start (off the main thread), then held in memory as a StateFlow.
 * - Every change rewrites the whole file *atomically*: write to a temp file, then rename
 *   over the old one. A power cut mid-write leaves the previous file intact, never half a file.
 * - Writes happen only when you save something — nothing per frame, nothing while driving.
 */
class ServiceStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(ServiceData())
    val data: StateFlow<ServiceData> = _data.asStateFlow()

    init {
        scope.launch {
            val loaded = runCatching { json.decodeFromString<ServiceData>(file.readText()) }.getOrNull()
            if (loaded != null) _data.value = loaded
        }
    }

    fun save(entry: ServiceEntry) = update { d ->
        val exists = d.entries.any { it.id == entry.id }
        d.copy(entries = if (exists) d.entries.map { if (it.id == entry.id) entry else it } else d.entries + entry)
    }

    fun delete(id: Long) = update { d -> d.copy(entries = d.entries.filterNot { it.id == id }) }

    fun correctOdometer(km: Int) = update { it.copy(manualKm = km, useManualKm = true) }

    /** Remember the car's reading (coarsely) so the page still knows the km with the ignition off. */
    fun rememberCarOdometer(km: Int) {
        val d = _data.value
        if (d.useManualKm || kotlin.math.abs(km - d.manualKm) < 10) return
        update { it.copy(manualKm = km) }
    }

    fun setManualPdf(uri: String) = update { it.copy(manualPdfUri = uri) }

    private fun update(change: (ServiceData) -> ServiceData) {
        val next = change(_data.value)
        _data.value = next
        scope.launch { writeLock.withLock { writeAtomically(json.encodeToString(ServiceData.serializer(), next)) } }
    }

    private fun writeAtomically(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
