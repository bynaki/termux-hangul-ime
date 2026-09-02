package com.naki.popupinput.hangul

import android.view.KeyEvent

/**
 * 두벌식 자판 매핑.
 *
 * 물리 키보드의 각인이나 시스템 레이아웃과 무관하게 동작해야 하므로
 * `getUnicodeChar()`가 아니라 **키의 물리적 위치**([KeyEvent.KEYCODE_A] ~ [KeyEvent.KEYCODE_Z])로 매핑한다.
 */
object DubeolsikKeyMap {

    /** QWERTY 위치 기준 두벌식 자모. */
    private val UNSHIFTED = mapOf(
        'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ',
        'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
        'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ',
        'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
        'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ',
        'n' to 'ㅜ', 'm' to 'ㅡ'
    )

    /** Shift로 달라지는 자리만. 나머지는 [UNSHIFTED]와 같다. */
    private val SHIFTED = mapOf(
        'q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ',
        'o' to 'ㅒ', 'p' to 'ㅖ'
    )

    /** 알파벳 키에 대응하는 자모. 두벌식에 없는 키면 null. */
    fun jamoFor(letter: Char, shift: Boolean): Char? {
        val lower = letter.lowercaseChar()
        return if (shift) SHIFTED[lower] ?: UNSHIFTED[lower] else UNSHIFTED[lower]
    }

    /** 안드로이드 키코드에 대응하는 자모. 알파벳 키가 아니거나 두벌식에 없으면 null. */
    fun jamoForKeyCode(keyCode: Int, shift: Boolean): Char? {
        if (keyCode < KeyEvent.KEYCODE_A || keyCode > KeyEvent.KEYCODE_Z) return null
        val letter = 'a' + (keyCode - KeyEvent.KEYCODE_A)
        return jamoFor(letter, shift)
    }
}
