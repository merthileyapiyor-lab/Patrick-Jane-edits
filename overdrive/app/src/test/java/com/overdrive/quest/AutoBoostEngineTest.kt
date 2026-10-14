package com.overdrive.quest

import com.overdrive.quest.core.auto.AutoBoostEngine
import com.overdrive.quest.core.auto.BoostInput
import com.overdrive.quest.core.auto.BoostReason
import com.overdrive.quest.core.model.AutoBoostConfig
import com.overdrive.quest.core.model.BoostMode
import com.overdrive.quest.core.model.DeviceCaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoBoostEngineTest {
    private val caps = DeviceCaps.detect("Quest 3", "eureka")
    private var now = 1_000_000L

    private fun input(
        fps: Int? = 90,
        target: Int? = 90,
        gpu: Float = 0.8f,
        cpu: Float = 0.5f,
        soc: Float? = 60f,
        battery: Int = 80,
        charging: Boolean = false,
        vr: Boolean = true,
    ): BoostInput {
        now += 1_000
        return BoostInput(now, vr, fps, target, gpu, cpu, soc, 35f, battery, charging)
    }

    private fun engine(config: AutoBoostConfig = AutoBoostConfig(enabled = true)) =
        AutoBoostEngine(config, caps).also { it.reset(3, 3) }

    @Test
    fun raisesGpuWhenFpsDropsAndGpuIsBusy() {
        val e = engine()
        assertNull(e.step(input()))
        var d = e.step(input(fps = 70, gpu = 0.98f))
        assertNull(d)
        d = e.step(input(fps = 70, gpu = 0.98f))
        assertNull(d)
        d = e.step(input(fps = 70, gpu = 0.98f))
        assertNotNull(d)
        assertEquals(BoostReason.FPS_DROP, d!!.reason)
        assertEquals(4, d.gpu)
        assertEquals(3, d.cpu)
    }

    @Test
    fun raisesCpuWhenCpuBound() {
        val e = engine()
        e.step(input())
        repeat(2) { e.step(input(fps = 60, gpu = 0.4f, cpu = 0.95f)) }
        val d = e.step(input(fps = 60, gpu = 0.4f, cpu = 0.95f))!!
        assertEquals(4, d.cpu)
        assertEquals(3, d.gpu)
    }

    @Test
    fun thermalGuardStepsDownAndHolds() {
        val e = engine()
        e.step(input())
        val d = e.step(input(soc = 90f))!!
        assertEquals(BoostReason.THERMAL, d.reason)
        assertEquals(2, d.gpu)
        // still above (limit - hysteresis): no raise even if FPS drops
        repeat(5) { assertNull(e.step(input(fps = 50, soc = 84f))) }
    }

    @Test
    fun lowersWhenThereIsHeadroom() {
        val e = engine()
        e.step(input())
        val decisions = (1..200).mapNotNull { e.step(input(gpu = 0.3f, cpu = 0.2f)) }
        assertEquals(BoostReason.HEADROOM, decisions.first().reason)
        // The less loaded unit (CPU here) is lowered first.
        assertEquals(2, decisions.first().cpu)
        assertEquals(3, decisions.first().gpu)
        // Sustained headroom keeps stepping down, but never below the floor.
        assertEquals(1, e.cpu)
        assertEquals(1, e.gpu)
    }

    @Test
    fun batteryGuardCapsLevels() {
        val e = AutoBoostEngine(AutoBoostConfig(enabled = true, mode = BoostMode.MAX_SUSTAIN), caps)
        e.reset(null, null)
        assertEquals(5, e.cpu)
        val d = e.step(input(battery = 10))!!
        assertEquals(BoostReason.BATTERY_GUARD, d.reason)
        assertEquals(2, d.cpu)
        assertEquals(2, d.gpu)
    }

    @Test
    fun idleEcoDropsToFloorOutsideVr() {
        val e = engine()
        var d = null as com.overdrive.quest.core.auto.BoostDecision?
        repeat(12) { d = e.step(input(vr = false, fps = null, target = null)) ?: d }
        assertEquals(BoostReason.IDLE, d!!.reason)
        assertEquals(1, e.cpu)
        assertEquals(1, e.gpu)
        val back = e.step(input())!!
        assertEquals(BoostReason.VR_START, back.reason)
        assertEquals(3, back.cpu)
    }

    @Test
    fun maxSustainRecoversAfterCooling() {
        val e = AutoBoostEngine(AutoBoostConfig(enabled = true, mode = BoostMode.MAX_SUSTAIN), caps)
        e.reset(null, null)
        e.step(input())
        e.step(input(soc = 88f))
        assertEquals(4, e.gpu)
        val d = e.step(input(soc = 70f))!!
        assertEquals(BoostReason.THERMAL_RECOVER, d.reason)
        assertEquals(5, e.gpu)
    }
}
