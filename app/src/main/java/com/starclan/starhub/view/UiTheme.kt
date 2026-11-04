package com.starclan.starhub.view

/**
 * Ban sao cua he mau trong res/values/colors.xml cho nhung noi ve bang code (Hub noi, FarmOverlay).
 * Thuan Kotlin (khong dung Android) nen test duoc: UiThemeColorsTest so tung mau voi colors.xml, lech la bao loi.
 * Doi mau thi sua CA HAI NOI.
 */
object UiTheme {
    // Nen
    const val BG = "#0B0D12"
    const val SURFACE = "#12151C"
    const val RAISED = "#1A1F2A"
    const val LINE = "#262C3A"
    const val NAV_BG = "#0F121A"
    const val NAV_LINE = "#1E2330"
    const val BAR_IDLE = "#2B3245"

    // Chu
    const val TEXT_PRIMARY = "#EEF1F6"
    const val TEXT_SECONDARY = "#B4BCCB"
    const val TEXT_MUTED = "#8B94A7"

    // Nhan
    const val ACCENT = "#FFC24B"
    const val ACCENT_ON = "#1A1200"
    const val SELECTED = "#9B8CFF"

    // Trang thai
    const val OK = "#4ADE80"
    const val INFO = "#60A5FA"
    const val WARN = "#FBBF24"
    const val BAD = "#F87171"

    // Nen nhat cua trang thai
    const val OK_TINT = "#12261B"
    const val INFO_TINT = "#14233A"
    const val WARN_TINT = "#2A2210"
    const val BAD_TINT = "#2A1719"
    const val SELECTED_TINT = "#1F1A33"
    const val WARN_LINE = "#5A4513"
    const val BAD_LINE = "#6B3A3F"

    /** Ten mau trong colors.xml -> gia tri o day (dung cho test doi chieu) */
    val ALL: Map<String, String> = mapOf(
        "bg" to BG, "surface" to SURFACE, "raised" to RAISED, "line" to LINE,
        "nav_bg" to NAV_BG, "nav_line" to NAV_LINE, "bar_idle" to BAR_IDLE,
        "text_primary" to TEXT_PRIMARY, "text_secondary" to TEXT_SECONDARY, "text_muted" to TEXT_MUTED,
        "accent" to ACCENT, "accent_on" to ACCENT_ON, "selected" to SELECTED,
        "ok" to OK, "info" to INFO, "warn" to WARN, "bad" to BAD,
        "ok_tint" to OK_TINT, "info_tint" to INFO_TINT, "warn_tint" to WARN_TINT, "bad_tint" to BAD_TINT,
        "selected_tint" to SELECTED_TINT, "warn_line" to WARN_LINE, "bad_line" to BAD_LINE
    )
}
