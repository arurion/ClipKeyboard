package dev.clipkeyboard.theme

import android.content.Context
import android.graphics.Color

data class ThemeConfig(
    val backgroundColor: Int = Color.parseColor("#EAF5FF"),
    val rowBackgroundColor: Int = Color.parseColor("#F8FAFC"),
    val textColor: Int = Color.parseColor("#0F172A"),
    val subTextColor: Int = Color.parseColor("#475569"),
    val accentColor: Int = Color.parseColor("#0369A1"),
    val dangerColor: Int = Color.parseColor("#E11D48"),
    val keyboardHeightDp: Float = 260f,
    val cornerRadiusDp: Float = 6f,
    val fontScale: Float = 1.0f,
    val hapticFeedbackEnabled: Boolean = true
) {
    companion object {
        private const val PREFS = "clipkeyboard_theme"

        fun load(context: Context): ThemeConfig {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val default = ThemeConfig()
            return ThemeConfig(
                backgroundColor = p.getInt("bg", default.backgroundColor),
                rowBackgroundColor = p.getInt("row_bg", default.rowBackgroundColor),
                textColor = p.getInt("text", default.textColor),
                subTextColor = p.getInt("sub_text", default.subTextColor),
                accentColor = p.getInt("accent", default.accentColor),
                dangerColor = p.getInt("danger", default.dangerColor),
                keyboardHeightDp = p.getFloat("height", default.keyboardHeightDp),
                cornerRadiusDp = p.getFloat("corner", default.cornerRadiusDp),
                fontScale = p.getFloat("font_scale", default.fontScale),
                hapticFeedbackEnabled = p.getBoolean("haptic", default.hapticFeedbackEnabled)
            )
        }

        fun save(context: Context, theme: ThemeConfig) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("bg", theme.backgroundColor)
                .putInt("row_bg", theme.rowBackgroundColor)
                .putInt("text", theme.textColor)
                .putInt("sub_text", theme.subTextColor)
                .putInt("accent", theme.accentColor)
                .putInt("danger", theme.dangerColor)
                .putFloat("height", theme.keyboardHeightDp)
                .putFloat("corner", theme.cornerRadiusDp)
                .putFloat("font_scale", theme.fontScale)
                .putBoolean("haptic", theme.hapticFeedbackEnabled)
                .apply()
        }

        fun reset(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
