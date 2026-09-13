package dev.clipkeyboard.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

object ThemeUtils {
    private val EDGE_COLOR = Color.parseColor("#64748B")

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

    fun blendColors(baseColor: Int, overlayColor: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val a = (Color.alpha(baseColor) * (1 - r) + Color.alpha(overlayColor) * r).toInt()
        val red = (Color.red(baseColor) * (1 - r) + Color.red(overlayColor) * r).toInt()
        val g = (Color.green(baseColor) * (1 - r) + Color.green(overlayColor) * r).toInt()
        val b = (Color.blue(baseColor) * (1 - r) + Color.blue(overlayColor) * r).toInt()
        return Color.argb(a, red, g, b)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * ガラス行(Row)の背景Drawable。
     * インデックス0(影)を下方向にオフセットし、インデックス1(ガラス面)を歪めずに正しく重ねる。
     */
    fun glassRowBackground(context: Context, theme: ThemeConfig): Drawable {
        val radiusPx = theme.cornerRadiusDp * context.resources.displayMetrics.density
        val shadowPx = dp(context, 1f)

        fun buildLayer(fillColor: Int): LayerDrawable {
            val shadow = GradientDrawable().apply {
                setColor(withAlpha(Color.BLACK, 15))
                cornerRadius = radiusPx
            }
            val glass = GradientDrawable().apply {
                setColor(fillColor)
                cornerRadius = radiusPx
                setStroke(dp(context, 0.8f).coerceAtLeast(1), withAlpha(EDGE_COLOR, 50))
            }
            val layer = LayerDrawable(arrayOf(shadow, glass))
            // 影(インデックス0)を1dp下に押し出す
            layer.setLayerInset(0, 0, shadowPx, 0, 0)
            return layer
        }

        val normal = buildLayer(withAlpha(theme.rowBackgroundColor, 225))
        val pressed = buildLayer(blendColors(theme.rowBackgroundColor, theme.accentColor, 0.25f))

        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }

        // リップルマスクには単一の角丸シェイプを使用し、境界漏れを防ぐ
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusPx
        }

        return try {
            RippleDrawable(android.content.res.ColorStateList.valueOf(withAlpha(theme.accentColor, 70)), stateList, mask)
        } catch (_: Exception) {
            stateList
        }
    }

    fun applyGlassRowBackground(view: View, theme: ThemeConfig) {
        view.background = glassRowBackground(view.context, theme)
    }

    fun headerBackgroundColor(theme: ThemeConfig): Int =
        blendColors(theme.backgroundColor, theme.accentColor, 0.12f)

    fun headerShadowDrawable(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.BLACK, 25), Color.TRANSPARENT)
        )
}
