package dev.clipkeyboard.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.clipkeyboard.databinding.ActivityThemeSettingsBinding
import dev.clipkeyboard.databinding.ItemClipRowBinding
import dev.clipkeyboard.databinding.PreviewKeyboardMockBinding
import dev.clipkeyboard.databinding.RowColorFieldBinding
import dev.clipkeyboard.theme.ThemeConfig
import dev.clipkeyboard.theme.ThemeUtils

class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeSettingsBinding
    private lateinit var preview: PreviewKeyboardMockBinding

    private class ColorField(
        val row: RowColorFieldBinding,
        val label: String,
        var currentColor: Int,
        val get: (ThemeConfig) -> Int
    )

    private lateinit var colorFields: List<ColorField>
    private var isBinding = false

    private val presets = listOf(
        "サックス・ガラス" to ThemeConfig(),
        "スレート" to ThemeConfig(
            backgroundColor = Color.parseColor("#0F172A"),
            rowBackgroundColor = Color.parseColor("#1E293B"),
            textColor = Color.parseColor("#F1F5F9"),
            subTextColor = Color.parseColor("#94A3B8"),
            accentColor = Color.parseColor("#38BDF8"),
            dangerColor = Color.parseColor("#FB7185")
        ),
        "サクラ" to ThemeConfig(
            backgroundColor = Color.parseColor("#FFF1F5"),
            rowBackgroundColor = Color.parseColor("#FFE1EC"),
            textColor = Color.parseColor("#4A2B36"),
            subTextColor = Color.parseColor("#9C6B7C"),
            accentColor = Color.parseColor("#DB2777"),
            dangerColor = Color.parseColor("#E11D48")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preview = PreviewKeyboardMockBinding.inflate(LayoutInflater.from(this), binding.previewContainer, true)

        val initialTheme = ThemeConfig.load(this)

        colorFields = listOf(
            ColorField(binding.rowBg, "背景色", initialTheme.backgroundColor) { it.backgroundColor },
            ColorField(binding.rowRowBg, "行の背景色", initialTheme.rowBackgroundColor) { it.rowBackgroundColor },
            ColorField(binding.rowText, "文字色", initialTheme.textColor) { it.textColor },
            ColorField(binding.rowSubText, "補助文字色", initialTheme.subTextColor) { it.subTextColor },
            ColorField(binding.rowAccent, "アクセント色", initialTheme.accentColor) { it.accentColor },
            ColorField(binding.rowDanger, "警告色", initialTheme.dangerColor) { it.dangerColor }
        )

        colorFields.forEach { field ->
            field.row.fieldLabel.text = field.label
            field.row.fieldHex.addTextChangedListener(SimpleWatcher {
                if (!isBinding) updateSwatchAndPreview(field)
            })
            field.row.fieldSwatch.setOnClickListener { field.row.fieldHex.requestFocus() }
        }

        presets.forEach { (name, preset) ->
            val btn = com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = name
                textSize = 12f
                insetTop = 0
                insetBottom = 0
                val margin = ThemeUtils.dp(context, 4f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    ThemeUtils.dp(context, 38f)
                ).apply { setMargins(margin, 0, margin, 0) }
                setOnClickListener { bindFields(preset) }
            }
            binding.presetContainer.addView(btn)
        }

        binding.seekFontScale.max = 70
        bindFields(initialTheme)

        binding.seekCorner.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSliderLabels()
                if (!isBinding) refreshPreview()
            }
        })
        binding.seekFontScale.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSliderLabels()
                if (!isBinding) refreshPreview()
            }
        })

        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnReset.setOnClickListener {
            ThemeConfig.reset(this)
            bindFields(ThemeConfig())
            Toast.makeText(this, "既定のテーマに戻しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSliderLabels() {
        binding.labelCornerValue.text = "${binding.seekCorner.progress} dp"
        val percent = 70 + binding.seekFontScale.progress
        binding.labelFontValue.text = "$percent %"
    }

    private fun bindFields(theme: ThemeConfig) {
        isBinding = true
        colorFields.forEach { field ->
            val color = field.get(theme)
            field.currentColor = color
            val hexVal = hex(color)
            field.row.fieldHex.setText(hexVal)
            field.row.fieldSwatch.background = ThemeUtils.roundedDrawable(color, 6f, this)
        }
        binding.seekCorner.progress = theme.cornerRadiusDp.toInt().coerceIn(0, binding.seekCorner.max)
        binding.seekFontScale.progress = ((theme.fontScale * 100).toInt() - 70).coerceIn(0, binding.seekFontScale.max)
        binding.switchHaptic.isChecked = theme.hapticFeedbackEnabled
        updateSliderLabels()
        isBinding = false
        refreshPreview()
    }

    private fun updateSwatchAndPreview(field: ColorField) {
        val text = field.row.fieldHex.text.toString()
        try {
            val parsed = Color.parseColor(text)
            field.currentColor = parsed
            field.row.fieldSwatch.background = ThemeUtils.roundedDrawable(parsed, 6f, this)
        } catch (_: Exception) {
        }
        refreshPreview()
    }

    private fun currentThemeFromFields(): ThemeConfig {
        return ThemeConfig(
            backgroundColor = colorFields[0].currentColor,
            rowBackgroundColor = colorFields[1].currentColor,
            textColor = colorFields[2].currentColor,
            subTextColor = colorFields[3].currentColor,
            accentColor = colorFields[4].currentColor,
            dangerColor = colorFields[5].currentColor,
            cornerRadiusDp = binding.seekCorner.progress.toFloat(),
            fontScale = (70 + binding.seekFontScale.progress) / 100f,
            hapticFeedbackEnabled = binding.switchHaptic.isChecked
        )
    }

    private fun refreshPreview() {
        val theme = currentThemeFromFields()

        preview.previewRoot.setBackgroundColor(theme.backgroundColor)
        preview.previewHeader.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))
        preview.previewHeaderShadow.background = ThemeUtils.headerShadowDrawable()
        preview.previewHeaderIcon.setColorFilter(theme.accentColor)
        preview.previewHeaderClose.setColorFilter(theme.subTextColor)
        preview.previewHeaderTitle.setTextColor(theme.textColor)

        applyPreviewRow(preview.previewRow1, theme, label = "プレビュー用ラベル", body = "タップするとこのように表示されます", pinned = true)
        applyPreviewRow(preview.previewRow2, theme, label = null, body = "2行目のクリップ例です", pinned = false)
    }

    private fun applyPreviewRow(
        row: ItemClipRowBinding,
        theme: ThemeConfig,
        label: String?,
        body: String,
        pinned: Boolean
    ) {
        ThemeUtils.applyGlassRowBackground(row.rowRoot, theme)
        row.textBody.text = body
        row.textBody.setTextColor(theme.textColor)
        row.textBody.textSize = 14f * theme.fontScale
        row.textLabel.text = label
        row.textLabel.visibility = if (label.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        row.textLabel.setTextColor(theme.accentColor)
        row.imgPinIndicator.visibility = if (pinned) android.view.View.VISIBLE else android.view.View.GONE
        row.imgPinIndicator.setColorFilter(theme.accentColor)
        row.textTime.text = "12:34"
        row.textTime.setTextColor(theme.subTextColor)
    }

    private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    private fun saveAndFinish() {
        ThemeConfig.save(this, currentThemeFromFields())
        Toast.makeText(this, "テーマを保存しました。キーボードを開き直すと反映されます。", Toast.LENGTH_LONG).show()
        finish()
    }

    private class SimpleWatcher(private val onChanged: () -> Unit) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged() }
        override fun afterTextChanged(s: Editable?) {}
    }

    private open class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
