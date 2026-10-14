package com.overdrive.quest

import com.overdrive.quest.core.monitor.VrApiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VrApiParserTest {
    private val line = "1728918000.512  2310  2401 I VrApi   : FPS=88/90,Prd=45ms,Tear=0,Early=0," +
        "Stale=2,Stale2/5/10/max=0/0/0/0,VSnc=1,Lat=-1,Fov=0,CPU4/GPU=4/5,1958/599MHz,OC=FF," +
        "TA=0/0/0,SP=N/N/N,Mem=2133MHz,Free=3402MB,PLS=0,Temp=36.5C/0.0C,TW=1.63ms,App=9.10ms," +
        "GD=0.00ms,CPU&GPU=10.16ms,LCnt=1(DR90,LM2),GPU%=0.87,CPU%=0.42(W0.71),DSF=1.00,CFL=14.08/14.21"

    @Test
    fun parsesFullLine() {
        val s = VrApiParser.parse(line)!!
        assertEquals(88, s.fps)
        assertEquals(90, s.targetFps)
        assertEquals(2, s.stale)
        assertEquals(45, s.predictionMs)
        assertEquals(4, s.cpuLevel)
        assertEquals(5, s.gpuLevel)
        assertEquals(1958, s.cpuMHz)
        assertEquals(599, s.gpuMHz)
        assertEquals(2133, s.memMHz)
        assertEquals(3402, s.freeMemMb)
        assertEquals(36.5f, s.batteryTempC!!, 0.001f)
        assertNull(s.sensorTempC)
        assertEquals(9.10f, s.appGpuMs!!, 0.001f)
        assertEquals(0.87f, s.gpuUtil!!, 0.001f)
        assertEquals(0.71f, s.cpuUtil!!, 0.001f)
        assertEquals(1728918000.512, s.timestampSec!!, 0.0001)
    }

    @Test
    fun percentUtilizationIsNormalized() {
        val s = VrApiParser.parse("FPS=72/72,GPU%=64,CPU%=33")!!
        assertEquals(0.64f, s.gpuUtil!!, 0.001f)
        assertEquals(0.33f, s.cpuUtil!!, 0.001f)
        assertNull(s.timestampSec)
    }

    @Test
    fun ignoresUnrelatedLines() {
        assertNull(VrApiParser.parse("VrApi: vrapi_EnterVrMode()"))
    }
}
