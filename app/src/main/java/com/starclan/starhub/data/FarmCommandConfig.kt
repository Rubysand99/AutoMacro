package com.starclan.starhub.data

/** Nhom lenh hien tren Hub noi (tab Setting, khung "Lenh trong farm"). */
enum class FarmCmdGroup(private val labelKey: String) {
    MAIN("farmgroup_main"),
    CHORE("farmgroup_chore"),
    CHECK("farmgroup_check");

    /** Ten nhom hien tren Hub noi (doc tu strings.xml qua [Texts]). */
    val label: String get() = Texts.get(labelKey)
}

/**
 * Moi lenh farm co the bat / tat rieng. `id` duoc LUU xuong may nen KHONG doi ten id da dung
 * (them lenh moi thi them id moi; lenh moi mac dinh BAT).
 * `shown` la chu hien duoi ten lenh tren Hub noi.
 */
enum class FarmCmd(val id: String, val group: FarmCmdGroup) {
    HUNT("hunt", FarmCmdGroup.MAIN),
    BATTLE("battle", FarmCmdGroup.MAIN),
    OWO("owo", FarmCmdGroup.MAIN),

    DAILY("daily", FarmCmdGroup.CHORE),
    QUEST("quest", FarmCmdGroup.CHORE),
    HUNTBOT("hb", FarmCmdGroup.CHORE),
    UPGRADE("upgrade", FarmCmdGroup.CHORE),
    OPEN_BOXES("boxes", FarmCmdGroup.CHORE),
    SACRIFICE("sacrifice", FarmCmdGroup.CHORE),
    TEAM("team", FarmCmdGroup.CHORE),
    WEAPON("weapon", FarmCmdGroup.CHORE),

    ZOO("zoo", FarmCmdGroup.CHECK),
    INV("inv", FarmCmdGroup.CHECK),
    CASH("cash", FarmCmdGroup.CHECK),
    WS("ws", FarmCmdGroup.CHECK);

    /** Ten lenh hien tren Hub noi (strings.xml: farmcmd_<id>_label). */
    val label: String get() = Texts.get("farmcmd_${id}_label")

    /** Chu hien duoi ten lenh (strings.xml: farmcmd_<id>_shown). */
    val shown: String get() = Texts.get("farmcmd_${id}_shown")

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
