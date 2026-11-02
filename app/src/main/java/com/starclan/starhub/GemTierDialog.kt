package com.starclan.starhub

import android.content.Context
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.starclan.starhub.data.GemPref
import com.starclan.starhub.data.GemTierChanges
import com.starclan.starhub.data.GemTierConfig

/**
 * Hop thoai chon tier gem DUOC PHEP dung cho tung loai (Hunting / Empowering / Lucky / Special).
 * Moi tier la 1 chip bat/tat (chip sang = duoc dung). Bam "Luu" thi tra ve cac loai da doi de gui len bot.
 * Dung hoan toan bang code (khong layout XML) de de chinh.
 */
object GemTierDialog {

    fun show(context: Context, config: GemTierConfig, onSave: (GemTierChanges) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val groups = LinkedHashMap<String, ChipGroup>()
        val spinners = LinkedHashMap<String, Spinner>()
        val prefValues = GemPref.options(config.tiers)
        val prefLabels = prefValues.map {
            when (it) {
                GemPref.HIGHEST -> context.getString(R.string.gem_pref_highest)
                GemPref.LOWEST -> context.getString(R.string.gem_pref_lowest)
                else -> it
            }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), px(8))
        }
        for (slot in config.slots) {
            val stockText = if (slot.stock.isEmpty()) context.getString(R.string.gem_tier_stock_empty)
            else slot.stock.joinToString(", ") { "${it.tier} ×${it.count}" }
            root.addView(TextView(context).apply {
                text = "${slot.name}"
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, px(14), 0, 0)
            })
            root.addView(TextView(context).apply {
                text = context.getString(R.string.gem_tier_stock, stockText)
                textSize = 12f
                alpha = 0.7f
            })
            val group = ChipGroup(context).apply {
                isSingleSelection = false
                chipSpacingHorizontal = px(6)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            for (tier in config.tiers) {
                group.addView(Chip(context).apply {
                    text = tier
                    isCheckable = true
                    isChecked = tier in slot.allowed
                    tag = tier
                })
            }
            groups[slot.slot] = group
            root.addView(group)

            root.addView(TextView(context).apply {
                text = context.getString(R.string.gem_pref_label)
                textSize = 13f
                setPadding(0, px(8), 0, 0)
            })
            val spinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, prefLabels)
                setSelection(GemPref.indexOf(slot.pref, config.tiers))
            }
            spinners[slot.slot] = spinner
            root.addView(spinner)
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.gem_tier_note)
            textSize = 12f
            alpha = 0.7f
            setPadding(0, px(14), 0, 0)
        })

        AlertDialog.Builder(context)
            .setTitle(R.string.gem_tier_title)
            .setView(ScrollView(context).apply { addView(root) })
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val changedAllowed = LinkedHashMap<String, List<String>>()
                val changedPref = LinkedHashMap<String, String>()
                for (slot in config.slots) {
                    val group = groups[slot.slot] ?: continue
                    val now = config.tiers.filter { tier ->
                        (0 until group.childCount).map { group.getChildAt(it) as Chip }
                            .any { it.tag == tier && it.isChecked }
                    }
                    if (now != slot.allowed) changedAllowed[slot.slot] = now
                    val picked = spinners[slot.slot]?.selectedItemPosition ?: continue
                    val newPref = prefValues.getOrNull(picked) ?: continue
                    if (picked != GemPref.indexOf(slot.pref, config.tiers)) changedPref[slot.slot] = newPref
                }
                onSave(GemTierChanges(changedAllowed, changedPref))
            }
            .show()
    }
}
