package com.overdrive.quest.core.monitor

/**
 * One line of the once-per-second VrApi performance log, e.g.
 * `FPS=72/72,Prd=37ms,Tear=0,Early=0,Stale=0,...,CPU4/GPU=2/2,1478/490MHz,...,Temp=34.0C/0.0C,...,GPU%=0.44,CPU%=0.22(W0.27),...`
 */
data class VrApiStats(
    val fps: Int,
    val targetFps: Int,
    val stale: Int? = null,
    val tear: Int? = null,
    val predictionMs: Int? = null,
    val cpuLevel: Int? = null,
    val gpuLevel: Int? = null,
    val cpuMHz: Int? = null,
    val gpuMHz: Int? = null,
    val memMHz: Int? = null,
    val freeMemMb: Int? = null,
    val batteryTempC: Float? = null,
    val sensorTempC: Float? = null,
    val appGpuMs: Float? = null,
    val gpuUtil: Float? = null,
    val cpuUtil: Float? = null,
    val timestampSec: Double? = null,
) {
    val fpsRatio: Float get() = if (targetFps > 0) fps.toFloat() / targetFps else 1f
}

object VrApiParser {
    private val timestamp = Regex("""^\s*(\d{9,}\.\d+)""")
    private val fps = Regex("""FPS=(\d+)/(\d+)""")
    private val stale = Regex("""Stale=(\d+)""")
    private val tear = Regex("""Tear=(\d+)""")
    private val prd = Regex("""Prd=(\d+)ms""")
    private val levels = Regex("""CPU\d*/GPU=(\d+)/(\d+),(\d+)/(\d+)MHz""")
    private val mem = Regex("""Mem=(\d+)MHz""")
    private val free = Regex("""Free=(\d+)MB""")
    private val temp = Regex("""Temp=(-?[\d.]+)C/(-?[\d.]+)C""")
    private val app = Regex("""App=([\d.]+)ms""")
    private val gpuPct = Regex("""GPU%=([\d.]+)""")
    private val cpuPct = Regex("""CPU%=([\d.]+)(?:\(W([\d.]+)\))?""")

    fun parse(line: String): VrApiStats? {
        val f = fps.find(line) ?: return null
        val lv = levels.find(line)
        val t = temp.find(line)
        val c = cpuPct.find(line)
        return VrApiStats(
            fps = f.groupValues[1].toInt(),
            targetFps = f.groupValues[2].toInt(),
            stale = stale.find(line)?.groupValues?.get(1)?.toIntOrNull(),
            tear = tear.find(line)?.groupValues?.get(1)?.toIntOrNull(),
            predictionMs = prd.find(line)?.groupValues?.get(1)?.toIntOrNull(),
            cpuLevel = lv?.groupValues?.get(1)?.toIntOrNull(),
            gpuLevel = lv?.groupValues?.get(2)?.toIntOrNull(),
            cpuMHz = lv?.groupValues?.get(3)?.toIntOrNull(),
            gpuMHz = lv?.groupValues?.get(4)?.toIntOrNull(),
            memMHz = mem.find(line)?.groupValues?.get(1)?.toIntOrNull(),
            freeMemMb = free.find(line)?.groupValues?.get(1)?.toIntOrNull(),
            batteryTempC = t?.groupValues?.get(1)?.toFloatOrNull(),
            sensorTempC = t?.groupValues?.get(2)?.toFloatOrNull()?.takeIf { it > 0f },
            appGpuMs = app.find(line)?.groupValues?.get(1)?.toFloatOrNull(),
            gpuUtil = gpuPct.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let(::normalizeUtil),
            // The worst-core figure (W) tracks main-thread load better than the average.
            cpuUtil = (c?.groupValues?.get(2)?.toFloatOrNull() ?: c?.groupValues?.get(1)?.toFloatOrNull())
                ?.let(::normalizeUtil),
            timestampSec = timestamp.find(line)?.groupValues?.get(1)?.toDoubleOrNull(),
        )
    }

    // Some firmware prints utilization as 0..1, some as 0..100.
    private fun normalizeUtil(v: Float): Float = (if (v > 1.5f) v / 100f else v).coerceIn(0f, 1f)
}
