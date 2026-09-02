package io.github.bynaki.moa

import android.content.Context
import android.view.KeyEvent

/** 설정값. 개수가 적어 SharedPreferences 하나로 충분하다. */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("moa", Context.MODE_PRIVATE)

    /** 팝업을 여는 키. 기본값은 안드로이드에서 IME 전환에 흔히 쓰이는 Shift+Space. */
    var hotkeyKeyCode: Int
        get() = prefs.getInt(KEY_HOTKEY_CODE, KeyEvent.KEYCODE_SPACE)
        set(value) = prefs.edit().putInt(KEY_HOTKEY_CODE, value).apply()

    /** 핫키에 필요한 수식키 ([Hotkey.SHIFT] 등의 비트합). */
    var hotkeyMeta: Int
        get() = prefs.getInt(KEY_HOTKEY_META, Hotkey.SHIFT)
        set(value) = prefs.edit().putInt(KEY_HOTKEY_META, value).apply()

    /** 전송 직후 Enter를 한 번 더 보내 명령을 바로 실행할지 여부. */
    var sendEnterAfterCommit: Boolean
        get() = prefs.getBoolean(KEY_SEND_ENTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SEND_ENTER, value).apply()

    val hotkeyLabel: String get() = Hotkey.describe(hotkeyKeyCode, hotkeyMeta)

    private companion object {
        const val KEY_HOTKEY_CODE = "hotkey_code"
        const val KEY_HOTKEY_META = "hotkey_meta"
        const val KEY_SEND_ENTER = "send_enter"
    }
}
