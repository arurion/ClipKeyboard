package dev.clipkeyboard.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

/**
 * Tinted Glass UI Philosophy に基づくビジュアルヘルパー。
 * 「境界は線ではなく光と影でつくる」という原則に沿い、単色のベタ塗りではなく
 * ・淡いシルバーグレーの極細エッジ
 * ・下方向へのごく微細なアンビエントシャドウ
 * を重ねたレイヤードローアブルを動的に生成する。色や角丸はすべてテーマ値から算出するため、
 * ユーザーがどんな配色を選んでも同じ質感原則が保たれる。
 */
object ThemeUtils {
    // 原則4「エッジは常にスモーキーなシルバーグレー」: どのテーマ色でもこの色味で統一する
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

    /** baseColor に overlayColor を ratio (0..1) だけ混ぜた色を返す。ヘッダーの「ガラス・クローム」等に使用。 */
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
     * 「行(Row)」用のガラス風背景。分厚いカード枠にはせず、
     * ・本体: rowColor をわずかに透過させた面 + 極細シルバーエッジ
     * ・影: 下端だけにアンビエントシャドウを1dp重ねる
     * の2層を LayerDrawable で構成する。通常時/押下時(タップの光)でStateListDrawableを切り替える。
     */
    fun glassRowBackground(context: Context, theme: ThemeConfig): Drawable {
        val radiusPx = theme.cornerRadiusDp * context.resources.displayMetrics.density
        val shadowPx = dp(context, 1f)

        fun buildLayer(fillColor: Int): LayerDrawable {
            val shadow = GradientDrawable().apply {
                setColor(withAlpha(Color.BLACK, 13))
                cornerRadius = radiusPx
            }
            val glass = GradientDrawable().apply {
                setColor(fillColor)
                cornerRadius = radiusPx
                setStroke(dp(context, 0.7f).coerceAtLeast(1), withAlpha(EDGE_COLOR, 60))
            }
            val layer = LayerDrawable(arrayOf(shadow, glass))
            // 影は本体より少しだけ下にオフセットさせ、輪郭ではなく「沈み込み」に見せる
            layer.setLayerInset(1, 0, 0, 0, shadowPx)
            return layer
        }

        val normal = buildLayer(withAlpha(theme.rowBackgroundColor, 200))
        val pressed = buildLayer(blendColors(theme.rowBackgroundColor, theme.accentColor, 0.22f))

        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        return try {
            RippleDrawable(android.content.res.ColorStateList.valueOf(withAlpha(theme.accentColor, 60)), stateList, normal)
        } catch (_: Exception) {
            stateList
        }
    }

    fun applyGlassRowBackground(view: View, theme: ThemeConfig) {
        view.background = glassRowBackground(view.context, theme)
    }

    /** ヘッダー(ガラス・クローム)の背景色: 背景色にアクセント色を薄く混ぜて質感を出す。 */
    fun headerBackgroundColor(theme: ThemeConfig): Int =
        blendColors(theme.backgroundColor, theme.accentColor, 0.10f)

    /** ヘッダー下の「面と影の境界」用: 黒い罫線を引かず、ごく薄いグラデーションで表現する。 */
    fun headerShadowDrawable(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.BLACK, 20), Color.TRANSPARENT)
        )
}
