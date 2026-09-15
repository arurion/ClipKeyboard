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
    private lateinit var overlayEditIcon: ImageView
    private lateinit var overlayDeleteIcon: ImageView

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
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_ClipKeyboard)
        theme = ThemeConfig.load(themedContext)

        val root = LayoutInflater.from(themedContext).inflate(R.layout.ime_keyboard_view, null) as FrameLayout

        val contentContainer = root.findViewById<LinearLayout>(R.id.ime_content_container)
        contentContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            ThemeUtils.dp(themedContext, 240f)
        )

        val header = root.findViewById<LinearLayout>(R.id.ime_header)
        val headerShadow = root.findViewById<View>(R.id.header_shadow)
        val switchBtn = root.findViewById<ImageButton>(R.id.btn_switch_ime)
        val titleText = root.findViewById<TextView>(R.id.text_ime_title)
        val addBtn = root.findViewById<ImageButton>(R.id.btn_quick_add)
        val closeBtn = root.findViewById<ImageButton>(R.id.btn_close_keyboard)
        countText = root.findViewById(R.id.text_count)
        emptyText = root.findViewById(R.id.text_empty)
        recycler = root.findViewById(R.id.recycler_clips)

        val editBar = root.findViewById<LinearLayout>(R.id.ime_edit_bar)
        val btnUndo = root.findViewById<ImageButton>(R.id.btn_undo)
        val btnRedo = root.findViewById<ImageButton>(R.id.btn_redo)
        val btnLeft = root.findViewById<ImageButton>(R.id.btn_dpad_left)
        val btnUp = root.findViewById<ImageButton>(R.id.btn_dpad_up)
        val btnDown = root.findViewById<ImageButton>(R.id.btn_dpad_down)
        val btnRight = root.findViewById<ImageButton>(R.id.btn_dpad_right)
        val btnBackspace = root.findViewById<ImageButton>(R.id.btn_backspace)
        val btnEnter = root.findViewById<ImageButton>(R.id.btn_enter)

        quickActionsOverlay = root.findViewById(R.id.overlay_quick_actions)
        overlayPreviewText = root.findViewById(R.id.overlay_preview_text)
        overlayPinLabel = root.findViewById(R.id.action_pin_label)
        overlayPinIcon = root.findViewById(R.id.action_pin_icon)
        overlayEditIcon = root.findViewById(R.id.action_edit_icon)
        overlayDeleteIcon = root.findViewById(R.id.action_delete_icon)

        contentContainer.setBackgroundColor(theme.backgroundColor)
        header.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))
        headerShadow.background = ThemeUtils.headerShadowDrawable()
        editBar.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))

        titleText.setTextColor(theme.textColor)
        countText.setTextColor(theme.subTextColor)
        emptyText.setTextColor(theme.subTextColor)

        switchBtn.setColorFilter(theme.accentColor)
        addBtn.setColorFilter(theme.subTextColor)
        closeBtn.setColorFilter(theme.subTextColor)

        btnUndo.setColorFilter(theme.subTextColor)
        btnRedo.setColorFilter(theme.subTextColor)
        btnLeft.setColorFilter(theme.textColor)
        btnUp.setColorFilter(theme.textColor)
        btnDown.setColorFilter(theme.textColor)
        btnRight.setColorFilter(theme.textColor)
        btnBackspace.setColorFilter(theme.dangerColor)
        btnEnter.setColorFilter(theme.accentColor)

        overlayEditIcon.setColorFilter(theme.subTextColor)
        overlayDeleteIcon.setColorFilter(theme.dangerColor)

        switchBtn.setOnClickListener { handleSwitchToPreviousIme() }
        addBtn.setOnClickListener { openCreateInHostApp() }
        closeBtn.setOnClickListener { requestHideSelf(0) }

        btnUndo.setOnClickListener { triggerEditAction { performUndo() } }
        btnRedo.setOnClickListener { triggerEditAction { performRedo() } }
        btnLeft.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_LEFT) } }
        btnUp.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_UP) } }
        btnDown.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_DOWN) } }
        btnRight.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) } }
        btnBackspace.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL) } }
        btnEnter.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER) } }

        quickActionsOverlay.setOnClickListener {
            quickActionsOverlay.visibility = View.GONE
        }

        recycler.layoutManager = LinearLayoutManager(themedContext)
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

    private inline fun triggerEditAction(action: () -> Unit) {
        if (theme.hapticFeedbackEnabled) {
            val root = window?.window?.decorView
            root?.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
        action()
    }

    private fun performUndo() {
        val ic = currentInputConnection ?: return
        val success = ic.performContextMenuAction(android.R.id.undo)
        if (!success) {
            sendCtrlKey(android.view.KeyEvent.KEYCODE_Z, false)
        }
    }

    private fun performRedo() {
        val ic = currentInputConnection ?: return
        val success = ic.performContextMenuAction(android.R.id.redo)
        if (!success) {
            sendCtrlKey(android.view.KeyEvent.KEYCODE_Z, true)
        }
    }

    private fun sendCtrlKey(keyCode: Int, shift: Boolean) {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val meta = android.view.KeyEvent.META_CTRL_ON or (if (shift) android.view.KeyEvent.META_SHIFT_ON else 0)
        ic.sendKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun commitClip(item: ClipItem) {
        // テキストの場合
        if (item.mimeType == "text/plain" || item.filePath == null) {
            currentInputConnection?.commitText(item.text.orEmpty(), 1)
            return
        }

        // 画像・ファイルの場合
        val file = java.io.File(item.filePath!!)
        if (!file.exists()) {
            android.widget.Toast.makeText(this, "ファイルが見つかりません", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData(
                item.label ?: item.fileName ?: "Clip",
                arrayOf(item.mimeType),
                android.content.ClipData.Item(contentUri)
            )
            cm.setPrimaryClip(clipData)

            if (item.isImage) {
                val editorInfo = currentInputEditorInfo
                val inputConnection = currentInputConnection
                if (editorInfo != null && inputConnection != null) {
                    val supportedMimes = androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(editorInfo)
                    val canCommit = supportedMimes.any { it.startsWith("image/") || it == item.mimeType }

                    if (canCommit) {
                        val description = android.content.ClipDescription(item.fileName ?: "image", arrayOf(item.mimeType))
                        val contentInfo = androidx.core.view.inputmethod.InputContentInfoCompat(
                            contentUri,
                            description,
                            null
                        )
                        androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                            inputConnection,
                            editorInfo,
                            contentInfo,
                            androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                            null
                        )
                        return
                    }
                }
            }

            android.widget.Toast.makeText(this, "クリップボードに復元しました(貼り付け可能です)", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "送信に失敗しました: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showQuickActionsOverlay(item: ClipItem) {
        overlayPreviewText.text = if (item.isImage) item.fileName ?: "画像" else item.text.orEmpty()
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var success = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            success = try { switchToPreviousInputMethod() } catch (_: Exception) { false }
            if (!success) {
                success = try { switchToNextInputMethod(false) } catch (_: Exception) { false }
            }
        } else {
            val token = window?.window?.attributes?.token
            if (token != null) {
                success = try {
                    imm.switchToNextInputMethod(token, false)
                } catch (_: Exception) { false }
            }
        }

        if (!success) {
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

        // キーボード起動時にOSクリップボードを即時同期（Gboardでコピーした内容も即反映）
        ClipboardWatcher.syncPrimaryClip(this)

        if (::adapter.isInitialized) {
            theme = ThemeConfig.load(this)
            adapter.updateThemeAndItems(theme, ClipStore.getAll(this))
        }
        refreshList()
    }
}
