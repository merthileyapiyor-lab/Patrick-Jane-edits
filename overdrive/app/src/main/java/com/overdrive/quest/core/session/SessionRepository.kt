package com.overdrive.quest.core.session

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.overdrive.quest.core.monitor.MetricsHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(
    val id: String,
    val startedAt: Long,
    val samples: Int,
    val live: SessionSummary,
)

/** "Performance Lab": records a play session and keeps summaries + CSVs on disk. */
class SessionRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val hub: MetricsHub,
) {
    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val dir = File(context.filesDir, "sessions")
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(SessionSummary.serializer())

    private val buffer = mutableListOf<SessionSample>()
    private var job: Job? = null

    fun load() {
        scope.launch(Dispatchers.IO) {
            _sessions.value = runCatching { json.decodeFromString(listSerializer, index.readText()) }
                .getOrDefault(emptyList())
        }
    }

    fun start() {
        if (job != null) return
        val startedAt = System.currentTimeMillis()
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAt))
        synchronized(buffer) { buffer.clear() }
        _recording.value = Recording(id, startedAt, 0, SessionSummary(id = id, startedAt = startedAt, durationSec = 0))
        hub.acquire(HUB_TAG)
        job = scope.launch {
            hub.metrics.collect { m ->
                if (m.timestamp < startedAt) return@collect
                val sample = SessionSample(
                    tMs = m.timestamp,
                    fps = m.fps,
                    targetFps = m.targetFps,
                    cpuLevel = m.cpuLevel,
                    gpuLevel = m.gpuLevel,
                    cpuMHz = m.cpuMHz,
                    gpuMHz = m.gpuMHz,
                    cpuUtil = m.cpuUtil,
                    gpuUtil = m.gpuUtil,
                    socTempC = m.socTempC,
                    batteryTempC = m.battery.tempC,
                    batteryPct = m.battery.level,
                    app = m.foreground,
                )
                val snapshot = synchronized(buffer) {
                    buffer += sample
                    if (buffer.size > MAX_SAMPLES) buffer.removeAt(0)
                    if (buffer.size % 5 == 1) buffer.toList() else null
                }
                _recording.update { rec ->
                    rec?.copy(
                        samples = rec.samples + 1,
                        live = snapshot?.let { SessionStats.summarize(rec.id, rec.startedAt, it) } ?: rec.live,
                    )
                }
            }
        }
    }

    suspend fun stop(): SessionSummary? {
        val j = job ?: return null
        j.cancel()
        job = null
        hub.release(HUB_TAG)
        val rec = _recording.value ?: return null
        _recording.value = null
        val samples = synchronized(buffer) { buffer.toList() }
        if (samples.size < 2) return null
        val summary = SessionStats.summarize(rec.id, rec.startedAt, samples)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, "${rec.id}.csv").writeText(SessionStats.toCsv(samples))
            val next = listOf(summary) + _sessions.value
            index.writeText(json.encodeToString(listSerializer, next))
            _sessions.value = next
        }
        return summary
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.csv").delete()
        val next = _sessions.value.filterNot { it.id == id }
        index.writeText(json.encodeToString(listSerializer, next))
        _sessions.value = next
    }

    /** Copies a session CSV to Download/Overdrive and returns the visible path. */
    suspend fun exportCsv(id: String): String? = withContext(Dispatchers.IO) {
        val source = File(dir, "$id.csv")
        if (!source.exists()) return@withContext null
        val name = "overdrive-session-$id.csv"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Overdrive")
        }
        runCatching {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            "${Environment.DIRECTORY_DOWNLOADS}/Overdrive/$name"
        }.getOrNull()
    }

    private companion object {
        const val HUB_TAG = "recorder"
        const val MAX_SAMPLES = 4 * 60 * 60
    }
}
