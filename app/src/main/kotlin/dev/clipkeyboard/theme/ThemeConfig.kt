package dev.clipkeyboard.theme

import android.content.Context
import android.graphics.Color

/**
 * ユーザーが編集可能なキーボード配色・見た目の設定。
 * SharedPreferences (プレーンな key/value) にそのまま保存するので、
 * 追加のシリアライズライブラリは不要。
 */
data class ThemeConfig(
    val backgroundColor: Int = Color.parseColor("#1E1F22"),
    val rowBackgroundColor: Int = Color.parseColor("#2B2D30"),
    val textColor: Int = Color.parseColor("#FFFFFF"),
    val subTextColor: Int = Color.parseColor("#9AA0A6"),
    val accentColor: Int = Color.parseColor("#8AB4F8"),
    val dangerColor: Int = Color.parseColor("#F28B82"),
    val cornerRadiusDp: Float = 10f,
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
