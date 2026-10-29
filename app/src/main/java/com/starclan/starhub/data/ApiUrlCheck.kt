package com.starclan.starhub.data

/**
 * Kiem tra dia chi API owo-tracker nguoi dung go vao o "Dia chi API".
 * Thuan Kotlin (khong dung Android) nen co the test bang JVM unit test.
 *
 * Vi sao can: app nham targetSdk 34, Android MAC DINH chan http:// (cleartext). Truoc day go http://
 * se loi ngam o tang mang, nguoi dung khong biet vi sao. Gio bao ro ngay luc bam Luu.
 */
object ApiUrlCheck {
    enum class Result { OK, EMPTY, MISSING_SCHEME, CLEARTEXT_BLOCKED }

    fun check(raw: String): Result {
        val url = raw.trim()
        if (url.isEmpty()) return Result.EMPTY
        val lower = url.lowercase()
        return when {
            lower.startsWith("https://") -> Result.OK
            lower.startsWith("http://") -> Result.CLEARTEXT_BLOCKED
            else -> Result.MISSING_SCHEME
        }
    }
}
