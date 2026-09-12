package dev.clipkeyboard.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View

object ThemeUtils {
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun roundedDrawable(color: Int, radiusDp: Float, context: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * context.resources.displayMetrics.density
        }

    fun applyKeyBackground(view: View, theme: ThemeConfig, color: Int) {
        view.background = roundedDrawable(color, theme.cornerRadiusDp, view.context)
    }
}
