package com.starclan.starhub.data

import java.util.Locale

/**
 * Bien du lieu /hub thanh chu de hien tren man hinh. Toan bo la ham thuan
 * (khong dung gi cua Android) nen de kiem tra rieng va khong bi loi giao dien.
 */
object HubFormat {
    /** Ten 4 loai gem cua OwO theo slot. Lenh dung gem van la ID so (owo use 51 65 72 79). */
    fun gemType(slot: String?): String = when (slot) {
        "1" -> "Hunting"
        "3" -> "Empowering"
        "4" -> "Lucky"
        "star" -> "Special"
        null -> "?"
        else -> "Slot $slot"
    }


    private const val LOW_GEM_PERCENT = 15

    // ---------- Ham nho dung chung ----------

    /** 11272674 -> "11,272,674"; null -> "?" */
    fun num(v: Number?): String =
        if (v == null || v.toLong() < 0) "?" else String.format(Locale.US, "%,d", v.toLong())

    /** Phan tram luon nam trong 0..100 (bot gui sai thi van hien hop ly) */
    fun pct(p: Int?): Int = (p ?: 0).coerceIn(0, 100)

    /** 88.50 -> "88.5", 90.0 -> "90" */
    fun trimNum(v: Double?): String {
        if (v == null) return "?"
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    }

    /** "cap nhat X truoc" — null thi tra chuoi rong */
    fun age(seconds: Long?): String {
        if (seconds == null) return ""
        val s = maxOf(seconds, 0L)
        return when {
            s < 60 -> Texts.get("hf_age_now")
            s < 3600 -> Texts.get("hf_age_min", s / 60)
            s < 86400 -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0L) Texts.get("hf_age_h", h) else Texts.get("hf_age_hm", h, m)
            }
            else -> Texts.get("hf_age_day", s / 86400)
        }
    }

    /** 3725 -> "1 giờ 02 phút 05 giây" */
    fun duration(seconds: Long): String {
        val s = maxOf(seconds, 0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> Texts.get("hf_dur_hms", h, m, sec)
            m > 0 -> Texts.get("hf_dur_ms", m, sec)
            else -> Texts.get("hf_dur_s", sec)
        }
    }

    /** Ban ngan cho o tom tat: 3725 -> "1g 02p", 125 -> "2p 05s", 40 -> "40s" */
    fun shortDuration(seconds: Long): String {
        val s = maxOf(seconds, 0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> Texts.get("hf_short_hm", h, m)
            m > 0 -> Texts.get("hf_short_ms", m, sec)
            else -> Texts.get("hf_short_s", sec)
        }
    }

    /** 63 -> "▰▰▰▰▰▰▱▱▱▱" */
    fun bar(percent: Int?, width: Int = 10): String {
        val p = (percent ?: 0).coerceIn(0, 100)
        val filled = Math.round(p / 100.0 * width).toInt().coerceIn(0, width)
        return "▰".repeat(filled) + "▱".repeat(width - filled)
    }

    /** Hien thay cho noi dung 1 muc khi muc do khong doc duoc (cac muc khac van binh thuong). */
    fun sectionError(reason: String): String = Texts.get("hf_section_error") + "\n" + reason

    private fun noData(hint: String?): String =
        Texts.get("hf_nodata") +
            (if (hint.isNullOrBlank()) "" else "\n$hint")

    // ---------- Tuoi du lieu cua tung muc ----------

    /** Gem gom nhieu slot: lay slot moi nhat (tuoi nho nhat) lam tuoi cua ca muc. */
    fun gemsAgeSeconds(g: HubGems?): Long? = g?.equipped.orEmpty().mapNotNull { it.ageSeconds }.minOrNull()

    // ---------- Tung muc ----------

    /** elapsedSec = so giay tu luc app lay du lieu (de dem nguoc chay muot giua 2 lan lam moi). */
    fun daily(d: HubDaily?, elapsedSec: Long): String {
        if (d == null) return noData(Texts.get("hf_hint_daily"))
        val sb = StringBuilder()
        val left = d.secondsLeft
        if (left == null) {
            sb.append(Texts.get("hf_daily_unknown"))
        } else {
            val remain = left - elapsedSec
            if (remain > 0) {
                sb.append(Texts.get("hf_daily_remain", duration(remain)))
            } else {
                sb.append(Texts.get("hf_daily_ready"))
            }
        }
        val extras = mutableListOf<String>()
        if (d.streak != null) extras.add(Texts.get("hf_streak", num(d.streak)))
        if (d.lastReward != null) extras.add(Texts.get("hf_last_reward", num(d.lastReward)))
        if (extras.isNotEmpty()) sb.append("\n").append(extras.joinToString(" · "))
        return sb.toString()
    }

    fun cowoncy(c: HubCowoncy?): String {
        if (c == null || c.amount == null) return noData(Texts.get("hf_hint_money"))
        return Texts.get("hf_cowoncy", num(c.amount))
    }

    fun gems(g: HubGems?): String {
        if (g == null) return noData(Texts.get("hf_hint_gems"))
        val sb = StringBuilder()
        val equipped = g.equipped.orEmpty()
        if (equipped.isEmpty()) {
            sb.append(Texts.get("hf_gems_none"))
        } else {
            sb.append(Texts.get("hf_gems_using"))
            for (e in equipped) {
                val warn = if ((e.percent ?: 100) <= LOW_GEM_PERCENT) " " + Texts.get("hf_gem_low") else ""
                sb.append("\n• ${gemType(e.slot)} — ${e.tier ?: "?"}$warn")
                sb.append("\n   ${bar(e.percent)} ${e.current ?: "?"}/${e.max ?: "?"} (${pct(e.percent)}%)")
            }
        }
        sb.append("\n\n" + Texts.get("hf_gems_spare"))
        val spare = g.spare.orEmpty()
        if (spare.isEmpty()) {
            sb.append("\n" + Texts.get("hf_gems_spare_none"))
        } else {
            for ((slot, list) in spare.groupBy { it.slot ?: "?" }) {
                sb.append("\n• ${gemType(slot)}: ")
                sb.append(list.joinToString(" · ") { "${it.tier ?: "?"} ×${num(it.count)} (${it.code ?: "?"})" })
            }
        }
        return sb.toString()
    }

    /** elapsedSec = so giay tu luc app lay du lieu, de dem nguoc chay muot giua 2 lan lam moi. */
    fun huntbot(h: HubHuntbot?, elapsedSec: Long): String {
        if (h == null) return noData(Texts.get("hf_hint_hb"))
        val sb = StringBuilder()
        if (h.hunting == true) {
            sb.append(Texts.get("hf_hb_hunting"))
            if (h.progressPct != null) sb.append(" · ${trimNum(h.progressPct)}%")
            val left = h.secondsLeft
            if (left != null) {
                val remain = left - elapsedSec
                if (remain > 0) {
                    sb.append("\n" + Texts.get("hf_hb_remain", duration(remain)))
                } else {
                    sb.append("\n" + Texts.get("hf_hb_due"))
                }
            } else if (!h.timeRemainingText.isNullOrBlank()) {
                sb.append("\n" + Texts.get("hf_hb_about", h.timeRemainingText))
            } else {
                sb.append("\n" + Texts.get("hf_hb_unknown"))
            }
        } else {
            sb.append(Texts.get("hf_hb_idle"))
        }
        val extras = mutableListOf<String>()
        if (h.animalsCaptured != null) extras.add(Texts.get("hf_hb_captured", num(h.animalsCaptured)))
        if (h.essence != null) extras.add("Essence: ${num(h.essence)}")
        if (extras.isNotEmpty()) sb.append("\n").append(extras.joinToString(" · "))
        return sb.toString()
    }

    fun quest(q: HubQuest?, elapsedSec: Long): String {
        if (q == null) return noData(null)
        val items = q.quests.orEmpty()
        val header = when {
            q.allDone == true -> Texts.get("hf_q_all_done")
            items.isNotEmpty() -> Texts.get("hf_q_left", items.count { it.done != true }, items.size)
            else -> Texts.get("hf_q_left_some")
        }
        val sb = StringBuilder(header)
        if (q.seals != null) sb.append(" · Seals: ${num(q.seals)}")
        val nextSec = q.nextQuestSeconds
        if (nextSec != null) {
            val remain = nextSec - elapsedSec
            if (remain > 0) {
                sb.append("\n⏰ " + Texts.get("hf_q_new_in", duration(remain)))
            } else {
                sb.append("\n⏰ " + Texts.get("hf_q_new"))
            }
        } else if (!q.nextQuest.isNullOrBlank()) {
            sb.append("\n" + Texts.get("hf_q_next", q.nextQuest))
        }
        for (qi in items) {
            val mark = if (qi.done == true) Texts.get("hf_q_done_mark") else "•"
            val rarity = if (qi.rarity.isNullOrBlank()) "" else " (${qi.rarity})"
            sb.append("\n\n$mark ${qi.index ?: "?"}. ${qi.title ?: "?"}$rarity")
            if (qi.current != null && qi.max != null && qi.max > 0) {
                val pct = (qi.current * 100 / qi.max).toInt().coerceIn(0, 100)
                sb.append("\n   ${bar(pct)} ${num(qi.current)}/${num(qi.max)} ($pct%)")
            }
            if (!qi.description.isNullOrBlank()) sb.append("\n   ${qi.description}")
            val rewards = qi.rewards.orEmpty()
            if (rewards.isNotEmpty()) {
                sb.append("\n   " + Texts.get("hf_q_reward") + " ").append(rewards.joinToString(" · ") { r -> "${r.item ?: "?"} +${num(r.amount)}" })
            }
        }
        return sb.toString()
    }

    fun team(t: HubTeam?): String {
        val members = t?.members.orEmpty()
        if (members.isEmpty()) return noData(null)
        return members.joinToString("\n") { m ->
            val stats = "HP${m.hp ?: "?"} WP${m.wp ?: "?"} ATT${m.att ?: "?"} MAG${m.mag ?: "?"}"
            val weapon = if (m.weaponId.isNullOrBlank()) "" else "\n   ${m.weaponId} · ${trimNum(m.quality)}%"
            "[${m.pos ?: "?"}] ${m.name ?: "?"} Lv${m.level ?: "?"} — $stats$weapon"
        }
    }

    fun zoo(z: HubZoo?, expanded: Boolean): String {
        if (z == null) return noData(Texts.get("hf_hint_zoo"))
        val sb = StringBuilder()
        sb.append(Texts.get("hf_zoo_head", num(z.zooPoints), num(z.totalPets)))
        // Dong cuoi bang zoo cua OwO = so pet DA BAT tu truoc toi nay (ban di khong giam), khac voi so DANG CO
        if (z.caughtTotal != null) sb.append("\n" + Texts.get("hf_zoo_caught", num(z.caughtTotal)))
        for ((tier, t) in z.byTier.orEmpty()) {
            if ((t.total ?: 0L) > 0L) sb.append("\n• " + Texts.get("hf_zoo_tier", tier, num(t.total), num(t.species)))
        }
        if (expanded) {
            if (!z.breakdownRaw.isNullOrBlank()) sb.append("\n\n" + Texts.get("hf_zoo_breakdown", z.breakdownRaw))
            sb.append("\n")
            for ((tier, list) in z.pets.orEmpty().groupBy { it.tier ?: "?" }) {
                sb.append("\n$tier: ")
                sb.append(list.joinToString(", ") { "${it.name ?: "?"} ×${num(it.count)}" })
            }
        } else if (z.pets.orEmpty().isNotEmpty()) {
            sb.append("\n\n" + Texts.get("hf_zoo_tap"))
        }
        return sb.toString()
    }

    fun inventory(inv: HubInventory?, expanded: Boolean): String {
        if (inv == null) return noData(Texts.get("hf_hint_inv"))
        val items = inv.items.orEmpty()
        if (expanded) {
            // Dong "N loai · M mon" da hien o dau the (HubSummary.inventoryLine) -> khong in lai o day.
            return items.sortedBy { it.code ?: "" }.joinToString("\n") { i ->
                "${i.code ?: "?"}  ${i.name ?: "?"} ×${num(i.count)}"
            }
        }
        val sb = StringBuilder(Texts.get("hf_inv_head", num(inv.kinds), num(inv.totalItems)))
        if (items.isNotEmpty()) sb.append("\n\n" + Texts.get("hf_inv_tap"))
        return sb.toString()
    }

    fun weapons(w: HubWeapons?, expanded: Boolean): String {
        val list = w?.weapons.orEmpty()
        if (w == null || list.isEmpty()) return noData(Texts.get("hf_hint_weapons"))
        val sb = StringBuilder(Texts.get("hf_w_head", num(w.count ?: list.size)))
        val shown = if (expanded) list else list.take(5)
        for (x in shown) {
            sb.append("\n• ${x.id ?: "?"} · ${x.typeName ?: "?"} ${trimNum(x.quality)}%")
            if (!x.passiveName.isNullOrBlank()) sb.append(" · ${x.passiveName}")
            if (expanded && !x.passiveDesc.isNullOrBlank()) sb.append("\n   ${x.passiveDesc}")
        }
        if (!expanded && list.size > shown.size) sb.append("\n\n" + Texts.get("hf_w_more", list.size))
        return sb.toString()
    }
}
