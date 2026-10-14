package com.overdrive.quest.core.model

import kotlinx.serialization.Serializable

/**
 * A set of performance overrides. A null field means "leave it to the game / stock runtime",
 * and applying the profile clears that override.
 */
@Serializable
data class PerformanceProfile(
    val cpuLevel: Int? = null,
    val gpuLevel: Int? = null,
    val refreshRate: Int? = null,
    val resolutionScale: Float? = null,
    val foveationLevel: Int? = null,
    val dynamicFoveation: Boolean? = null,
) {
    val isStock: Boolean
        get() = cpuLevel == null && gpuLevel == null && refreshRate == null &&
            resolutionScale == null && foveationLevel == null && dynamicFoveation == null

    /** Settings that only take effect when the game is (re)launched. */
    fun displayDiffers(other: PerformanceProfile): Boolean =
        refreshRate != other.refreshRate || resolutionScale != other.resolutionScale ||
            foveationLevel != other.foveationLevel || dynamicFoveation != other.dynamicFoveation

    fun clampedTo(caps: DeviceCaps): PerformanceProfile = copy(
        cpuLevel = cpuLevel?.coerceIn(0, caps.maxCpuLevel),
        gpuLevel = gpuLevel?.coerceIn(0, caps.maxGpuLevel),
        resolutionScale = resolutionScale?.coerceIn(DeviceCaps.MIN_SCALE, DeviceCaps.MAX_SCALE),
        foveationLevel = foveationLevel?.coerceIn(0, 4),
    )
}

enum class Preset {
    ECO, BALANCED, PERFORMANCE, OVERDRIVE, CLARITY;

    fun profile(caps: DeviceCaps): PerformanceProfile {
        val cpuMax = caps.maxCpuLevel
        val gpuMax = caps.maxGpuLevel
        return when (this) {
            ECO -> PerformanceProfile(
                cpuLevel = 1, gpuLevel = 1, foveationLevel = 3, dynamicFoveation = true,
            )
            BALANCED -> PerformanceProfile(cpuLevel = (cpuMax + 1) / 2, gpuLevel = (gpuMax + 1) / 2)
            PERFORMANCE -> PerformanceProfile(cpuLevel = cpuMax - 1, gpuLevel = gpuMax - 1)
            OVERDRIVE -> PerformanceProfile(cpuLevel = cpuMax, gpuLevel = gpuMax)
            CLARITY -> PerformanceProfile(
                cpuLevel = cpuMax - 1, gpuLevel = gpuMax, resolutionScale = 1.3f,
                foveationLevel = 1, dynamicFoveation = false,
            )
        }
    }

    companion object {
        fun detect(profile: PerformanceProfile, caps: DeviceCaps): Preset? =
            entries.firstOrNull { it.profile(caps) == profile }
    }
}

data class DeviceCaps(
    val model: String,
    val codename: String,
    val isQuest: Boolean,
    val maxCpuLevel: Int,
    val maxGpuLevel: Int,
    val refreshRates: List<Int>,
    val baseEyeWidth: Int,
    val baseEyeHeight: Int,
) {
    fun withExtendedRange(extended: Boolean): DeviceCaps =
        if (extended) copy(maxCpuLevel = maxCpuLevel + 1, maxGpuLevel = maxGpuLevel + 1) else this

    fun eyeSize(scale: Float): Pair<Int, Int> =
        roundTo8(baseEyeWidth * scale) to roundTo8(baseEyeHeight * scale)

    private fun roundTo8(v: Float): Int = (Math.round(v / 8f) * 8).coerceAtLeast(8)

    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 1.7f

        fun detect(model: String, device: String): DeviceCaps {
            val m = model.lowercase()
            val d = device.lowercase()
            return when {
                d == "panther" || m.contains("quest 3s") -> DeviceCaps(
                    "Meta Quest 3S", device, true, 5, 5, listOf(72, 80, 90, 120), 1440, 1584,
                )
                d == "eureka" || m.contains("quest 3") -> DeviceCaps(
                    "Meta Quest 3", device, true, 5, 5, listOf(72, 80, 90, 120), 1680, 1760,
                )
                d == "seacliff" || m.contains("quest pro") -> DeviceCaps(
                    "Meta Quest Pro", device, true, 4, 4, listOf(72, 90), 1800, 1920,
                )
                d == "hollywood" || m.contains("quest 2") -> DeviceCaps(
                    "Meta Quest 2", device, true, 4, 4, listOf(60, 72, 80, 90, 120), 1440, 1584,
                )
                m.contains("quest") -> DeviceCaps(
                    model, device, true, 5, 5, listOf(72, 90, 120), 1680, 1760,
                )
                else -> DeviceCaps(
                    model.ifBlank { "Android" }, device, false, 5, 5, listOf(72, 90, 120), 1680, 1760,
                )
            }
        }
    }
}

@Serializable
enum class BoostMode { ADAPTIVE, MAX_SUSTAIN, ECO_SMART }

@Serializable
enum class Aggressiveness { GENTLE, NORMAL, AGGRESSIVE }

@Serializable
enum class ThermalSource { SOC, BATTERY }

@Serializable
data class AutoBoostConfig(
    val enabled: Boolean = false,
    val mode: BoostMode = BoostMode.ADAPTIVE,
    val aggressiveness: Aggressiveness = Aggressiveness.NORMAL,
    val thermalSource: ThermalSource = ThermalSource.SOC,
    val socLimitC: Int = 85,
    val batteryLimitC: Int = 44,
    val batteryGuard: Boolean = true,
    val batteryGuardPct: Int = 20,
    val floorLevel: Int = 1,
    val ceilingLevel: Int? = null,
    val idleEco: Boolean = true,
) {
    val limitC: Int get() = if (thermalSource == ThermalSource.SOC) socLimitC else batteryLimitC
}

@Serializable
enum class HudStyle { MINIMAL, STANDARD, PRO }

@Serializable
enum class HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Serializable
data class OverlayConfig(
    val enabled: Boolean = false,
    val style: HudStyle = HudStyle.STANDARD,
    val opacity: Float = 0.92f,
    val scale: Float = 1f,
    val clickThrough: Boolean = false,
    val corner: HudCorner = HudCorner.TOP_LEFT,
    val x: Int = 40,
    val y: Int = 40,
    val showFps: Boolean = true,
    val showCpu: Boolean = true,
    val showGpu: Boolean = true,
    val showTemp: Boolean = true,
    val showBattery: Boolean = true,
    val showRam: Boolean = false,
)

@Serializable
enum class AccentTheme { NEON, PLASMA, TOXIC, SOLAR, ICE }

@Serializable
data class CaptureConfig(
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val bitrateMbps: Int? = null,
    val fullRate: Boolean? = null,
) {
    val isStock: Boolean
        get() = width == null && height == null && fps == null && bitrateMbps == null && fullRate == null
}

@Serializable
data class AppSettings(
    val profile: PerformanceProfile = PerformanceProfile(),
    val applyOnConnect: Boolean = true,
    val auto: AutoBoostConfig = AutoBoostConfig(),
    val overlay: OverlayConfig = OverlayConfig(),
    val accent: AccentTheme = AccentTheme.NEON,
    val reduceMotion: Boolean = false,
    val sampleIntervalMs: Long = 1000L,
    val extendedRange: Boolean = false,
    val perAppProfiles: Boolean = true,
    val capture: CaptureConfig = CaptureConfig(),
    val useRoot: Boolean = false,
)

@Serializable
data class AppProfile(
    val packageName: String,
    val label: String,
    val profile: PerformanceProfile,
)
