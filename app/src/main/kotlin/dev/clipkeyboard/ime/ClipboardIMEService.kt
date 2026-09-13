package dev.clipkeyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.clipkeyboard.R
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.data.ClipboardWatcher
import dev.clipkeyboard.theme.ThemeConfig
import dev.clipkeyboard.theme.ThemeUtils
import dev.clipkeyboard.ui.EditClipActivity

class ClipboardIMEService : InputMethodService() {

    private lateinit var theme: ThemeConfig
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ClipAdapter
    private lateinit var countText: TextView
    private lateinit var emptyText: TextView
    private lateinit var quickActionsOverlay: LinearLayout
    private lateinit var overlayPreviewText: TextView
    private lateinit var overlayPinLabel: TextView
    private lateinit var overlayPinIcon: ImageView

    private val storeListener = { refreshList() }

    override fun onCreate() {
        super.onCreate()
        ClipboardWatcher.register(this)
        ClipStore.addListener(storeListener)
    }

    override fun onDestroy() {
        ClipStore.removeListener(storeListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        theme = ThemeConfig.load(this)
        val root = LayoutInflater.from(this).inflate(R.layout.ime_keyboard_view, null) as FrameLayout

        val contentContainer = root.findViewById<LinearLayout>(R.id.ime_content_container)
        val header = root.findViewById<LinearLayout>(R.id.ime_header)
        val headerShadow = root.findViewById<View>(R.id.header_shadow)
        val switchBtn = root.findViewById<ImageButton>(R.id.btn_switch_ime)
        val titleText = root.findViewById<TextView>(R.id.text_ime_title)
        val addBtn = root.findViewById<ImageButton>(R.id.btn_quick_add)
        val closeBtn = root.findViewById<ImageButton>(R.id.btn_close_keyboard)
        countText = root.findViewById(R.id.text_count)
        emptyText = root.findViewById(R.id.text_empty)
        recycler = root.findViewById(R.id.recycler_clips)

        // オーバーレイ参照
        quickActionsOverlay = root.findViewById(R.id.overlay_quick_actions)
        overlayPreviewText = root.findViewById(R.id.overlay_preview_text)
        overlayPinLabel = root.findViewById(R.id.action_pin_label)
        overlayPinIcon = root.findViewById(R.id.action_pin_icon)

        // 背景とテーマ適用
        contentContainer.setBackgroundColor(theme.backgroundColor)
        header.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))
        headerShadow.background = ThemeUtils.headerShadowDrawable()
        titleText.setTextColor(theme.textColor)
        countText.setTextColor(theme.subTextColor)
        emptyText.setTextColor(theme.subTextColor)
        switchBtn.setColorFilter(theme.accentColor)
        addBtn.setColorFilter(theme.subTextColor)
        closeBtn.setColorFilter(theme.subTextColor)

        // 操作リスナー
        switchBtn.setOnClickListener { handleSwitchToPreviousIme() }
        addBtn.setOnClickListener { openCreateInHostApp() }
        closeBtn.setOnClickListener { requestHideSelf(0) }

        // 背景タップでオーバーレイを閉じる
        quickActionsOverlay.setOnClickListener {
            quickActionsOverlay.visibility = View.GONE
        }

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ClipAdapter(
            items = emptyList(),
            theme = theme,
            onTap = { item ->
                if (quickActionsOverlay.visibility == View.VISIBLE) {
                    quickActionsOverlay.visibility = View.GONE
                } else {
                    commitClip(item)
                }
            },
            onLongPress = { item -> showQuickActionsOverlay(item) }
        )
        recycler.adapter = adapter

        refreshList()
        return root
    }

    private fun refreshList() {
        if (!::adapter.isInitialized) return
        val all = ClipStore.getAll(this)
        adapter.submit(all)
        countText.text = "${all.size}件"
        emptyText.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun commitClip(item: ClipItem) {
        currentInputConnection?.commitText(item.text, 1)
    }

    /**
     * 長押し時にダイアログではなく、Viewオーバーレイで安全にクイックメニューを表示
     * (Window Token不要で100%クラッシュしない)
     */
    private fun showQuickActionsOverlay(item: ClipItem) {
        overlayPreviewText.text = item.text
        overlayPinLabel.text = if (item.pinned) "ピン留めを解除する" else "ピン留めする"
        overlayPinIcon.setColorFilter(theme.accentColor)

        quickActionsOverlay.findViewById<LinearLayout>(R.id.action_toggle_pin).setOnClickListener {
            ClipStore.togglePin(this, item.id)
            quickActionsOverlay.visibility = View.GONE
        }

        quickActionsOverlay.findViewById<LinearLayout>(R.id.action_edit).setOnClickListener {
            quickActionsOverlay.visibility = View.GONE
            openEditInHostApp(item)
        }

        quickActionsOverlay.findViewById<LinearLayout>(R.id.action_delete).setOnClickListener {
            ClipStore.delete(this, item.id)
            quickActionsOverlay.visibility = View.GONE
        }

        quickActionsOverlay.visibility = View.VISIBLE
    }

    private fun handleSwitchToPreviousIme() {
        var success = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            success = try { switchToPreviousInputMethod() } catch (_: Exception) { false }
        }

        if (!success) {
            success = try { switchToNextInputMethod(false) } catch (_: Exception) { false }
        }

        if (!success) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun openEditInHostApp(item: ClipItem) {
        val intent = Intent(this, EditClipActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EditClipActivity.EXTRA_CLIP_ID, item.id)
        }
        startActivity(intent)
    }

    private fun openCreateInHostApp() {
        val intent = Intent(this, EditClipActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::quickActionsOverlay.isInitialized) {
            quickActionsOverlay.visibility = View.GONE
        }
        refreshList()
    }
}
