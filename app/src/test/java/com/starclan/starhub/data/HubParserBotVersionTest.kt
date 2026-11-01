package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubParserBotVersionTest {
    @Test fun doc_phien_ban_bot() =
        assertEquals("v1.12 (a1b2c3d)", HubParser.parse("""{"bot_version":"v1.12 (a1b2c3d)"}""").botVersion)

    @Test fun bot_cu_khong_gui_truong_nay_thi_null() =
        assertNull(HubParser.parse("""{"hub_version":1}""").botVersion)

    @Test fun chuoi_rong_thi_null() =
        assertNull(HubParser.parse("""{"bot_version":"  "}""").botVersion)

    @Test fun kieu_sai_khong_lam_hong_ca_man_hinh() =
        assertNull(HubParser.parse("""{"bot_version":{"a":1}}""").botVersion)
}
