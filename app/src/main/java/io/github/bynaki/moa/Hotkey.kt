package io.github.bynaki.moa

import android.view.KeyEvent

/**
 * 팝업을 여는 핫키.
 *
 * 기기·키보드마다 한/영 키가 보내는 키코드가 달라서 고정할 수 없다. 그래서 값 자체는 사용자가
 * [HotkeyCaptureActivity]에서 직접 눌러 등록하고, 여기서는 저장·비교·표기만 담당한다.
 */
object Hotkey {

    const val SHIFT = 1
    const val CTRL = 2
    const val ALT = 4

    /** 이벤트의 수식키 상태. 수식키 자체가 핫키인 경우 그 키가 눌려 생긴 비트는 뺀다. */
    fun metaFlags(event: KeyEvent): Int {
        var flags = 0
        if (event.isShiftPressed) flags = flags or SHIFT
        if (event.isCtrlPressed) flags = flags or CTRL
        if (event.isAltPressed) flags = flags or ALT
        return flags and selfFlag(event.keyCode).inv()
    }

    fun matches(event: KeyEvent, keyCode: Int, meta: Int): Boolean =
        event.keyCode == keyCode && metaFlags(event) == meta

    fun describe(keyCode: Int, meta: Int): String {
        val parts = mutableListOf<String>()
        if (meta and CTRL != 0) parts += "Ctrl"
        if (meta and ALT != 0) parts += "Alt"
        if (meta and SHIFT != 0) parts += "Shift"
        parts += keyName(keyCode)
        return parts.joinToString("+")
    }

    private fun keyName(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_ALT_RIGHT -> "오른쪽 Alt"
        KeyEvent.KEYCODE_ALT_LEFT -> "왼쪽 Alt"
        KeyEvent.KEYCODE_SHIFT_RIGHT -> "오른쪽 Shift"
        KeyEvent.KEYCODE_SHIFT_LEFT -> "왼쪽 Shift"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }

    private fun selfFlag(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> CTRL
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> ALT
        else -> 0
    }
}
