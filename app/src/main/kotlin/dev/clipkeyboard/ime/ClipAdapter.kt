package dev.clipkeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.clipkeyboard.R
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.theme.ThemeConfig
import dev.clipkeyboard.theme.ThemeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipAdapter(
    private var items: List<ClipItem>,
    private var theme: ThemeConfig,
    private val onTap: (ClipItem) -> Unit,
    private val onLongPress: (ClipItem) -> Unit
) : RecyclerView.Adapter<ClipAdapter.VH>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(newItems: List<ClipItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateThemeAndItems(newTheme: ThemeConfig, newItems: List<ClipItem>) {
        theme = newTheme
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val pinIcon: ImageView = root.findViewById(R.id.img_pin_indicator)
        val label: TextView = root.findViewById(R.id.text_label)
        val body: TextView = root.findViewById(R.id.text_body)
        val time: TextView = root.findViewById(R.id.text_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clip_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.body.text = item.text
        holder.body.setTextColor(theme.textColor)
        holder.body.textSize = 14f * theme.fontScale

        holder.label.text = item.label
        holder.label.visibility = if (item.label.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.label.setTextColor(theme.accentColor)

        holder.pinIcon.visibility = if (item.pinned) View.VISIBLE else View.GONE
        holder.pinIcon.setColorFilter(theme.accentColor)

        holder.time.text = timeFormat.format(Date(item.updatedAt))
        holder.time.setTextColor(theme.subTextColor)

        ThemeUtils.applyGlassRowBackground(holder.root, theme)

        holder.root.setOnClickListener {
            if (theme.hapticFeedbackEnabled) {
                holder.root.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            onTap(item)
        }
        holder.root.setOnLongClickListener {
            onLongPress(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
