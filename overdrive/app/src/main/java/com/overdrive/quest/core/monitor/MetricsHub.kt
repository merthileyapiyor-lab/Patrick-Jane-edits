package com.overdrive.quest.core.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.overdrive.quest.core.shell.ShellManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Samples the headset once per interval while anyone (screen, service, recorder) holds it.
 * One privileged shell call per tick gathers clocks, thermals, VrApi stats and props.
 */
class MetricsHub(
    private val context: Context,
    private val shell: ShellManager,
    private val scope: CoroutineScope,
    private val intervalMs: () -> Long,
) {
    private val _metrics = MutableStateFlow(Metrics())
    val metrics: StateFlow<Metrics> = _metrics.asStateFlow()

    private val _history = MutableStateFlow(History())
    val history: StateFlow<History> = _history.asStateFlow()

    /** Clock speeds observed per performance level, learned from VrApi while games run. */
    private val _cpuClocks = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val cpuClocks: StateFlow<Map<Int, Int>> = _cpuClocks.asStateFlow()
    private val _gpuClocks = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val gpuClocks: StateFlow<Map<Int, Int>> = _gpuClocks.asStateFlow()

    private val holders = mutableSetOf<String>()
    private var job: Job? = null
    private var tick = 0L
    private var lastForeground: String? = null

    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun acquire(tag: String) {
        synchronized(holders) {
            holders += tag
            if (job?.isActive != true) job = scope.launch(Dispatchers.IO) { loop() }
        }
    }

    fun release(tag: String) {
        synchronized(holders) {
            holders -= tag
            if (holders.isEmpty()) {
                job?.cancel()
                job = null
            }
        }
    }

    /** Forces an immediate sample, e.g. right after applying a profile. */
    fun pokeSoon() {
        scope.launch(Dispatchers.IO) {
            delay(600)
            runCatching { sampleOnce(includeForeground = false) }
        }
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val started = SystemClock.elapsedRealtime()
            runCatching { sampleOnce(includeForeground = tick % 3 == 0L) }
            tick++
            val elapsed = SystemClock.elapsedRealtime() - started
            delay((intervalMs().coerceAtLeast(250L) - elapsed).coerceAtLeast(100L))
        }
    }

    private suspend fun sampleOnce(includeForeground: Boolean) {
        val battery = readBattery()
        val ram = readRam()
        val privileged = shell.state.value.ready
        val snapshot = if (privileged) {
            val r = shell.exec(SnapshotProbe.script(includeForeground), log = false, timeoutMs = 5_000)
            if (r.out.contains("##END")) SnapshotProbe.parse(r.out) else null
        } else {
            readLocalSnapshot()
        }
        if (privileged && includeForeground && snapshot != null) lastForeground = snapshot.foreground
        if (!privileged) lastForeground = null

        val m = Metrics(
            timestamp = System.currentTimeMillis(),
            shellOk = privileged && snapshot != null,
            battery = battery,
            ram = ram,
            snapshot = snapshot,
            vrActive = snapshot?.vrFresh() == true,
            foreground = lastForeground,
        )
        learnClocks(m)
        _metrics.value = m
        _history.update { it.push(m) }
    }

    private fun learnClocks(m: Metrics) {
        val vr = m.vr ?: return
        val cl = vr.cpuLevel
        val cm = vr.cpuMHz
        if (cl != null && cm != null && _cpuClocks.value[cl] != cm) _cpuClocks.update { it + (cl to cm) }
        val gl = vr.gpuLevel
        val gm = vr.gpuMHz
        if (gl != null && gm != null && _gpuClocks.value[gl] != gm) _gpuClocks.update { it + (gl to gm) }
    }

    private fun readBattery(): BatteryStat {
        val intent: Intent? = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = intent?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) l * 100 / s else null
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val current = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()?.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { it / 1000 }
        return BatteryStat(
            level = level,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            tempC = temp,
            currentMa = current,
            voltageMv = voltage,
        )
    }

    private fun readRam(): RamStat? = runCatching {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info) ?: return null
        RamStat(totalMb = info.totalMem / MB, availMb = info.availMem / MB)
    }.getOrNull()

    /** Best effort without Shizuku: some kernels let apps read cpufreq and thermal zones. */
    private fun readLocalSnapshot(): DeviceSnapshot? = runCatching {
        val cpuDirs = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
            .orEmpty().sortedBy { it.name.removePrefix("cpu").toInt() }
        val cores = cpuDirs.map { dir ->
            CoreFreq(
                index = dir.name.removePrefix("cpu").toInt(),
                curMHz = readLong(File(dir, "cpufreq/scaling_cur_freq"))?.let { (it / 1000).toInt() },
                maxMHz = readLong(File(dir, "cpufreq/cpuinfo_max_freq"))?.let { (it / 1000).toInt() },
                online = readLong(File(dir, "online"))?.let { it != 0L } ?: true,
            )
        }
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            .orEmpty().mapNotNull { z ->
                val type = runCatching { File(z, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
                val raw = readLong(File(z, "temp"))?.toFloat() ?: return@mapNotNull null
                val c = if (kotlin.math.abs(raw) >= 1000f) raw / 1000f else raw
                if (c < 5f || c > 140f) null else ThermalZone(type, c)
            }
        DeviceSnapshot(cores = cores, zones = zones)
    }.getOrNull()

    private fun readLong(f: File): Long? = runCatching { f.readText().trim().toLong() }.getOrNull()

    private companion object {
        const val MB = 1024L * 1024L
    }
}
