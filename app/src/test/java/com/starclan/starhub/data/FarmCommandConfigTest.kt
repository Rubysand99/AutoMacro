package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmCommandConfigTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that

    @Test fun default_is_everything_on() {
        val c = FarmCommandConfig.ALL_ON
        for (cmd in FarmCmd.values()) assertTrue(cmd.label, c.isEnabled(cmd))
        assertEquals(FarmCmd.values().size, c.enabledCount())
        assertTrue(c.anyEnabled())
        assertEquals("", c.encode())
    }

    @Test fun ids_are_unique_and_not_blank() {
        val ids = FarmCmd.values().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in ids) {
            assertTrue(id.isNotBlank())
            // id duoc luu cach nhau dau phay -> khong duoc chua dau phay / khoang trang
            assertFalse(id.contains(',') || id.contains(' '))
        }
    }

    @Test fun every_command_belongs_to_a_group_and_has_text() {
        for (cmd in FarmCmd.values()) {
            assertTrue(cmd.label.isNotBlank())
            assertTrue(cmd.shown.isNotBlank())
        }
        for (g in FarmCmdGroup.values()) assertTrue("nhom ${g.name} trong", FarmCmd.values().any { it.group == g })
    }

    @Test fun with_turns_a_single_command_off_and_on() {
        val off = FarmCommandConfig.ALL_ON.with(FarmCmd.HUNT, false)
        assertFalse(off.isEnabled(FarmCmd.HUNT))
        assertTrue(off.isEnabled(FarmCmd.BATTLE))
        assertEquals(FarmCmd.values().size - 1, off.enabledCount())
        val on = off.with(FarmCmd.HUNT, true)
        assertEquals(FarmCommandConfig.ALL_ON, on)
        // tat 2 lan van nhu tat 1 lan
        assertEquals(off, off.with(FarmCmd.HUNT, false))
    }

    @Test fun any_enabled_is_false_only_when_everything_is_off() {
        var c = FarmCommandConfig.ALL_ON
        for (cmd in FarmCmd.values()) c = c.with(cmd, false)
        assertFalse(c.anyEnabled())
        assertEquals(0, c.enabledCount())
        assertTrue(c.with(FarmCmd.ZOO, true).anyEnabled())
    }

    @Test fun encode_is_sorted_and_roundtrips() {
        val c = FarmCommandConfig.ALL_ON.with(FarmCmd.ZOO, false).with(FarmCmd.DAILY, false).with(FarmCmd.HUNT, false)
        assertEquals("daily,hunt,zoo", c.encode())
        assertEquals(c, FarmCommandConfig.decode(c.encode()))
        // thu tu tat khac nhau van ra cung 1 chuoi
        val c2 = FarmCommandConfig.ALL_ON.with(FarmCmd.HUNT, false).with(FarmCmd.ZOO, false).with(FarmCmd.DAILY, false)
        assertEquals(c.encode(), c2.encode())
    }

    @Test fun every_possible_single_off_roundtrips() {
        for (cmd in FarmCmd.values()) {
            val c = FarmCommandConfig.ALL_ON.with(cmd, false)
            assertEquals(cmd.name, c, FarmCommandConfig.decode(c.encode()))
        }
        val allOff = FarmCmd.values().fold(FarmCommandConfig.ALL_ON) { acc, cmd -> acc.with(cmd, false) }
        assertEquals(allOff, FarmCommandConfig.decode(allOff.encode()))
    }

    @Test fun decode_is_forgiving() {
        assertEquals(FarmCommandConfig.ALL_ON, FarmCommandConfig.decode(null))
        assertEquals(FarmCommandConfig.ALL_ON, FarmCommandConfig.decode(""))
        assertEquals(FarmCommandConfig.ALL_ON, FarmCommandConfig.decode("   "))
        // id la (vd lenh da bi go o ban sau) bi bo qua, id hop le van duoc giu; khoang trang thua khong sao
        val c = FarmCommandConfig.decode(" hunt , khong_co_lenh_nay ,, battle ")
        assertFalse(c.isEnabled(FarmCmd.HUNT))
        assertFalse(c.isEnabled(FarmCmd.BATTLE))
        assertEquals(FarmCmd.values().size - 2, c.enabledCount())
    }

    @Test fun new_commands_default_to_on_for_old_saved_data() {
        // du lieu cu chi nho "hunt" tat -> moi lenh khac (ke ca lenh them sau nay) deu bat
        val c = FarmCommandConfig.decode("hunt")
        for (cmd in FarmCmd.values()) assertEquals(cmd != FarmCmd.HUNT, c.isEnabled(cmd))
    }

    @Test fun only_keeps_exactly_the_listed_commands() {
        val c = FarmCommandConfig.only(FarmCmd.DAILY, FarmCmd.ZOO)
        assertEquals(2, c.enabledCount())
        assertTrue(c.isEnabled(FarmCmd.DAILY))
        assertTrue(c.isEnabled(FarmCmd.ZOO))
        assertFalse(c.isEnabled(FarmCmd.HUNT))
    }

    @Test fun hunt_battle_preset() {
        val c = FarmCommandConfig.HUNT_BATTLE_ONLY
        assertEquals(2, c.enabledCount())
        assertTrue(c.isEnabled(FarmCmd.HUNT))
        assertTrue(c.isEnabled(FarmCmd.BATTLE))
        for (cmd in FarmCmd.values()) if (cmd != FarmCmd.HUNT && cmd != FarmCmd.BATTLE) assertFalse(cmd.label, c.isEnabled(cmd))
    }

    @Test fun bot_check_commands_map_to_their_toggle() {
        assertEquals(FarmCmd.CASH, FarmCmd.forCheckCommand("owo cash"))
        assertEquals(FarmCmd.INV, FarmCmd.forCheckCommand(" OWO  inv "))
        assertEquals(FarmCmd.QUEST, FarmCmd.forCheckCommand("owo quest"))
        assertEquals(FarmCmd.WS, FarmCmd.forCheckCommand("owo ws"))
        assertEquals(FarmCmd.ZOO, FarmCmd.forCheckCommand("owo zoo"))
        assertEquals(FarmCmd.HUNTBOT, FarmCmd.forCheckCommand("owo hb"))
    }

    @Test fun other_commands_are_never_gated() {
        // hbstart nho app go "owo hb 24h", thay gem go "owo use ..." : khong bi cong tac chan
        assertEquals(null, FarmCmd.forCheckCommand("owo hb 24h"))
        assertEquals(null, FarmCmd.forCheckCommand("owo use 051"))
        assertEquals(null, FarmCmd.forCheckCommand(null))
        assertEquals(null, FarmCmd.forCheckCommand(""))
    }
}
