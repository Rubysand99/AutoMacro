package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailStreakTest {
    @Test
    fun cycleAllOkResetsToZero() {
        val s = FailStreak(6)
        s.beginCycle(); assertFalse(s.fail()); assertEquals(1, s.count)
        s.beginCycle(); s.ok(); s.ok(); s.endCycle()
        assertEquals(0, s.count)
    }

    @Test
    fun battleFailsWhileHuntOkStillAccumulatesInEitherOrder() {
        val s = FailStreak(6)
        repeat(5) { i ->
            s.beginCycle()
            if (i % 2 == 0) { s.ok(); s.fail() } else { s.fail(); s.ok() }   // thu tu ngau nhien: hunt OK / battle loi
            s.endCycle()
            assertEquals(i + 1, s.count)
        }
        s.beginCycle(); s.ok()
        assertTrue("lan thu 6 phai bao dung han", s.fail())
        assertEquals(6, s.count)
    }

    @Test
    fun idleCycleKeepsCount() {
        val s = FailStreak(6)
        s.beginCycle(); s.fail()
        s.beginCycle(); s.endCycle()   // hunt tam dung, battle tat: khong go gi
        assertEquals(1, s.count)
    }

    @Test
    fun recoversAfterFailuresOnceAFullCycleIsClean() {
        val s = FailStreak(6)
        s.beginCycle(); s.fail(); s.fail()
        s.beginCycle(); s.ok(); s.endCycle()
        assertEquals(0, s.count)
    }

    @Test
    fun reopenFailureCountsTowardStop() {
        val s = FailStreak(2)
        s.beginCycle(); assertFalse(s.fail())
        s.beginCycle(); assertTrue(s.fail())
    }
}
