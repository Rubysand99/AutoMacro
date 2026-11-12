package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HuntbotAlertLogicTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that
    private val e = 1_800_000_000L

    @Test fun con_9_phut_thi_bao() = assertEquals(true, HuntbotAlertLogic.shouldNotify(true, 540, e, null))
    @Test fun dung_10_phut_thi_bao() = assertEquals(true, HuntbotAlertLogic.shouldNotify(true, 600, e, null))
    @Test fun con_11_phut_chua_bao() = assertEquals(false, HuntbotAlertLogic.shouldNotify(true, 660, e, null))
    @Test fun con_chua_toi_1_phut_thoi() = assertEquals(false, HuntbotAlertLogic.shouldNotify(true, 30, e, null))
    @Test fun khong_san_thi_khong_bao() = assertEquals(false, HuntbotAlertLogic.shouldNotify(false, 540, e, null))
    @Test fun chua_biet_gio_ve() = assertEquals(false, HuntbotAlertLogic.shouldNotify(true, null, null, null))
    @Test fun da_bao_chuyen_nay_thi_khong_lap() = assertEquals(false, HuntbotAlertLogic.shouldNotify(true, 500, e, e))
    @Test fun gio_ve_dao_dong_2_phut_van_la_cung_chuyen() =
        assertEquals(false, HuntbotAlertLogic.shouldNotify(true, 500, e + 120, e))
    @Test fun chuyen_moi_24h_sau_thi_bao_lai() =
        assertEquals(true, HuntbotAlertLogic.shouldNotify(true, 500, e + 86_400, e))

    @Test fun phut_lam_tron() {
        assertEquals(9L, HuntbotAlertLogic.minutesText(540))
        assertEquals(9L, HuntbotAlertLogic.minutesText(525))
        assertEquals(1L, HuntbotAlertLogic.minutesText(10))
    }

    @Test fun moc_ve_theo_dong_ho_may() {
        // 9 phut nua tinh tu 1_000_000 ms
        assertEquals(1_000_000L + 540_000L, HuntbotAlertLogic.readyAtMillis(1_000_000L, 540L))
    }

    @Test fun tu_go_sau_khi_ve_2_phut() {
        assertEquals(540_000L + 120_000L, HuntbotAlertLogic.timeoutAfterMillis(540L))
    }
}
