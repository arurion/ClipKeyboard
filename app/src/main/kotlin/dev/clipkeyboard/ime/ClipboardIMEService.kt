package dev.clipkeyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private lateinit var searchBox: EditText
    private lateinit var countText: TextView
    private lateinit var emptyText: TextView
    private var lastQuery: String = ""

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

    // 横向き表示時などに入力欄がキーボードの「フルスクリーン抽出モード」に
    // 覆われてしまわないようにする。多くのサードパーティ製IMEで採用されている挙動。
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        theme = ThemeConfig.load(this)
        val root = LayoutInflater.from(this).inflate(R.layout.ime_keyboard_view, null) as LinearLayout

        val header = root.findViewById<LinearLayout>(R.id.ime_header)
        val headerShadow = root.findViewById<View>(R.id.header_shadow)
        val switchBtn = root.findViewById<ImageButton>(R.id.btn_switch_ime)
        val addBtn = root.findViewById<ImageButton>(R.id.btn_quick_add)
        val closeBtn = root.findViewById<ImageButton>(R.id.btn_close_keyboard)
        searchBox = root.findViewById(R.id.edit_search)
        countText = root.findViewById(R.id.text_count)
        emptyText = root.findViewById(R.id.text_empty)
        recycler = root.findViewById(R.id.recycler_clips)

        // --- テーマ適用 ---------------------------------------------------
        root.setBackgroundColor(theme.backgroundColor)
        header.setBackgroundColor(ThemeUtils.headerBackgroundColor(theme))
        headerShadow.background = ThemeUtils.headerShadowDrawable()
        searchBox.setTextColor(theme.textColor)
        searchBox.setHintTextColor(theme.subTextColor)
        countText.setTextColor(theme.subTextColor)
        emptyText.setTextColor(theme.subTextColor)
        tintIcon(switchBtn, theme.accentColor)
        tintIcon(addBtn, theme.subTextColor)
        tintIcon(closeBtn, theme.subTextColor)

        // --- ヘッダー操作 ---------------------------------------------------
        switchBtn.setOnClickListener { handleSwitchToPreviousIme() }
        addBtn.setOnClickListener { openCreateInHostApp() }
        closeBtn.setOnClickListener { requestHideSelf(0) }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                lastQuery = s?.toString().orEmpty()
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // --- 一覧 -------------------------------------------------------
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ClipAdapter(
            items = emptyList(),
            theme = theme,
            onTap = { item -> commitClip(item) },
            onLongPress = { item -> showQuickActions(item) }
        )
        recycler.adapter = adapter

        refreshList()
        return root
    }

    private fun tintIcon(view: ImageView, color: Int) {
        view.setColorFilter(color)
    }

    private fun refreshList() {
        if (!::adapter.isInitialized) return
        val all = ClipStore.getAll(this)
        val filtered = if (lastQuery.isBlank()) all else all.filter {
            it.text.contains(lastQuery, ignoreCase = true) || it.label?.contains(lastQuery, ignoreCase = true) == true
        }
        adapter.submit(filtered)
        countText.text = "${filtered.size}件"
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun commitClip(item: ClipItem) {
        currentInputConnection?.commitText(item.text, 1)
    }

    /**
     * 長押しで現れるクイックメニュー(ボトムシート)。
     * 行の表面から「削除」を隠蔽し、誤操作を防止する(身体的直感の原則)。
     */
    private fun showQuickActions(item: ClipItem) {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_ClipKeyboard)
        val dialog = BottomSheetDialog(themedContext)
        val sheet = LayoutInflater.from(themedContext).inflate(R.layout.bottom_sheet_clip_actions, null)

        sheet.findViewById<TextView>(R.id.sheet_preview_text).apply {
            text = item.text
            setTextColor(theme.subTextColor)
        }

        val pinRow = sheet.findViewById<LinearLayout>(R.id.action_toggle_pin)
        val pinLabel = sheet.findViewById<TextView>(R.id.action_toggle_pin_label)
        pinLabel.text = if (item.pinned) "ピン留めを解除する" else "ピン留めする"
        pinRow.setOnClickListener {
            ClipStore.togglePin(this, item.id)
            dialog.dismiss()
        }

        sheet.findViewById<LinearLayout>(R.id.action_edit).setOnClickListener {
            dialog.dismiss()
            openEditInHostApp(item)
        }

        val deleteRow = sheet.findViewById<LinearLayout>(R.id.action_delete)
        sheet.findViewById<TextView>(R.id.action_delete_label).setTextColor(theme.dangerColor)
        sheet.findViewById<ImageView>(R.id.action_delete_icon).setColorFilter(theme.dangerColor)
        deleteRow.setOnClickListener {
            ClipStore.delete(this, item.id)
            dialog.dismiss()
        }

        dialog.setContentView(sheet)
        dialog.show()
    }

    /**
     * 直前のIME(Gboard/Simeji等)への復帰アクション。
     * APIレベルに応じたフォールバックチェーンで、確実にどこかへ切り替わることを保証する。
     */
    private fun handleSwitchToPreviousIme() {
        var success = false

        // 1. Android 9 (API 28) 以上: OS推奨の直前IMEスイッチ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            success = try { switchToPreviousInputMethod() } catch (_: Exception) { false }
        }

        // 2. それ以外、または失敗時: 次の有効なIMEへのスイッチ
        if (!success) {
            success = try { switchToNextInputMethod(false) } catch (_: Exception) { false }
        }

        // 3. スイッチ不可(登録IMEが1つのみ等): OS標準のピッカーを明示的に開く
        if (!success) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    /** IMEサービスからは複雑な編集UIを直接出さず、ホストアプリのActivityに委譲する。 */
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
            // EXTRA_CLIP_ID を指定しない = 新規作成モード
        }
        startActivity(intent)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshList()
    }
}
