package io.ingwe.dash.data.signals

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Loads assets/signals.json — off the main thread, once, only when a live source needs it. */
object SignalCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<SignalDefinition>? = null

    suspend fun load(context: Context): List<SignalDefinition> = cached ?: withContext(Dispatchers.IO) {
        context.assets.open("signals.json").bufferedReader().use { reader ->
            json.decodeFromString<List<SignalDefinition>>(reader.readText())
        }.also { cached = it }
    }
}
