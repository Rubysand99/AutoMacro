package com.starclan.starhub.data

/**
 * Tom tat de nhin la hieu: moi o/the co 1 gia tri to + 1 mau trang thai.
 * Ham thuan (khong dung Android) nen kiem tra duoc rieng.
 *
 *  OK   xanh la  = xong / san sang / on
 *  INFO xanh duong = dang dem nguoc, binh thuong
 *  WARN cam      = nen de y (vd HuntBot dang nghi, gem thap)
 *  BAD  do       = can lam ngay (vd gem sap het) hoac loi
 *  IDLE xam      = chua co du lieu
 */
enum class Tone { OK, INFO, WARN, BAD, IDLE }

data class TileState(val value: String, val sub: String, val tone: Tone,
                     val icon: IconSpec = NO_ICON, val ringPercent: Int? = null)

data class LineState(val text: String, val tone: Tone)

object HubSummary {

    private const val GEM_BAD_PERCENT = 15
    private const val GEM_WARN_PERCENT = 40
    // OwO co dinh 24h/lan — dung de uoc luong % da troi qua cho vong tron o Daily (chi de ve, khong phai so chinh xac).
    private const val DAILY_COOLDOWN_SECONDS = 24 * 3600L

    fun gemTone(percent: Int?): Tone = when {
        percent == null -> Tone.IDLE
        percent <= GEM_BAD_PERCENT -> Tone.BAD
        percent <= GEM_WARN_PERCENT -> Tone.WARN
        else -> Tone.OK
    }

    // ---------- 6 o tom tat o dau man hinh ----------

    fun dailyTile(d: HubDaily?, elapsedSec: Long): TileState {
        if (d == null) return TileState("—", Texts.get("hs_daily_type"), Tone.IDLE)
        val left = d.secondsLeft ?: return TileState("?", Texts.get("hs_daily_unknown"), Tone.IDLE)
        val remain = left - elapsedSec
        val extras = mutableListOf<String>()
        if (d.streak != null) extras.add(Texts.get("hs_streak", d.streak))
        if (d.lastReward != null) extras.add("+${HubFormat.num(d.lastReward)}")
        val icon = dailyTileIcon(d)
        return if (remain > 0) {
            val ring = (100 - (remain * 100 / DAILY_COOLDOWN_SECONDS).toInt()).coerceIn(0, 99)
            TileState(HubFormat.shortDuration(remain), extras.joinToString(" · ").ifEmpty { Texts.get("hs_daily_wait") }, Tone.INFO, icon, ring)
        } else {
            TileState(Texts.get("hs_ready"), Texts.get("hs_daily_ready_hint"), Tone.OK, icon, 100)
        }
    }

    fun huntbotTile(h: HubHuntbot?, elapsedSec: Long): TileState {
        if (h == null) return TileState("—", Texts.get("hs_hb_type"), Tone.IDLE)
        val extras = mutableListOf<String>()
        if (h.progressPct != null) extras.add("${HubFormat.trimNum(h.progressPct)}%")
        if (h.animalsCaptured != null) extras.add(Texts.get("hs_pet_count", HubFormat.num(h.animalsCaptured)))
        if (h.essence != null) extras.add(Texts.get("hs_essence", HubFormat.num(h.essence)))
        val info = extras.joinToString(" · ")
        val icon = huntbotTileIcon()
        val ring = h.progressPct?.toInt()?.coerceIn(0, 100)
        if (h.hunting != true) {
            return TileState(Texts.get("hs_resting"), info.ifEmpty { Texts.get("hs_not_sent") }, Tone.WARN, icon, ring)
        }
        val left = h.secondsLeft
        return when {
            left == null -> TileState(Texts.get("hs_hunting"), h.timeRemainingText ?: Texts.get("hs_hb_unknown_time"), Tone.INFO, icon, ring)
            left - elapsedSec > 0 -> TileState(HubFormat.shortDuration(left - elapsedSec), info.ifEmpty { Texts.get("hs_hunting") }, Tone.INFO, icon, ring)
            else -> TileState(Texts.get("hs_done"), Texts.get("hs_collect"), Tone.OK, icon, 100)
        }
    }

    fun cowoncyTile(c: HubCowoncy?, ageSec: Long?): TileState {
        if (c == null || c.amount == null) return TileState("—", Texts.get("hs_cash_type"), Tone.IDLE)
        val age = HubFormat.age(ageSec)
        return TileState(HubFormat.num(c.amount), if (age.isEmpty()) Texts.get("hs_cowoncy") else Texts.get("hs_updated", age), Tone.INFO, cowoncyTileIcon(c))
    }

    fun questTile(q: HubQuest?, elapsedSec: Long): TileState {
        if (q == null) return TileState("—", Texts.get("hs_quest_type"), Tone.IDLE)
        val items = q.quests.orEmpty()
        val nextSec = q.nextQuestSeconds
        val next = when {
            nextSec == null -> null
            nextSec - elapsedSec > 0 -> Texts.get("hs_quest_new_in", HubFormat.shortDuration(nextSec - elapsedSec))
            else -> Texts.get("hs_quest_new")
        }
        val seals = if (q.seals != null) Texts.get("hs_seals", HubFormat.num(q.seals)) else null
        val sub = listOfNotNull(next, seals).joinToString(" · ").ifEmpty { Texts.get("hs_quest") }
        val icon = questTileIcon(q)
        return when {
            q.allDone == true -> TileState(Texts.get("hs_all_done"), sub, Tone.OK, icon, 100)
            items.isNotEmpty() -> {
                val done = items.count { it.done == true }
                val ring = (done * 100 / items.size)
                TileState(Texts.get("hs_n_done", done, items.size), sub, if (done == items.size) Tone.OK else Tone.INFO, icon, ring)
            }
            else -> TileState(Texts.get("hs_quest_left"), sub, Tone.INFO, icon)
        }
    }

    fun gemsTile(g: HubGems?): TileState {
        if (g == null) return TileState("—", Texts.get("hs_gem_type"), Tone.IDLE)
        val equipped = g.equipped.orEmpty()
        val lowest = equipped.minByOrNull { it.percent ?: 100 }
            ?: return TileState("—", Texts.get("hs_no_gem"), Tone.IDLE)
        val more = if (equipped.size > 1) " · " + Texts.get("hs_gems_more", equipped.size) else ""
        val icon = iconOf(lowest.emojiId, lowest.emojiAnimated, lowest.emoji, lowest.tier)
        return TileState("${lowest.percent ?: 0}%", "${HubFormat.gemType(lowest.slot)} ${lowest.tier ?: ""}$more".trim(),
                         gemTone(lowest.percent), icon, lowest.percent)
    }

    fun zooTile(z: HubZoo?): TileState {
        if (z == null) return TileState("—", Texts.get("hs_zoo_type"), Tone.IDLE)
        return TileState(HubFormat.num(z.totalPets), Texts.get("hs_zoo_tile_sub", HubFormat.num(z.zooPoints)), Tone.INFO, zooTileIcon(z))
    }

    // ---------- Dong tom tat cua tung the chi tiet ----------

    private fun noData() = LineState(Texts.get("hs_nodata"), Tone.IDLE)

    fun gemsLine(g: HubGems?): LineState {
        if (g == null) return noData()
        val equipped = g.equipped.orEmpty()
        val spareTotal = g.spare.orEmpty().sumOf { it.count ?: 0L }
        val lowest = equipped.minByOrNull { it.percent ?: 100 }
        val text = if (lowest == null) Texts.get("hs_gems_none", HubFormat.num(spareTotal))
        else Texts.get("hs_gems_line", equipped.size, lowest.percent ?: 0, HubFormat.num(spareTotal))
        return LineState(text, if (lowest == null) Tone.IDLE else gemTone(lowest.percent))
    }

    fun questLine(q: HubQuest?): LineState {
        if (q == null) return noData()
        val items = q.quests.orEmpty()
        val seals = if (q.seals != null) " · " + Texts.get("hs_seals", HubFormat.num(q.seals)) else ""
        return when {
            q.allDone == true -> LineState(Texts.get("hs_quest_all_done") + seals, Tone.OK)
            items.isNotEmpty() -> {
                val left = items.count { it.done != true }
                LineState(Texts.get("hs_quest_left_count", left, items.size) + seals, if (left == 0) Tone.OK else Tone.INFO)
            }
            else -> LineState(Texts.get("hs_quest_left_some") + seals, Tone.INFO)
        }
    }

    fun teamLine(t: HubTeam?): LineState {
        val members = t?.members.orEmpty()
        if (members.isEmpty()) return noData()
        val names = members.mapNotNull { it.name }
        val shown = names.take(3).joinToString(", ") + if (names.size > 3) "…" else ""
        return LineState(Texts.get("hs_team", members.size, shown), Tone.INFO)
    }

    fun zooLine(z: HubZoo?): LineState {
        if (z == null) return noData()
        return LineState(Texts.get("hs_zoo_line", HubFormat.num(z.totalPets), HubFormat.num(z.zooPoints)), Tone.INFO)
    }

    fun weaponsLine(w: HubWeapons?): LineState {
        if (w == null || w.weapons.orEmpty().isEmpty()) return noData()
        return LineState(Texts.get("hs_weapons", HubFormat.num(w.count ?: w.weapons?.size)), Tone.INFO)
    }

    fun petDexLine(d: HubPetDex?): LineState {
        if (d == null || d.species.orEmpty().isEmpty()) return noData()
        return LineState(Texts.get("hs_dex", HubFormat.num(d.totalOwned), HubFormat.num(d.totalKnown)), Tone.INFO)
    }

    fun inventoryLine(inv: HubInventory?): LineState {
        if (inv == null) return noData()
        return LineState(Texts.get("hs_inv", HubFormat.num(inv.kinds), HubFormat.num(inv.totalItems)), Tone.INFO)
    }
}
