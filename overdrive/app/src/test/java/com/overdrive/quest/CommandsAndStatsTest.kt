package com.overdrive.quest

import com.overdrive.quest.core.model.CaptureConfig
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.model.Preset
import com.overdrive.quest.core.session.SessionSample
import com.overdrive.quest.core.session.SessionStats
import com.overdrive.quest.core.tweaks.QuestCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandsAndStatsTest {
    private val q3 = DeviceCaps.detect("Quest 3", "eureka")

    @Test
    fun detectsDevices() {
        assertEquals("Meta Quest 3", q3.model)
        assertEquals("Meta Quest 3S", DeviceCaps.detect("Quest 3S", "panther").model)
        assertEquals(4, DeviceCaps.detect("Quest 2", "hollywood").maxCpuLevel)
        assertEquals(6, q3.withExtendedRange(true).maxGpuLevel)
    }

    @Test
    fun profileCommandsSetAndClear() {
        val cmds = QuestCommands.profile(PerformanceProfile(cpuLevel = 5, resolutionScale = 1.2f), q3)
        assertTrue(cmds.contains("setprop debug.oculus.cpuLevel 5"))
        assertTrue(cmds.contains("setprop debug.oculus.gpuLevel ''"))
        assertTrue(cmds.contains("setprop debug.oculus.textureWidth 2016"))
        assertTrue(cmds.contains("setprop debug.oculus.textureHeight 2112"))
        assertEquals(7, cmds.size)
    }

    @Test
    fun captureBitrateIsInBitsPerSecond() {
        val cmds = QuestCommands.capture(CaptureConfig(width = 1920, height = 1080, bitrateMbps = 40, fullRate = true))
        assertTrue(cmds.contains("setprop debug.oculus.capture.bitrate 40000000"))
        assertTrue(cmds.contains("setprop debug.oculus.fullRateCapture 1"))
    }

    @Test
    fun adbScriptQuotesEmptyValues() {
        val adb = QuestCommands.adbScript(QuestCommands.levels(null, 3))
        assertEquals("adb shell \"setprop debug.oculus.cpuLevel ''\"\nadb shell \"setprop debug.oculus.gpuLevel 3\"", adb)
    }

    @Test
    fun presetsRoundTrip() {
        Preset.entries.forEach { assertEquals(it, Preset.detect(it.profile(q3), q3)) }
        assertEquals(PerformanceProfile(cpuLevel = 5, gpuLevel = 5), Preset.OVERDRIVE.profile(q3))
    }

    @Test
    fun summarizesSession() {
        val samples = (0 until 200).map { i ->
            SessionSample(
                tMs = i * 1000L,
                fps = if (i < 2) 45 else 90,
                targetFps = 90,
                socTempC = 60f + i / 10f,
                batteryPct = 100 - i / 40,
                app = "com.game",
            )
        }
        val s = SessionStats.summarize("x", 0L, samples)
        assertEquals(199, s.durationSec)
        assertEquals(45f, s.onePercentLowFps!!, 0.01f)
        assertEquals(45, s.minFps)
        assertEquals(99f, s.smoothnessPct!!, 0.01f)
        assertEquals(4, s.batteryUsedPct)
        assertEquals("com.game", s.app)
        assertTrue(SessionStats.toCsv(samples).startsWith(SessionStats.CSV_HEADER))
    }
}
