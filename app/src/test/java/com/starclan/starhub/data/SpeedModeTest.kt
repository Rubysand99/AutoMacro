package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedModeTest {
    @Test fun ids_are_unique_and_roundtrip() {
        assertEquals(SpeedMode.values().size, SpeedMode.values().map { it.id }.toSet().size)
        for (m in SpeedMode.values()) assertEquals(m, SpeedMode.fromId(m.id))
        assertEquals(SpeedMode.NORMAL, SpeedMode.fromId(99))
    }

    @Test fun ranges_are_valid() {
        for (m in SpeedMode.values()) {
            assertTrue(m.cycleMinMs < m.cycleMaxMs)
            assertTrue(m.restEveryMin < m.restEveryMax)
            assertTrue(m.restMinMs < m.restMaxMs)
            // khong bao gio nhanh hon cooldown that cua OwO (~15s)
            assertTrue("${m.name} vong qua ngan", m.cycleMinMs >= 15_500L)
        }
    }

    @Test fun ordered_fast_to_slow() {
        assertTrue(SpeedMode.FAST.cycleMaxMs <= SpeedMode.NORMAL.cycleMaxMs)
        assertTrue(SpeedMode.NORMAL.cycleMinMs < SpeedMode.SLOW.cycleMinMs)
    }
}
