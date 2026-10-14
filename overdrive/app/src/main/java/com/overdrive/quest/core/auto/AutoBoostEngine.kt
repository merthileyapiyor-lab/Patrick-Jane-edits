package com.overdrive.quest.core.auto

import com.overdrive.quest.core.model.Aggressiveness
import com.overdrive.quest.core.model.AutoBoostConfig
import com.overdrive.quest.core.model.BoostMode
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.ThermalSource

enum class BoostReason { FPS_DROP, HEADROOM, THERMAL, THERMAL_RECOVER, BATTERY_GUARD, IDLE, VR_START, SUSTAIN }

data class BoostInput(
    val nowMs: Long,
    val vrActive: Boolean,
    val fps: Int?,
    val targetFps: Int?,
    val gpuUtil: Float?,
    val cpuUtil: Float?,
    val socTempC: Float?,
    val batteryTempC: Float?,
    val batteryPct: Int?,
    val charging: Boolean,
)

data class BoostDecision(
    val cpu: Int,
    val gpu: Int,
    val fromCpu: Int,
    val fromGpu: Int,
    val reason: BoostReason,
    val fps: Int? = null,
    val targetFps: Int? = null,
    val tempC: Float? = null,
)

/**
 * The "auto overclock" brain. Fed one sample per tick, it decides when to raise or lower the
 * CPU/GPU levels. It keeps no Android state so every rule is unit tested.
 */
class AutoBoostEngine(
    private var config: AutoBoostConfig,
    private var caps: DeviceCaps,
) {
    var cpu: Int = caps.maxCpuLevel / 2
        private set
    var gpu: Int = caps.maxGpuLevel / 2
        private set

    private var lowStreak = 0
    private var headroomStreak = 0
    private var lastChangeMs = Long.MIN_VALUE / 2
    private var thermalHold = false
    private var wasVrActive = false
    private var lastVrMs = Long.MIN_VALUE / 2
    private var baseCpu = cpu
    private var baseGpu = gpu

    private data class Tuning(
        val lowNeeded: Int,
        val dropRatio: Float,
        val raiseCooldownMs: Long,
        val headroomNeeded: Int,
        val lowerCooldownMs: Long,
    )

    private val tuning: Tuning
        get() {
            val t = when (config.aggressiveness) {
                Aggressiveness.GENTLE -> Tuning(5, 0.90f, 6_000, 30, 15_000)
                Aggressiveness.NORMAL -> Tuning(3, 0.93f, 4_000, 20, 10_000)
                Aggressiveness.AGGRESSIVE -> Tuning(2, 0.96f, 2_000, 12, 6_000)
            }
            return if (config.mode == BoostMode.ECO_SMART) {
                t.copy(headroomNeeded = (t.headroomNeeded / 2).coerceAtLeast(4))
            } else {
                t
            }
        }

    val floor: Int get() = config.floorLevel.coerceIn(0, minOf(caps.maxCpuLevel, caps.maxGpuLevel))
    val cpuCeiling: Int get() = (config.ceilingLevel ?: caps.maxCpuLevel).coerceIn(floor, caps.maxCpuLevel)
    val gpuCeiling: Int get() = (config.ceilingLevel ?: caps.maxGpuLevel).coerceIn(floor, caps.maxGpuLevel)

    fun updateConfig(newConfig: AutoBoostConfig, newCaps: DeviceCaps) {
        config = newConfig
        caps = newCaps
        cpu = cpu.coerceIn(floor, cpuCeiling)
        gpu = gpu.coerceIn(floor, gpuCeiling)
    }

    /** Sets the starting point, e.g. the active profile's levels. */
    fun reset(startCpu: Int?, startGpu: Int?) {
        baseCpu = startModeLevel(startCpu, cpuCeiling)
        baseGpu = startModeLevel(startGpu, gpuCeiling)
        cpu = baseCpu
        gpu = baseGpu
        lowStreak = 0
        headroomStreak = 0
        thermalHold = false
    }

    private fun startModeLevel(requested: Int?, ceiling: Int): Int = when (config.mode) {
        BoostMode.MAX_SUSTAIN -> ceiling
        BoostMode.ECO_SMART -> (requested ?: floor + 1).coerceIn(floor, ceiling).coerceAtMost(floor + 1)
        BoostMode.ADAPTIVE -> (requested ?: (ceiling + floor + 1) / 2).coerceIn(floor, ceiling)
    }

    fun step(input: BoostInput): BoostDecision? {
        val t = tuning
        val temp = when (config.thermalSource) {
            ThermalSource.SOC -> input.socTempC ?: input.batteryTempC
            ThermalSource.BATTERY -> input.batteryTempC
        }
        val limit = config.limitC.toFloat()
        val sinceChange = input.nowMs - lastChangeMs

        // 1. Thermal guard always wins.
        if (temp != null && temp >= limit) {
            thermalHold = true
            lowStreak = 0
            headroomStreak = 0
            if (sinceChange < THERMAL_COOLDOWN_MS || (cpu <= floor && gpu <= floor)) return null
            return if (gpu >= cpu && gpu > floor) {
                change(input, cpu, gpu - 1, BoostReason.THERMAL, temp)
            } else if (cpu > floor) {
                change(input, cpu - 1, gpu, BoostReason.THERMAL, temp)
            } else {
                change(input, cpu, gpu - 1, BoostReason.THERMAL, temp)
            }
        }
        if (thermalHold) {
            if (temp != null && temp > limit - THERMAL_HYSTERESIS_C) return null
            thermalHold = false
            if (config.mode == BoostMode.MAX_SUSTAIN && input.vrActive) {
                return change(input, cpuCap(input), gpuCap(input), BoostReason.THERMAL_RECOVER, temp)
            }
        }

        // 2. Battery guard caps the ceiling while discharging.
        val capCpu = cpuCap(input)
        val capGpu = gpuCap(input)
        if (cpu > capCpu || gpu > capGpu) {
            return change(input, minOf(cpu, capCpu), minOf(gpu, capGpu), BoostReason.BATTERY_GUARD, temp)
        }

        // 3. Outside VR: optionally idle at the floor.
        if (!input.vrActive) {
            lowStreak = 0
            headroomStreak = 0
            wasVrActive = false
            if (config.idleEco && input.nowMs - lastVrMs > IDLE_DELAY_MS && (cpu != floor || gpu != floor)) {
                return change(input, floor, floor, BoostReason.IDLE, temp)
            }
            return null
        }
        lastVrMs = input.nowMs

        // 4. A game just started rendering: jump to the mode's starting point.
        if (!wasVrActive) {
            wasVrActive = true
            val startCpu = if (config.mode == BoostMode.MAX_SUSTAIN) capCpu else baseCpu.coerceIn(floor, capCpu)
            val startGpu = if (config.mode == BoostMode.MAX_SUSTAIN) capGpu else baseGpu.coerceIn(floor, capGpu)
            if (startCpu != cpu || startGpu != gpu) {
                return change(input, startCpu, startGpu, BoostReason.VR_START, temp)
            }
        }

        if (config.mode == BoostMode.MAX_SUSTAIN) {
            if ((cpu < capCpu || gpu < capGpu) && sinceChange >= t.raiseCooldownMs) {
                return change(input, capCpu, capGpu, BoostReason.SUSTAIN, temp)
            }
            return null
        }

        // 5. Adaptive / eco: chase the target frame rate.
        val fps = input.fps
        val target = input.targetFps
        if (fps == null || target == null || target <= 0) return null
        val ratio = fps.toFloat() / target
        val gpuUtil = input.gpuUtil ?: 0f
        val cpuUtil = input.cpuUtil ?: 0f

        lowStreak = if (ratio < t.dropRatio) lowStreak + 1 else 0
        headroomStreak = if (ratio >= 0.98f && gpuUtil < HEADROOM_UTIL && cpuUtil < HEADROOM_UTIL) {
            headroomStreak + 1
        } else {
            0
        }

        if (lowStreak >= t.lowNeeded && sinceChange >= t.raiseCooldownMs) {
            val gpuFirst = gpuUtil >= cpuUtil
            val next = when {
                gpuFirst && gpu < capGpu -> cpu to gpu + 1
                !gpuFirst && cpu < capCpu -> cpu + 1 to gpu
                gpu < capGpu -> cpu to gpu + 1
                cpu < capCpu -> cpu + 1 to gpu
                else -> null
            }
            lowStreak = 0
            if (next != null) return change(input, next.first, next.second, BoostReason.FPS_DROP, temp)
        }

        if (headroomStreak >= t.headroomNeeded && sinceChange >= t.lowerCooldownMs) {
            headroomStreak = 0
            val next = when {
                cpuUtil <= gpuUtil && cpu > floor -> cpu - 1 to gpu
                gpu > floor -> cpu to gpu - 1
                cpu > floor -> cpu - 1 to gpu
                else -> null
            }
            if (next != null) return change(input, next.first, next.second, BoostReason.HEADROOM, temp)
        }
        return null
    }

    private fun batteryCapped(input: BoostInput): Boolean =
        config.batteryGuard && !input.charging &&
            (input.batteryPct ?: 100) <= config.batteryGuardPct

    private fun cpuCap(input: BoostInput): Int =
        if (batteryCapped(input)) minOf(cpuCeiling, maxOf(floor, BATTERY_GUARD_LEVEL)) else cpuCeiling

    private fun gpuCap(input: BoostInput): Int =
        if (batteryCapped(input)) minOf(gpuCeiling, maxOf(floor, BATTERY_GUARD_LEVEL)) else gpuCeiling

    private fun change(input: BoostInput, newCpu: Int, newGpu: Int, reason: BoostReason, temp: Float?): BoostDecision? {
        val c = newCpu.coerceIn(0, caps.maxCpuLevel)
        val g = newGpu.coerceIn(0, caps.maxGpuLevel)
        if (c == cpu && g == gpu) return null
        val decision = BoostDecision(
            cpu = c, gpu = g, fromCpu = cpu, fromGpu = gpu, reason = reason,
            fps = input.fps, targetFps = input.targetFps, tempC = temp,
        )
        cpu = c
        gpu = g
        lastChangeMs = input.nowMs
        return decision
    }

    companion object {
        const val THERMAL_COOLDOWN_MS = 4_000L
        const val THERMAL_HYSTERESIS_C = 3f
        const val HEADROOM_UTIL = 0.55f
        const val IDLE_DELAY_MS = 8_000L
        const val BATTERY_GUARD_LEVEL = 2
    }
}
