package dev.clipkeyboard.ime

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.theme.ThemeConfig
import dev.clipkeyboard.theme.ThemeUtils

class ClipAdapter(
    private var items: List<ClipItem>,
    private val theme: ThemeConfig,
    private val onTap: (ClipItem) -> Unit,
    private val onLongPress: (ClipItem) -> Unit,
    private val onPinToggle: (ClipItem) -> Unit,
    private val onDelete: (ClipItem) -> Unit
) : RecyclerView.Adapter<ClipAdapter.VH>() {

    fun submit(newItems: List<ClipItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val title = TextView(root.context)
        val body = TextView(root.context)
        val pinBtn = TextView(root.context)
        val delBtn = TextView(root.context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val pad = ThemeUtils.dp(ctx, 10f)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(pad, pad / 2, pad, pad / 2) }
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_VERTICAL
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val vh = VH(root)
        vh.title.apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            textSize = 12f * theme.fontScale
        }
        vh.body.apply {
            maxLines = 2
            textSize = 15f * theme.fontScale
        }
        textCol.addView(vh.title)
        textCol.addView(vh.body)

        vh.pinBtn.apply {
            text = "📌"
            textSize = 16f
            setPadding(pad, 0, pad, 0)
        }
        vh.delBtn.apply {
            text = "🗑"
            textSize = 16f
            setPadding(pad, 0, 0, 0)
        }

        root.addView(textCol)
        root.addView(vh.pinBtn)
        root.addView(vh.delBtn)
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = if (item.pinned) "📌 " + (item.label ?: "ピン留め") else (item.label ?: "")
        holder.title.visibility = if (item.label.isNullOrBlank() && !item.pinned) View.GONE else View.VISIBLE
        holder.body.text = item.text

        holder.body.setTextColor(theme.textColor)
        holder.title.setTextColor(theme.accentColor)
        ThemeUtils.applyKeyBackground(holder.root, theme, theme.rowBackgroundColor)

        holder.root.setOnClickListener {
            if (theme.hapticFeedbackEnabled) {
                holder.root.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            onTap(item)
        }
        holder.root.setOnLongClickListener { onLongPress(item); true }
        holder.pinBtn.setOnClickListener { onPinToggle(item) }
        holder.delBtn.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
