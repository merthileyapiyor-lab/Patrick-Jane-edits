package com.overdrive.quest.core.monitor

import com.overdrive.quest.core.tweaks.QuestProps

data class BatteryStat(
    val level: Int? = null,
    val charging: Boolean = false,
    val tempC: Float? = null,
    val currentMa: Int? = null,
    val voltageMv: Int? = null,
)

data class RamStat(val totalMb: Long, val availMb: Long) {
    val usedMb: Long get() = (totalMb - availMb).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalMb > 0) usedMb.toFloat() / totalMb else 0f
}

/** One telemetry tick, merged from the privileged probe and regular Android APIs. */
data class Metrics(
    val timestamp: Long = 0L,
    val shellOk: Boolean = false,
    val battery: BatteryStat = BatteryStat(),
    val ram: RamStat? = null,
    val snapshot: DeviceSnapshot? = null,
    val vrActive: Boolean = false,
    val foreground: String? = null,
) {
    val vr: VrApiStats? get() = if (vrActive) snapshot?.vr else null
    val fps: Int? get() = vr?.fps
    val targetFps: Int? get() = vr?.targetFps
    val cpuMHz: Int? get() = vr?.cpuMHz ?: snapshot?.cpuPeakMHz
    val gpuMHz: Int? get() = vr?.gpuMHz ?: snapshot?.gpuMHz
    val cpuMaxMHz: Int? get() = snapshot?.cpuMaxMHz
    val gpuMaxMHz: Int? get() = snapshot?.gpuMaxMHz
    val gpuUtil: Float? get() = vr?.gpuUtil ?: snapshot?.gpuBusyPct?.let { it / 100f }
    val cpuUtil: Float? get() = vr?.cpuUtil
    val socTempC: Float? get() = snapshot?.socTempC
    val cpuLevel: Int? get() = vr?.cpuLevel ?: propInt(QuestProps.CPU_LEVEL)
    val gpuLevel: Int? get() = vr?.gpuLevel ?: propInt(QuestProps.GPU_LEVEL)

    fun prop(key: String): String? = snapshot?.props?.get(key)?.takeIf { it.isNotEmpty() }
    fun propInt(key: String): Int? = prop(key)?.toIntOrNull()
}

/** Rolling window used by the charts. */
data class History(
    val fps: List<Float?> = emptyList(),
    val gpuUtil: List<Float?> = emptyList(),
    val cpuUtil: List<Float?> = emptyList(),
    val socTemp: List<Float?> = emptyList(),
    val batteryTemp: List<Float?> = emptyList(),
    val cpuMHz: List<Float?> = emptyList(),
    val gpuMHz: List<Float?> = emptyList(),
) {
    fun push(m: Metrics, capacity: Int = CAPACITY): History = History(
        fps = fps.append(m.fps?.toFloat(), capacity),
        gpuUtil = gpuUtil.append(m.gpuUtil, capacity),
        cpuUtil = cpuUtil.append(m.cpuUtil, capacity),
        socTemp = socTemp.append(m.socTempC, capacity),
        batteryTemp = batteryTemp.append(m.battery.tempC, capacity),
        cpuMHz = cpuMHz.append(m.cpuMHz?.toFloat(), capacity),
        gpuMHz = gpuMHz.append(m.gpuMHz?.toFloat(), capacity),
    )

    private fun List<Float?>.append(v: Float?, capacity: Int): List<Float?> =
        (if (size >= capacity) drop(size - capacity + 1) else this) + v

    companion object {
        const val CAPACITY = 120
    }
}
