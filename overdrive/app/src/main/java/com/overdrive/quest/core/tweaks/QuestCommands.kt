package com.overdrive.quest.core.tweaks

import com.overdrive.quest.core.model.CaptureConfig
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile

/** System properties read by the Horizon OS VR runtime. They are cleared on every reboot. */
object QuestProps {
    const val CPU_LEVEL = "debug.oculus.cpuLevel"
    const val GPU_LEVEL = "debug.oculus.gpuLevel"
    const val REFRESH_RATE = "debug.oculus.refreshRate"
    const val TEXTURE_WIDTH = "debug.oculus.textureWidth"
    const val TEXTURE_HEIGHT = "debug.oculus.textureHeight"
    const val FOVEATION_LEVEL = "debug.oculus.foveation.level"
    const val FOVEATION_DYNAMIC = "debug.oculus.foveation.dynamic"

    const val CAPTURE_WIDTH = "debug.oculus.capture.width"
    const val CAPTURE_HEIGHT = "debug.oculus.capture.height"
    const val CAPTURE_FPS = "debug.oculus.capture.fps"
    const val CAPTURE_BITRATE = "debug.oculus.capture.bitrate"
    const val CAPTURE_FULL_RATE = "debug.oculus.fullRateCapture"

    val PROFILE_KEYS = listOf(
        CPU_LEVEL, GPU_LEVEL, REFRESH_RATE, TEXTURE_WIDTH, TEXTURE_HEIGHT,
        FOVEATION_LEVEL, FOVEATION_DYNAMIC,
    )
    val CAPTURE_KEYS = listOf(
        CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS, CAPTURE_BITRATE, CAPTURE_FULL_RATE,
    )
}

/** Builds the shell commands Overdrive runs. Pure so it can be unit tested and exported as ADB. */
object QuestCommands {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val OVR_METRICS_PACKAGE = "com.oculus.ovrmonitormetricsservice"
    const val SHIZUKU_START =
        "sh /storage/emulated/0/Android/data/$SHIZUKU_PACKAGE/start.sh"

    const val KILL_BACKGROUND = "am kill-all"
    const val DROP_CACHES_ROOT = "sync; echo 3 > /proc/sys/vm/drop_caches"
    const val PROXIMITY_DISABLE = "am broadcast -a com.oculus.vrpowermanager.prox_close"
    const val PROXIMITY_RESTORE = "am broadcast -a com.oculus.vrpowermanager.automation_disable"

    fun setprop(key: String, value: String?): String =
        if (value.isNullOrEmpty()) "setprop $key ''" else "setprop $key $value"

    fun levels(cpu: Int?, gpu: Int?): List<String> = listOf(
        setprop(QuestProps.CPU_LEVEL, cpu?.toString()),
        setprop(QuestProps.GPU_LEVEL, gpu?.toString()),
    )

    fun profile(p: PerformanceProfile, caps: DeviceCaps): List<String> {
        val eye = p.resolutionScale?.let { caps.eyeSize(it) }
        return levels(p.cpuLevel, p.gpuLevel) + listOf(
            setprop(QuestProps.REFRESH_RATE, p.refreshRate?.toString()),
            setprop(QuestProps.TEXTURE_WIDTH, eye?.first?.toString()),
            setprop(QuestProps.TEXTURE_HEIGHT, eye?.second?.toString()),
            setprop(QuestProps.FOVEATION_LEVEL, p.foveationLevel?.toString()),
            setprop(QuestProps.FOVEATION_DYNAMIC, p.dynamicFoveation?.let { if (it) "1" else "0" }),
        )
    }

    fun capture(c: CaptureConfig): List<String> = listOf(
        setprop(QuestProps.CAPTURE_WIDTH, c.width?.toString()),
        setprop(QuestProps.CAPTURE_HEIGHT, c.height?.toString()),
        setprop(QuestProps.CAPTURE_FPS, c.fps?.toString()),
        setprop(QuestProps.CAPTURE_BITRATE, c.bitrateMbps?.let { (it * 1_000_000).toString() }),
        setprop(QuestProps.CAPTURE_FULL_RATE, c.fullRate?.let { if (it) "1" else "0" }),
    )

    fun resetAll(): List<String> =
        (QuestProps.PROFILE_KEYS + QuestProps.CAPTURE_KEYS).map { setprop(it, null) }

    fun ovrMetricsOverlay(enable: Boolean): String {
        val action = if (enable) "ENABLE_OVERLAY" else "DISABLE_OVERLAY"
        return "am broadcast -n $OVR_METRICS_PACKAGE/.SettingsBroadcastReceiver " +
            "-a $OVR_METRICS_PACKAGE.$action"
    }

    fun selfGrants(packageName: String, sdkInt: Int): List<String> = buildList {
        add("appops set $packageName SYSTEM_ALERT_WINDOW allow")
        add("appops set $packageName RUN_IN_BACKGROUND allow")
        add("appops set $packageName RUN_ANY_IN_BACKGROUND allow")
        add("dumpsys deviceidle whitelist +$packageName")
        if (sdkInt >= 33) add("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }

    fun forceStop(packageName: String): String = "am force-stop $packageName"

    /** Joins commands so a failing one does not stop the rest. */
    fun script(commands: List<String>): String = commands.joinToString(" ; ")

    /** The same commands as something a user can paste into a PC terminal. */
    fun adbScript(commands: List<String>): String =
        commands.joinToString("\n") { "adb shell \"$it\"" }
}
