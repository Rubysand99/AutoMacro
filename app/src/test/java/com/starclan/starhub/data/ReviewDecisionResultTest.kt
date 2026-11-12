package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewDecisionResultTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that
    @Test fun parses_next_id_and_remaining() {
        val r = ReviewDecisionResult.parse("""{"ok":true,"decided":"ab12","decision":"drop","next_id":"cd34","remaining":7}""")!!
        assertEquals("ab12", r.decided)
        assertEquals("cd34", r.nextId)
        assertEquals(7, r.remaining)
    }

    @Test fun null_next_id_means_finished() {
        val r = ReviewDecisionResult.parse("""{"ok":true,"decided":"ab12","next_id":null,"remaining":0}""")!!
        assertNull(r.nextId)
        assertEquals(0, r.remaining)
    }

    @Test fun garbage_returns_null() {
        assertNull(ReviewDecisionResult.parse("khong phai json"))
        assertNull(ReviewDecisionResult.parse("[]"))
    }
}
