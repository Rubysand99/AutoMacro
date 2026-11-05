package com.starclan.starhub.data

/** Nhom lenh hien tren Hub noi (tab Setting, khung "Lenh trong farm"). */
enum class FarmCmdGroup(val label: String) {
    MAIN("Lệnh chính mỗi vòng"),
    CHORE("Việc vặt chen giữa vòng"),
    CHECK("Lệnh kiểm tra (cập nhật dữ liệu cho bot)")
}

/**
 * Moi lenh farm co the bat / tat rieng. `id` duoc LUU xuong may nen KHONG doi ten id da dung
 * (them lenh moi thi them id moi; lenh moi mac dinh BAT).
 * `shown` la chu hien duoi ten lenh tren Hub noi.
 */
enum class FarmCmd(val id: String, val label: String, val shown: String, val group: FarmCmdGroup) {
    HUNT("hunt", "Hunt", "owo hunt", FarmCmdGroup.MAIN),
    BATTLE("battle", "Battle", "owo battle", FarmCmdGroup.MAIN),
    OWO("owo", "Owo", "owo (gõ riêng sau battle)", FarmCmdGroup.MAIN),

    DAILY("daily", "Daily", "owo daily (khi đến giờ nhận)", FarmCmdGroup.CHORE),
    QUEST("quest", "Quest", "owo quest + bấm nhận thưởng", FarmCmdGroup.CHORE),
    HUNTBOT("hb", "Huntbot", "owo hb (nhận thành quả)", FarmCmdGroup.CHORE),
    UPGRADE("upgrade", "Nâng cấp huntbot", "owo upg … (kèm owo hb)", FarmCmdGroup.CHORE),
    OPEN_BOXES("boxes", "Mở hộp", "owo lb / wc / bwc all", FarmCmdGroup.CHORE),
    SACRIFICE("sacrifice", "Hiến tế", "owo sc all", FarmCmdGroup.CHORE),
    TEAM("team", "Đội hình", "owo team", FarmCmdGroup.CHORE),
    WEAPON("weapon", "Chi tiết vũ khí", "owo weapon <ID>", FarmCmdGroup.CHORE),

    ZOO("zoo", "Zoo", "owo zoo (lúc bắt đầu + định kỳ)", FarmCmdGroup.CHECK),
    INV("inv", "Kho đồ", "owo inv", FarmCmdGroup.CHECK),
    CASH("cash", "Cowoncy", "owo cash", FarmCmdGroup.CHECK),
    WS("ws", "Weapon shards", "owo ws", FarmCmdGroup.CHECK);

    companion object {
        fun fromId(id: String): FarmCmd? = values().firstOrNull { it.id == id }

        /**
         * Lenh "check" ma BOT nho app go xen (action auto_check: owo cash / inv / quest / ws / zoo / hb) thuoc cong tac nao.
         * Tra ve null neu khong phai 1 trong cac lenh do -> khong bi cong tac chan.
         */
        fun forCheckCommand(commandText: String?): FarmCmd? =
            when (commandText?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")) {
                "owo cash" -> CASH
                "owo inv" -> INV
                "owo quest" -> QUEST
                "owo ws" -> WS
                "owo zoo" -> ZOO
                "owo hb" -> HUNTBOT
                else -> null
            }
    }
}

/**
 * Cau hinh bat / tat tung lenh farm. Thuan Kotlin (khong dinh Android) de kiem tra de dang.
 * Chi luu tap lenh DANG TAT: lenh moi them o ban sau tu nhien mac dinh BAT, nguoi dung khong phai lam gi.
 */
data class FarmCommandConfig(val disabled: Set<FarmCmd> = emptySet()) {

    fun isEnabled(cmd: FarmCmd): Boolean = cmd !in disabled

    fun with(cmd: FarmCmd, enabled: Boolean): FarmCommandConfig =
        copy(disabled = if (enabled) disabled - cmd else disabled + cmd)

    fun enabledCount(): Int = FarmCmd.values().size - disabled.size

    fun total(): Int = FarmCmd.values().size

    /** Con it nhat 1 lenh bat thi farm moi co viec de lam */
    fun anyEnabled(): Boolean = enabledCount() > 0

    /** Dang luu: id cac lenh DANG TAT, cach nhau dau phay, da sap xep (on dinh de so sanh). Rong = bat het. */
    fun encode(): String = disabled.map { it.id }.sorted().joinToString(",")

    companion object {
        /** Mac dinh = hanh vi cu cua app: bat het */
        val ALL_ON = FarmCommandConfig()

        /** Che do toi gian: chi hunt + battle, tat moi viec vat / lenh kiem tra / owo */
        val HUNT_BATTLE_ONLY = only(FarmCmd.HUNT, FarmCmd.BATTLE)

        /** Chi bat cac lenh duoc liet ke, tat con lai */
        fun only(vararg enabled: FarmCmd): FarmCommandConfig =
            FarmCommandConfig(FarmCmd.values().filter { it !in enabled }.toSet())

        /** Doc lai tu chuoi da luu. Chuoi rong / hong / id la (ban cu hon hoac moi hon) thi bo qua id do, khong loi. */
        fun decode(text: String?): FarmCommandConfig {
            if (text.isNullOrBlank()) return ALL_ON
            val off = text.split(',')
                .mapNotNull { FarmCmd.fromId(it.trim()) }
                .toSet()
            return FarmCommandConfig(off)
        }
    }
}
