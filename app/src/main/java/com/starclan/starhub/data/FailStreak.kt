package com.starclan.starhub.data

/**
 * Dem so lan loi LIEN TIEP cua vong farm (thuan Kotlin, test duoc bang JVM).
 *
 * Thu tu hunt / battle / owo ngau nhien moi vong, nen KHONG duoc xoa bo dem ngay khi 1 lenh go thanh cong: neu chi battle
 * loi con hunt van OK thi bo dem se bi xoa ve 0 moi vong va app khong bao gio tu dung / mo lai kenh.
 * Quy tac: loi thi +1 ngay; chi ve 0 khi KET THUC 1 vong ma vong do co it nhat 1 lenh OK va khong co loi nao.
 * Vong khong go gi (hunt tam dung, lenh bi tat) thi giu nguyen.
 */
class FailStreak(private val max: Int) {
    var count: Int = 0
        private set
    private var cycleFailed = false
    private var cycleSentOk = false

    /** Goi dau moi vong farm. */
    fun beginCycle() {
        cycleFailed = false
        cycleSentOk = false
    }

    /** 1 lenh trong vong go thanh cong. */
    fun ok() {
        cycleSentOk = true
    }

    /** 1 lenh (hoac buoc mo lai kenh) that bai. Tra ve true neu da du `max` lan lien tiep -> phai dung han. */
    fun fail(): Boolean {
        count++
        cycleFailed = true
        return count >= max
    }

    /** Goi sau khi lam xong cac lenh cua vong (khong goi neu vong ket thuc som vi loi). */
    fun endCycle() {
        if (cycleSentOk && !cycleFailed) count = 0
    }
}
