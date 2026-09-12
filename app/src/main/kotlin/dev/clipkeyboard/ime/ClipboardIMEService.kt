package dev.clipkeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.data.ClipboardWatcher
import dev.clipkeyboard.theme.ThemeConfig
import dev.clipkeyboard.theme.ThemeUtils
import dev.clipkeyboard.ui.EditClipActivity
import dev.clipkeyboard.ui.MainActivity

class ClipboardIMEService : InputMethodService() {

    private lateinit var theme: ThemeConfig
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ClipAdapter
    private lateinit var searchBox: EditText
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
        val ctx = this
        val pad = ThemeUtils.dp(ctx, 8f)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ThemeUtils.dp(ctx, 260f))
            setBackgroundColor(theme.backgroundColor)
            setPadding(pad, pad, pad, pad)
        }

        // --- ツールバー -------------------------------------------------
        val toolbar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchBox = EditText(ctx).apply {
            hint = "検索"
            setHintTextColor(theme.subTextColor)
            setTextColor(theme.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            ThemeUtils.applyKeyBackground(this, theme, theme.rowBackgroundColor)
            setPadding(pad, pad / 2, pad, pad / 2)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    lastQuery = s?.toString().orEmpty()
                    refreshList()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        val addBtn = toolbarButton(ctx, "＋") { addCurrentClipboardManually() }
        val importBtn = toolbarButton(ctx, "取込") { openImportInHostApp() }
        val settingsBtn = toolbarButton(ctx, "⚙") { openSettingsInHostApp() }
        val switchImeBtn = toolbarButton(ctx, "⌨") { switchToNextInputMethod(false) }

        toolbar.addView(searchBox)
        toolbar.addView(addBtn)
        toolbar.addView(importBtn)
        toolbar.addView(settingsBtn)
        toolbar.addView(switchImeBtn)

        // --- 一覧 -------------------------------------------------------
        recycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addItemDecoration(DividerItemDecoration(ctx, DividerItemDecoration.VERTICAL))
        }
        adapter = ClipAdapter(
            items = emptyList(),
            theme = theme,
            onTap = { item -> commitClip(item) },
            onLongPress = { item -> openEditInHostApp(item) },
            onPinToggle = { item -> ClipStore.togglePin(this, item.id) },
            onDelete = { item -> ClipStore.delete(this, item.id) }
        )
        recycler.adapter = adapter

        val emptyLabel = TextView(ctx).apply {
            text = "コピーした内容がここに並びます。\n右上の「取込」からファイルをインポートすることもできます。"
            setTextColor(theme.subTextColor)
            gravity = Gravity.CENTER
            setPadding(pad, pad * 2, pad, pad * 2)
        }

        root.addView(toolbar)
        root.addView(recycler)
        root.addView(emptyLabel)

        refreshList()
        recycler.post {
            emptyLabel.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        }
        return root
    }

    private fun toolbarButton(ctx: Context, label: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            setTextColor(theme.accentColor)
            gravity = Gravity.CENTER
            val padH = ThemeUtils.dp(ctx, 10f)
            setPadding(padH, padH / 2, padH, padH / 2)
            setOnClickListener { onClick() }
        }

    private fun refreshList() {
        if (!::adapter.isInitialized) return
        val all = ClipStore.getAll(this)
        val filtered = if (lastQuery.isBlank()) all else all.filter {
            it.text.contains(lastQuery, ignoreCase = true) || it.label?.contains(lastQuery, ignoreCase = true) == true
        }
        adapter.submit(filtered)
    }

    private fun commitClip(item: ClipItem) {
        currentInputConnection?.commitText(item.text, 1)
    }

    private fun addCurrentClipboardManually() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrBlank()) ClipStore.addOrTouch(this, text)
    }

    /** IMEサービスからは編集用の複雑なUIを直接出さず、ホストアプリのActivityに委譲する。 */
    private fun openEditInHostApp(item: ClipItem) {
        val intent = Intent(this, EditClipActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EditClipActivity.EXTRA_CLIP_ID, item.id)
        }
        startActivity(intent)
    }

    private fun openImportInHostApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_IMPORT)
        }
        startActivity(intent)
    }

    private fun openSettingsInHostApp() {
        val intent = Intent(this, dev.clipkeyboard.ui.ThemeSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshList()
    }
}
