package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownFormatTest {
    @Test fun lam_tron_len() = assertEquals("0:43", CountdownFormat.mmss(42_300))
    @Test fun dung_mot_giay() = assertEquals("0:01", CountdownFormat.mmss(1_000))
    @Test fun con_chut_xiu_van_la_1_giay() = assertEquals("0:01", CountdownFormat.mmss(1))
    @Test fun het_gio() = assertEquals("0:00", CountdownFormat.mmss(0))
    @Test fun am_thi_ve_0() = assertEquals("0:00", CountdownFormat.mmss(-5_000))
    @Test fun muoi_phut() = assertEquals("10:00", CountdownFormat.mmss(600_000))
    @Test fun chin_phut_rua_giay() = assertEquals("9:41", CountdownFormat.mmss(580_300))
    @Test fun tren_mot_gio() = assertEquals("1:02:03", CountdownFormat.mmss(3_723_000))

    @Test fun ti_le_giua() = assertEquals(0.5f, CountdownFormat.fraction(300_000, 600_000))
    @Test fun ti_le_het() = assertEquals(0f, CountdownFormat.fraction(0, 600_000))
    @Test fun ti_le_khong_vuot_1() = assertEquals(1f, CountdownFormat.fraction(700_000, 600_000))
    @Test fun ti_le_tong_0() = assertEquals(0f, CountdownFormat.fraction(5, 0))
}
