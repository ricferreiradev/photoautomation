package com.ricferreira.photoautomation

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Visual styling only: existing controls, listeners and page state stay mounted. */
object AppAppearance {
    private val ink = Color.rgb(232, 238, 247)
    private val muted = Color.rgb(165, 179, 198)
    private val accent = Color.rgb(146, 187, 255)
    private fun View.dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun View.surface(color: Int, radius: Int = 18, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke) setStroke(dp(1), Color.rgb(48, 61, 79))
    }
    private fun style(view: View) {
        when (view) {
            is CheckBox -> {
                view.textSize = 15f; view.setTextColor(ink); view.buttonTintList = ColorStateList.valueOf(accent)
                view.minHeight = view.dp(56); view.setPadding(view.dp(4), view.dp(8), view.dp(4), view.dp(8))
            }
            is EditText -> {
                view.textSize = 16f; view.setTextColor(ink); view.setHintTextColor(muted)
                view.backgroundTintList = null
                view.background = view.surface(Color.rgb(17, 25, 37), 12, true)
                view.setPadding(view.dp(14), view.dp(14), view.dp(14), view.dp(14))
                view.minHeight = view.dp(52)
                (view.layoutParams as? LinearLayout.LayoutParams)?.apply { topMargin = view.dp(4); bottomMargin = view.dp(12) }
            }
            is Button -> {
                val primary = view.text.startsWith("Salvar") || view.text.startsWith("Testar análise")
                val danger = view.text.startsWith("Apagar")
                view.isAllCaps = false; view.textSize = 14f; view.setTypeface(null, Typeface.BOLD)
                view.gravity = android.view.Gravity.CENTER
                view.minHeight = view.dp(50); view.minimumHeight = view.dp(50)
                view.setPadding(view.dp(14), view.dp(10), view.dp(14), view.dp(10))
                view.setTextColor(if (primary) Color.WHITE else if (danger) Color.rgb(255, 164, 175) else accent)
                view.backgroundTintList = null
                view.stateListAnimator = null
                view.elevation = 0f
                view.background = RippleDrawable(ColorStateList.valueOf(0x203553AA),
                    view.surface(if (primary) Color.rgb(35, 83, 170) else if (danger) Color.rgb(54, 30, 39) else Color.rgb(30, 44, 64), 12), null)
                view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = view.dp(6); bottomMargin = view.dp(2) }
            }
            is TextView -> {
                view.textSize = 14f; view.setTextColor(muted)
                view.setLineSpacing(view.dp(3).toFloat(), 1f)
                view.setPadding(0, view.dp(6), 0, view.dp(8))
            }
            is Spinner -> {
                view.background = view.surface(Color.rgb(30, 44, 64), 12, true)
                view.minimumHeight = view.dp(56)
                view.setPadding(view.dp(12), 0, view.dp(8), 0)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
    }
    private fun card(page: LinearLayout, children: List<View>, title: String? = null) {
        if (children.isEmpty()) return
        val index = page.indexOfChild(children.first())
        val box = LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(18))
            background = surface(Color.rgb(23, 32, 46), 20, true)
        }
        children.forEach { page.removeView(it); box.addView(it) }
        page.addView(box, index, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = page.dp(16) })
        val heading = children.firstOrNull() as? TextView
        if (heading != null && title != null) {
            heading.text = title; heading.textSize = 17f; heading.setTextColor(ink); heading.setTypeface(null, Typeface.BOLD)
        }
    }
    fun page(page: LinearLayout, subtitle: String, boundaries: Set<String>) {
        val original = (0 until page.childCount).map { page.getChildAt(it) }
        original.forEach(::style)
        val title = original.first() as TextView
        title.textSize = 30f; title.setTextColor(ink); title.setTypeface(null, Typeface.BOLD)
        title.setPadding(0, page.dp(4), 0, page.dp(4))
        val description = TextView(page.context).apply {
            text = subtitle; textSize = 14f; setTextColor(muted)
            setPadding(0, 0, 0, dp(24)); setLineSpacing(dp(3).toFloat(), 1f)
        }
        page.addView(description, 1)
        var group = mutableListOf<View>()
        fun flush() {
            if (group.isNotEmpty()) {
                val heading = (group.first() as? TextView)?.text?.toString()
                card(page, group, heading?.takeIf { it in boundaries })
                group = mutableListOf()
            }
        }
        for (view in original.drop(1)) {
            val heading = (view as? TextView)?.text?.toString()
            if (heading in boundaries || view is LinearLayout) flush()
            if (view is LinearLayout) {
                // Existing role-aware sections keep their visibility and ownership.
                view.background = view.surface(Color.rgb(23, 32, 46), 20, true)
                view.setPadding(view.dp(18), view.dp(12), view.dp(18), view.dp(18))
                view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = view.dp(16) }
                (view.getChildAt(0) as? TextView)?.apply {
                    textSize = 17f; setTextColor(ink); setTypeface(null, Typeface.BOLD)
                }
            } else group.add(view)
        }
        flush()
    }
}
