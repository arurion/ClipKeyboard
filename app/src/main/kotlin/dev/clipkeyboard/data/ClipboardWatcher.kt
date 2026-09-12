package dev.clipkeyboard.data

import android.content.ClipboardManager
import android.content.Context

/**
 * システムのクリップボード変更を監視し、変更があれば ClipStore に自動保存する。
 * Application#onCreate と IME サービス#onCreate の両方から register() を呼んでおくことで、
 * 「アプリを開いていなくてもキーボードが有効な間はコピーが自動的に溜まっていく」挙動になる。
 * (ただし OS がプロセスを回収した場合は次回キーボード表示/アプリ起動時まで再開されない点はAndroidの制約)
 */
object ClipboardWatcher {
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            try {
                val clip = cm.primaryClip ?: return@addPrimaryClipChangedListener
                if (clip.itemCount == 0) return@addPrimaryClipChangedListener
                val text = clip.getItemAt(0).coerceToText(appContext)?.toString()
                if (!text.isNullOrBlank()) {
                    ClipStore.addOrTouch(appContext, text)
                }
            } catch (_: Exception) {
                // クリップボードアクセス制限(バックグラウンド制限やフォーカス無しでの読み取り不可)は無視する
            }
        }
        registered = true
    }
}
