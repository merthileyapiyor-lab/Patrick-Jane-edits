package com.overdrive.quest

import com.overdrive.quest.core.monitor.SnapshotProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotProbeTest {
    private val raw = """
        ##CPU
        cpu0 1785600 2054400 1
        cpu1 1785600 2054400 1
        cpu2 - - 0
        cpu3 2054400 2054400 1
        ##GPU
        clk 599000000
        max 599000000
        busy 87 %
        ##THERMAL
        cpu-0-0-usr|61200
        gpuss-0-usr|66800
        skin-therm|38100
        battery|36
        bogus|-273000
        ##VRAPI
        1728918000.512  2310  2401 I VrApi   : FPS=90/90,CPU4/GPU=4/5,1958/599MHz,GPU%=0.87,CPU%=0.42(W0.71)
        ##PROPS
        [debug.oculus.cpuLevel]: [4]
        [debug.oculus.gpuLevel]: [5]
        [debug.oculus.textureWidth]: []
        ##FG
          topResumedActivity=ActivityRecord{4c1d u0 com.beatgames.beatsaber/com.unity3d.player.UnityPlayerActivity t42}
        ##NOW
        1728918002
        ##END
    """.trimIndent()

    @Test
    fun parsesAllSections() {
        val s = SnapshotProbe.parse(raw)
        assertEquals(4, s.cores.size)
        assertEquals(1785, s.cores[0].curMHz)
        assertFalse(s.cores[2].online)
        assertNull(s.cores[2].curMHz)
        assertEquals(2054, s.cpuPeakMHz)
        assertEquals(599, s.gpuMHz)
        assertEquals(87, s.gpuBusyPct)
        assertEquals(66.8f, s.socTempC!!, 0.01f)
        assertEquals(38.1f, s.skinTempC!!, 0.01f)
        assertEquals(4, s.zones.size)
        assertEquals(90, s.vr!!.fps)
        assertEquals("4", s.props["debug.oculus.cpuLevel"])
        assertEquals("", s.props["debug.oculus.textureWidth"])
        assertEquals("com.beatgames.beatsaber", s.foreground)
        assertEquals(1728918002L, s.epochSec)
        assertTrue(s.vrFresh())
    }

    @Test
    fun staleVrApiLineIsNotFresh() {
        val s = SnapshotProbe.parse(raw.replace("1728918002", "1728918030"))
        assertFalse(s.vrFresh())
    }

    @Test
    fun scriptMentionsEverySection() {
        val script = SnapshotProbe.script(includeForeground = true)
        listOf("##CPU", "##GPU", "##THERMAL", "##VRAPI", "##PROPS", "##FG", "##NOW").forEach {
            assertTrue(it, script.contains(it))
        }
        assertFalse(SnapshotProbe.script(includeForeground = false).contains("##FG"))
    }
}
