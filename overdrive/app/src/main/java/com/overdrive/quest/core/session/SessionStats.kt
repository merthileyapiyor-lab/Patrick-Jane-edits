package com.overdrive.quest.core.session

import kotlinx.serialization.Serializable
import kotlin.math.ceil

data class SessionSample(
    val tMs: Long,
    val fps: Int? = null,
    val targetFps: Int? = null,
    val cpuLevel: Int? = null,
    val gpuLevel: Int? = null,
    val cpuMHz: Int? = null,
    val gpuMHz: Int? = null,
    val cpuUtil: Float? = null,
    val gpuUtil: Float? = null,
    val socTempC: Float? = null,
    val batteryTempC: Float? = null,
    val batteryPct: Int? = null,
    val app: String? = null,
)

@Serializable
data class SessionSummary(
    val id: String,
    val startedAt: Long,
    val durationSec: Long,
    val app: String? = null,
    val samples: Int = 0,
    val avgFps: Float? = null,
    val onePercentLowFps: Float? = null,
    val minFps: Int? = null,
    val targetFps: Int? = null,
    val smoothnessPct: Float? = null,
    val maxSocC: Float? = null,
    val maxBatteryC: Float? = null,
    val avgCpuUtil: Float? = null,
    val avgGpuUtil: Float? = null,
    val batteryUsedPct: Int? = null,
    val drainPerHourPct: Float? = null,
)

object SessionStats {
    fun summarize(id: String, startedAt: Long, samples: List<SessionSample>): SessionSummary {
        if (samples.isEmpty()) return SessionSummary(id = id, startedAt = startedAt, durationSec = 0)
        val durationMs = (samples.last().tMs - samples.first().tMs).coerceAtLeast(0)
        val fps = samples.mapNotNull { it.fps }
        val sorted = fps.sorted()
        val lowCount = if (sorted.isEmpty()) 0 else ceil(sorted.size * 0.01).toInt().coerceAtLeast(1)
        val target = samples.mapNotNull { it.targetFps }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val vrSamples = samples.filter { it.fps != null && it.targetFps != null && it.targetFps > 0 }
        val smooth = if (vrSamples.isEmpty()) {
            null
        } else {
            vrSamples.count { it.fps!!.toFloat() / it.targetFps!! >= 0.95f } * 100f / vrSamples.size
        }
        val batteries = samples.mapNotNull { it.batteryPct }
        val used = if (batteries.size >= 2) (batteries.first() - batteries.last()).coerceAtLeast(0) else null
        val hours = durationMs / 3_600_000f
        val app = samples.mapNotNull { it.app }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        return SessionSummary(
            id = id,
            startedAt = startedAt,
            durationSec = durationMs / 1000,
            app = app,
            samples = samples.size,
            avgFps = fps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            onePercentLowFps = sorted.take(lowCount).takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            minFps = sorted.firstOrNull(),
            targetFps = target,
            smoothnessPct = smooth,
            maxSocC = samples.mapNotNull { it.socTempC }.maxOrNull(),
            maxBatteryC = samples.mapNotNull { it.batteryTempC }.maxOrNull(),
            avgCpuUtil = samples.mapNotNull { it.cpuUtil }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            avgGpuUtil = samples.mapNotNull { it.gpuUtil }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            batteryUsedPct = used,
            drainPerHourPct = if (used != null && hours >= 1f / 60f) used / hours else null,
        )
    }

    const val CSV_HEADER =
        "t_ms,fps,target_fps,cpu_level,gpu_level,cpu_mhz,gpu_mhz,cpu_util,gpu_util,soc_c,battery_c,battery_pct,app"

    fun toCsv(samples: List<SessionSample>): String = buildString {
        appendLine(CSV_HEADER)
        val t0 = samples.firstOrNull()?.tMs ?: 0L
        samples.forEach { s ->
            appendLine(
                listOf(
                    (s.tMs - t0).toString(),
                    s.fps?.toString(), s.targetFps?.toString(),
                    s.cpuLevel?.toString(), s.gpuLevel?.toString(),
                    s.cpuMHz?.toString(), s.gpuMHz?.toString(),
                    s.cpuUtil?.let { "%.3f".format(java.util.Locale.US, it) },
                    s.gpuUtil?.let { "%.3f".format(java.util.Locale.US, it) },
                    s.socTempC?.let { "%.1f".format(java.util.Locale.US, it) },
                    s.batteryTempC?.let { "%.1f".format(java.util.Locale.US, it) },
                    s.batteryPct?.toString(),
                    s.app,
                ).joinToString(",") { it ?: "" },
            )
        }
    }
}
