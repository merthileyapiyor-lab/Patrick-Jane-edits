package com.overdrive.quest.service

import com.overdrive.quest.core.AppGraph
import com.overdrive.quest.core.auto.AutoBoostEngine
import com.overdrive.quest.core.auto.BoostInput
import com.overdrive.quest.core.auto.EngineEventKind
import com.overdrive.quest.core.model.AppSettings
import com.overdrive.quest.core.model.BoostMode
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.monitor.Metrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs inside the foreground service: switches per-game profiles as games come to the front
 * and drives the auto-overclock engine from live telemetry.
 */
class EngineCoordinator(private val scope: CoroutineScope) {
    private val g = AppGraph
    private val lock = Mutex()
    private var job: Job? = null

    private var engine: AutoBoostEngine? = null
    private var engineMode: BoostMode? = null
    private var lastProfile: PerformanceProfile? = null
    private var lastForeground: String? = null
    private var activeAppProfile: String? = null

    fun start() {
        if (job != null) return
        g.metrics.acquire(TAG)
        job = scope.launch {
            launch { g.settings.settingsFlow.collect { s -> lock.withLock { onSettings(s) } } }
            launch { g.metrics.metrics.collect { m -> lock.withLock { onTick(m) } } }
            launch {
                g.shell.state.map { it.ready }.distinctUntilChanged().collect { ready ->
                    if (ready) lock.withLock { engine?.let { applyLevels(it.cpu, it.gpu, "Auto: reconnect") } }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        g.metrics.release(TAG)
    }

    private fun baseProfile(s: AppSettings): PerformanceProfile =
        activeAppProfile?.let { g.settings.appProfiles.value[it]?.profile } ?: s.profile

    private suspend fun onSettings(s: AppSettings) {
        val caps = g.caps
        val profileChanged = lastProfile != null && lastProfile != s.profile
        lastProfile = s.profile

        if (s.auto.enabled) {
            val current = engine
            if (current == null) {
                val base = baseProfile(s)
                engine = AutoBoostEngine(s.auto, caps).also { e ->
                    e.reset(base.cpuLevel, base.gpuLevel)
                    applyLevels(e.cpu, e.gpu, "Auto: engage")
                }
                engineMode = s.auto.mode
                g.boostLog.add(EngineEventKind.ENGAGED)
            } else {
                current.updateConfig(s.auto, caps)
                if (engineMode != s.auto.mode || profileChanged) {
                    val base = baseProfile(s)
                    current.reset(base.cpuLevel, base.gpuLevel)
                    applyLevels(current.cpu, current.gpu, "Auto: reset")
                }
                engineMode = s.auto.mode
            }
        } else if (engine != null) {
            engine = null
            engineMode = null
            val base = baseProfile(s)
            applyLevels(base.cpuLevel, base.gpuLevel, "Auto: release")
            g.boostLog.add(EngineEventKind.DISENGAGED)
        }

        if (!s.perAppProfiles && activeAppProfile != null) {
            activeAppProfile = null
            g.tweaks.applyProfile(s.profile, "Global profile")
        }
    }

    private suspend fun onTick(m: Metrics) {
        if (!m.shellOk) return
        val s = g.settings.settings.value

        val fg = m.foreground
        if (s.perAppProfiles && fg != null && fg != lastForeground) {
            lastForeground = fg
            onForegroundChanged(fg, s)
        }

        val e = engine ?: return
        val decision = e.step(
            BoostInput(
                nowMs = m.timestamp,
                vrActive = m.vrActive,
                fps = m.fps,
                targetFps = m.targetFps,
                gpuUtil = m.gpuUtil,
                cpuUtil = m.cpuUtil,
                socTempC = m.socTempC,
                batteryTempC = m.battery.tempC,
                batteryPct = m.battery.level,
                charging = m.battery.charging,
            ),
        ) ?: return
        applyLevels(decision.cpu, decision.gpu, "Auto: ${decision.reason.name.lowercase()}")
        g.boostLog.add(EngineEventKind.BOOST, decision)
    }

    private suspend fun onForegroundChanged(pkg: String, s: AppSettings) {
        if (pkg == g.app.packageName) return
        val profile = g.settings.appProfiles.value[pkg]
        if (profile != null) {
            activeAppProfile = pkg
            g.tweaks.applyProfile(profile.profile, "Game profile: ${profile.label}")
            engine?.let { e ->
                e.reset(profile.profile.cpuLevel, profile.profile.gpuLevel)
                applyLevels(e.cpu, e.gpu, "Auto: game profile")
            }
            g.boostLog.add(EngineEventKind.APP_PROFILE, appLabel = profile.label)
        } else if (activeAppProfile != null) {
            activeAppProfile = null
            g.tweaks.applyProfile(s.profile, "Global profile")
            engine?.let { e ->
                e.reset(s.profile.cpuLevel, s.profile.gpuLevel)
                applyLevels(e.cpu, e.gpu, "Auto: global profile")
            }
            g.boostLog.add(EngineEventKind.APP_RESTORE)
        }
    }

    private suspend fun applyLevels(cpu: Int?, gpu: Int?, label: String) {
        if (!g.shell.state.value.ready) return
        g.tweaks.setLevels(cpu, gpu, label)
    }

    private companion object {
        const val TAG = "engine"
    }
}
