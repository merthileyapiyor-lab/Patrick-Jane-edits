package com.overdrive.quest.core.monitor

data class CoreFreq(val index: Int, val curMHz: Int?, val maxMHz: Int?, val online: Boolean)

data class ThermalZone(val type: String, val tempC: Float)

/** Everything one privileged probe returns. */
data class DeviceSnapshot(
    val cores: List<CoreFreq> = emptyList(),
    val gpuMHz: Int? = null,
    val gpuMaxMHz: Int? = null,
    val gpuBusyPct: Int? = null,
    val zones: List<ThermalZone> = emptyList(),
    val vr: VrApiStats? = null,
    val props: Map<String, String> = emptyMap(),
    val epochSec: Long? = null,
    val foreground: String? = null,
) {
    val socTempC: Float? = zones.filter { isSocZone(it.type) }.maxOfOrNull { it.tempC }
    val skinTempC: Float? = zones.filter { isSkinZone(it.type) }.maxOfOrNull { it.tempC }
    val cpuPeakMHz: Int? = cores.filter { it.online }.mapNotNull { it.curMHz }.maxOrNull()
    val cpuMaxMHz: Int? = cores.mapNotNull { it.maxMHz }.maxOrNull()

    /** True when the last VrApi line is recent, i.e. an immersive app is rendering right now. */
    fun vrFresh(maxAgeSec: Double = 3.5): Boolean {
        val ts = vr?.timestampSec ?: return false
        val now = epochSec ?: return false
        return now - ts <= maxAgeSec
    }

    companion object {
        private val socPattern = Regex("cpu|gpu|tsens|soc|apc|cluster|nsp|ddr", RegexOption.IGNORE_CASE)
        private val skinPattern = Regex("skin|quiet|sys-therm|xo-therm|case", RegexOption.IGNORE_CASE)
        fun isSocZone(type: String) = socPattern.containsMatchIn(type)
        fun isSkinZone(type: String) = skinPattern.containsMatchIn(type)
    }
}

/** Shell script + parser for one telemetry tick. Kept pure so the parsing is unit tested. */
object SnapshotProbe {
    private const val GPU = "/sys/class/kgsl/kgsl-3d0"

    fun script(includeForeground: Boolean): String = buildString {
        append("echo '##CPU'; ")
        append("for c in /sys/devices/system/cpu/cpu[0-9]*; do ")
        append("f=\$(cat \$c/cpufreq/scaling_cur_freq 2>/dev/null); ")
        append("m=\$(cat \$c/cpufreq/cpuinfo_max_freq 2>/dev/null); ")
        append("o=\$(cat \$c/online 2>/dev/null); ")
        append("echo \"\${c##*/} \${f:--} \${m:--} \${o:-1}\"; done; ")
        append("echo '##GPU'; ")
        append("echo \"clk \$(cat $GPU/gpuclk 2>/dev/null || cat $GPU/devfreq/cur_freq 2>/dev/null)\"; ")
        append("echo \"max \$(cat $GPU/max_gpuclk 2>/dev/null)\"; ")
        append("echo \"busy \$(cat $GPU/gpu_busy_percentage 2>/dev/null)\"; ")
        append("echo '##THERMAL'; ")
        append("for z in /sys/class/thermal/thermal_zone*; do ")
        append("echo \"\$(cat \$z/type 2>/dev/null)|\$(cat \$z/temp 2>/dev/null)\"; done; ")
        append("echo '##VRAPI'; ")
        append("logcat -d -v epoch -T \"\$((\$(date +%s) - 5)).000\" -s VrApi:I 2>/dev/null | grep 'FPS=' | tail -n 1; ")
        append("echo '##PROPS'; ")
        append("getprop | grep 'debug.oculus'; ")
        if (includeForeground) {
            append("echo '##FG'; ")
            append("dumpsys activity activities 2>/dev/null | grep -m 1 -E 'topResumedActivity|mResumedActivity'; ")
        }
        append("echo '##NOW'; date +%s; echo '##END'")
    }

    private val propLine = Regex("""^\[([^\]]+)]:\s*\[([^\]]*)]""")
    private val fgLine = Regex("""u\d+\s+([A-Za-z0-9_.]+)/""")

    fun parse(raw: String): DeviceSnapshot {
        val sections = mutableMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("##")) {
                current = sections.getOrPut(line.removePrefix("##").trim()) { mutableListOf() }
            } else if (line.isNotBlank()) {
                current?.add(line)
            }
        }

        val cores = sections["CPU"].orEmpty().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val idx = parts.getOrNull(0)?.removePrefix("cpu")?.toIntOrNull() ?: return@mapNotNull null
            CoreFreq(
                index = idx,
                curMHz = parts.getOrNull(1)?.toLongOrNull()?.let { (it / 1000).toInt() },
                maxMHz = parts.getOrNull(2)?.toLongOrNull()?.let { (it / 1000).toInt() },
                online = parts.getOrNull(3) != "0",
            )
        }.sortedBy { it.index }

        val gpu = sections["GPU"].orEmpty().associate { line ->
            val (k, v) = line.trim().split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            k to v.trim()
        }

        val zones = sections["THERMAL"].orEmpty().mapNotNull { line ->
            val type = line.substringBefore('|').trim()
            val t = line.substringAfter('|', "").trim().toFloatOrNull() ?: return@mapNotNull null
            val c = if (kotlin.math.abs(t) >= 1000f) t / 1000f else t
            if (type.isEmpty() || c < 5f || c > 140f) null else ThermalZone(type, c)
        }

        val props = sections["PROPS"].orEmpty().mapNotNull { line ->
            propLine.find(line.trim())?.let { it.groupValues[1] to it.groupValues[2] }
        }.toMap()

        return DeviceSnapshot(
            cores = cores,
            gpuMHz = gpu["clk"]?.toLongOrNull()?.let(::hzToMHz),
            gpuMaxMHz = gpu["max"]?.toLongOrNull()?.let(::hzToMHz),
            gpuBusyPct = gpu["busy"]?.filter { it.isDigit() }?.toIntOrNull(),
            zones = zones,
            vr = sections["VRAPI"]?.lastOrNull()?.let(VrApiParser::parse),
            props = props,
            epochSec = sections["NOW"]?.firstOrNull()?.trim()?.toLongOrNull(),
            foreground = sections["FG"]?.firstOrNull()?.let { fgLine.find(it)?.groupValues?.get(1) },
        )
    }

    // kgsl reports Hz; devfreq on some kernels too. Anything small is already MHz.
    private fun hzToMHz(v: Long): Int = if (v > 100_000) (v / 1_000_000).toInt() else v.toInt()
}
