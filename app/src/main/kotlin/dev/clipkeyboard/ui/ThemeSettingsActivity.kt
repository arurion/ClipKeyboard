package dev.clipkeyboard.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.clipkeyboard.databinding.ActivityThemeSettingsBinding
import dev.clipkeyboard.theme.ThemeConfig

/**
 * カラーピッカーライブラリに依存せず、#RRGGBB の16進テキスト入力 + プリセットボタンで
 * 配色を編集できるシンプルな設定画面。
 */
class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeSettingsBinding

    private val presets = listOf(
        "ダーク" to ThemeConfig(
            backgroundColor = Color.parseColor("#1E1F22"),
            rowBackgroundColor = Color.parseColor("#2B2D30"),
            textColor = Color.parseColor("#FFFFFF"),
            subTextColor = Color.parseColor("#9AA0A6"),
            accentColor = Color.parseColor("#8AB4F8"),
            dangerColor = Color.parseColor("#F28B82")
        ),
        "ライト" to ThemeConfig(
            backgroundColor = Color.parseColor("#F5F5F5"),
            rowBackgroundColor = Color.parseColor("#FFFFFF"),
            textColor = Color.parseColor("#202124"),
            subTextColor = Color.parseColor("#5F6368"),
            accentColor = Color.parseColor("#1A73E8"),
            dangerColor = Color.parseColor("#D93025")
        ),
        "パステル" to ThemeConfig(
            backgroundColor = Color.parseColor("#FFF1F5"),
            rowBackgroundColor = Color.parseColor("#FFE1EC"),
            textColor = Color.parseColor("#5A3E4A"),
            subTextColor = Color.parseColor("#B08498"),
            accentColor = Color.parseColor("#FF7FA6"),
            dangerColor = Color.parseColor("#E5484D")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val theme = ThemeConfig.load(this)
        bindFields(theme)

        presets.forEachIndexed { i, (name, preset) ->
            val btn = com.google.android.material.button.MaterialButton(this)
            btn.text = name
            btn.setOnClickListener { bindFields(preset) }
            binding.presetContainer.addView(btn)
        }

        binding.seekCorner.progress = theme.cornerRadiusDp.toInt()
        binding.seekFontScale.progress = ((theme.fontScale - 0.7f) * 100).toInt().coerceIn(0, 100)
        binding.switchHaptic.isChecked = theme.hapticFeedbackEnabled

        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnReset.setOnClickListener {
            ThemeConfig.reset(this)
            bindFields(ThemeConfig())
            Toast.makeText(this, "既定のテーマに戻しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindFields(theme: ThemeConfig) {
        binding.editBg.setText(hex(theme.backgroundColor))
        binding.editRowBg.setText(hex(theme.rowBackgroundColor))
        binding.editText.setText(hex(theme.textColor))
        binding.editSubText.setText(hex(theme.subTextColor))
        binding.editAccent.setText(hex(theme.accentColor))
        binding.editDanger.setText(hex(theme.dangerColor))
    }

    private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    private fun parseColorSafe(text: String, fallback: Int): Int =
        try { Color.parseColor(text) } catch (_: Exception) { fallback }

    private fun saveAndFinish() {
        val default = ThemeConfig()
        val theme = ThemeConfig(
            backgroundColor = parseColorSafe(binding.editBg.text.toString(), default.backgroundColor),
            rowBackgroundColor = parseColorSafe(binding.editRowBg.text.toString(), default.rowBackgroundColor),
            textColor = parseColorSafe(binding.editText.text.toString(), default.textColor),
            subTextColor = parseColorSafe(binding.editSubText.text.toString(), default.subTextColor),
            accentColor = parseColorSafe(binding.editAccent.text.toString(), default.accentColor),
            dangerColor = parseColorSafe(binding.editDanger.text.toString(), default.dangerColor),
            cornerRadiusDp = binding.seekCorner.progress.toFloat(),
            fontScale = 0.7f + binding.seekFontScale.progress / 100f,
            hapticFeedbackEnabled = binding.switchHaptic.isChecked
        )
        ThemeConfig.save(this, theme)
        Toast.makeText(this, "テーマを保存しました。キーボードを開き直すと反映されます。", Toast.LENGTH_LONG).show()
        finish()
    }
}
