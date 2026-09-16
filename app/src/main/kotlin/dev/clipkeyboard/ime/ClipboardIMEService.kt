package dev.clipkeyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
    private lateinit var rootLayout: FrameLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ClipAdapter
    private lateinit var countText: TextView
    private lateinit var emptyText: TextView
    private lateinit var btnToggleFloating: ImageButton
    private lateinit var quickActionsOverlay: LinearLayout
    private lateinit var overlayPreviewText: TextView
    private lateinit var overlayPinLabel: TextView
    private lateinit var overlayPinIcon: ImageView
    private lateinit var overlayEditIcon: ImageView
    private lateinit var overlayDeleteIcon: ImageView

    private var isFloating = false
    private var floatingX = 100
    private var floatingY = 300
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialX = 0
    private var initialY = 0

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

        rootLayout = LayoutInflater.from(themedContext).inflate(R.layout.ime_keyboard_view, null) as FrameLayout
        contentContainer = rootLayout.findViewById(R.id.ime_content_container)

        val header = rootLayout.findViewById<LinearLayout>(R.id.ime_header)
        val headerShadow = rootLayout.findViewById<View>(R.id.header_shadow)
        val switchBtn = rootLayout.findViewById<ImageButton>(R.id.btn_switch_ime)
        val titleText = rootLayout.findViewById<TextView>(R.id.text_ime_title)
        btnToggleFloating = rootLayout.findViewById(R.id.btn_toggle_floating)
        val addBtn = rootLayout.findViewById<ImageButton>(R.id.btn_quick_add)
        val closeBtn = rootLayout.findViewById<ImageButton>(R.id.btn_close_keyboard)
        countText = rootLayout.findViewById(R.id.text_count)
        emptyText = rootLayout.findViewById(R.id.text_empty)
        recycler = rootLayout.findViewById(R.id.recycler_clips)

        val editBar = rootLayout.findViewById<LinearLayout>(R.id.ime_edit_bar)
        val btnUndo = rootLayout.findViewById<ImageButton>(R.id.btn_undo)
        val btnRedo = rootLayout.findViewById<ImageButton>(R.id.btn_redo)
        val btnLeft = rootLayout.findViewById<ImageButton>(R.id.btn_dpad_left)
        val btnUp = rootLayout.findViewById<ImageButton>(R.id.btn_dpad_up)
        val btnDown = rootLayout.findViewById<ImageButton>(R.id.btn_dpad_down)
        val btnRight = rootLayout.findViewById<ImageButton>(R.id.btn_dpad_right)
        val btnBackspace = rootLayout.findViewById<ImageButton>(R.id.btn_backspace)
        val btnEnter = rootLayout.findViewById<ImageButton>(R.id.btn_enter)

        quickActionsOverlay = rootLayout.findViewById(R.id.overlay_quick_actions)
        overlayPreviewText = rootLayout.findViewById(R.id.overlay_preview_text)
        overlayPinLabel = rootLayout.findViewById(R.id.action_pin_label)
        overlayPinIcon = rootLayout.findViewById(R.id.action_pin_icon)
        overlayEditIcon = rootLayout.findViewById(R.id.action_edit_icon)
        overlayDeleteIcon = rootLayout.findViewById(R.id.action_delete_icon)

        contentContainer.setBackgroundColor(theme.backgroundColor)
        header.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))
        headerShadow.background = ThemeUtils.headerShadowDrawable()
        editBar.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))

        titleText.setTextColor(theme.textColor)
        countText.setTextColor(theme.subTextColor)
        emptyText.setTextColor(theme.subTextColor)

        switchBtn.setColorFilter(theme.accentColor)
        btnToggleFloating.setColorFilter(theme.subTextColor)
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

        btnToggleFloating.setOnClickListener {
            isFloating = !isFloating
            applyWindowMode()
        }

        header.setOnTouchListener { _, event ->
            if (!isFloating) return@setOnTouchListener false
            val w = window?.window ?: return@setOnTouchListener false
            val lp = w.attributes

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    initialX = lp.x
                    initialY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = initialX + (event.rawX - dragStartX).toInt()
                    lp.y = initialY + (event.rawY - dragStartY).toInt()
                    floatingX = lp.x
                    floatingY = lp.y
                    w.attributes = lp
                    true
                }
                else -> false
            }
        }

        btnUndo.setOnClickListener { triggerEditAction { performUndo() } }
        btnRedo.setOnClickListener { triggerEditAction { performRedo() } }
        btnLeft.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) } }
        btnUp.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP) } }
        btnDown.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN) } }
        btnRight.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) } }
        btnBackspace.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) } }
        btnEnter.setOnClickListener { triggerEditAction { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) } }

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

        applyWindowMode()
        refreshList()
        return rootLayout
    }

    /** 通常ドックモードとフローティングパレットモードを切り替える */
    private fun applyWindowMode() {
        val w = window?.window ?: return
        val lp = w.attributes

        if (isFloating) {
            btnToggleFloating.setImageResource(R.drawable.ic_dock_bottom_24)
            btnToggleFloating.contentDescription = "ドックに戻す"

            w.setGravity(Gravity.TOP or Gravity.START)
            lp.width = ThemeUtils.dp(this, 300f)
            lp.height = ThemeUtils.dp(this, 270f)
            lp.x = floatingX
            lp.y = floatingY

            contentContainer.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        } else {
            btnToggleFloating.setImageResource(R.drawable.ic_floating_24)
            btnToggleFloating.contentDescription = "フローティング表示"

            w.setGravity(Gravity.BOTTOM)
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.x = 0
            lp.y = 0

            contentContainer.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ThemeUtils.dp(this, theme.keyboardHeightDp)
            )
        }
        w.attributes = lp
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

    /**
     * ブラウザやWebView（Chromium）でも確実に動作する物理キーボード互換のUndo/Redo
     */
    private fun performUndo() {
        val ic = currentInputConnection ?: return
        val handled = ic.performContextMenuAction(android.R.id.undo)
        if (!handled) {
            sendCtrlKeySequence(KeyEvent.KEYCODE_Z, false)
        }
    }

    private fun performRedo() {
        val ic = currentInputConnection ?: return
        val handled = ic.performContextMenuAction(android.R.id.redo)
        if (!handled) {
            sendCtrlKeySequence(KeyEvent.KEYCODE_Z, true)
        }
    }

    private fun sendCtrlKeySequence(keyCode: Int, shift: Boolean) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()

        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
        if (shift) {
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))
        }

        val meta = KeyEvent.META_CTRL_ON or (if (shift) KeyEvent.META_SHIFT_ON else 0)
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))

        if (shift) {
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, KeyEvent.META_CTRL_ON))
        }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0))
    }

    private fun commitClip(item: ClipItem) {
        if (item.mimeType == "text/plain" || item.filePath == null) {
            currentInputConnection?.commitText(item.text.orEmpty(), 1)
            return
        }

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

        ClipboardWatcher.syncPrimaryClip(this)

        if (::adapter.isInitialized) {
            theme = ThemeConfig.load(this)
            adapter.updateThemeAndItems(theme, ClipStore.getAll(this))
            applyWindowMode()
        }
        refreshList()
    }
}
