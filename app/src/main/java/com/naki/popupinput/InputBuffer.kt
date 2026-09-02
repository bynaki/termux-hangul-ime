package com.naki.popupinput

import com.naki.popupinput.hangul.HangulAutomaton

/**
 * 팝업이 들고 있는 편집 버퍼.
 *
 * 확정된 텍스트([text])와 조합 중인 음절(오토마타 안)을 분리해서 관리한다.
 * 조합 중인 글자는 커서 자리에 끼워 넣어 보여주기만 하고, 전송 시점에 [take]로 한 번에 꺼낸다.
 */
class InputBuffer {

    private val text = StringBuilder()
    private val automaton = HangulAutomaton()
    private var cursor = 0

    /** 한글 조합 모드 여부. false면 입력된 키를 그대로 넣는다. */
    var koreanMode = true

    val isEmpty: Boolean get() = text.isEmpty() && automaton.isEmpty

    /** 조합 중인 글자까지 포함해 화면에 그릴 전체 텍스트. */
    val display: String get() = StringBuilder(text).insert(cursor, automaton.composing).toString()

    /** [display] 기준 커서 위치 (조합 중인 글자의 끝). */
    val displayCursor: Int get() = cursor + automaton.composing.length

    /** [display] 기준 조합 중인 구간. 밑줄로 강조한다. 조합 중이 아니면 빈 구간. */
    val composingRange: IntRange get() = cursor until (cursor + automaton.composing.length)

    fun typeJamo(jamo: Char) {
        insertRaw(automaton.input(jamo))
    }

    /** 자모가 아닌 문자(숫자·문장부호·공백·줄바꿈)를 넣는다. 조합 중이던 글자는 먼저 확정한다. */
    fun insert(s: String) {
        flushComposing()
        insertRaw(s)
    }

    fun backspace() {
        // 조합 중인 글자가 있으면 자모 단위로 역분해하고, 없으면 확정된 글자를 지운다
        if (automaton.backspace()) return
        if (cursor > 0) {
            text.deleteCharAt(cursor - 1)
            cursor--
        }
    }

    fun moveCursor(delta: Int) {
        flushComposing()
        cursor = (cursor + delta).coerceIn(0, text.length)
    }

    fun moveToStart() {
        flushComposing()
        cursor = 0
    }

    fun moveToEnd() {
        flushComposing()
        cursor = text.length
    }

    /** 조합을 끝내고 전체 텍스트를 꺼낸 뒤 버퍼를 비운다. */
    fun take(): String {
        flushComposing()
        val out = text.toString()
        clear()
        return out
    }

    fun clear() {
        text.setLength(0)
        automaton.flush()
        cursor = 0
    }

    /** 조합 중이던 글자를 확정해 버퍼에 넣는다. 커서 이동·모드 전환처럼 조합이 끊기는 시점에 호출한다. */
    fun flushComposing() {
        insertRaw(automaton.flush())
    }

    private fun insertRaw(s: String) {
        if (s.isEmpty()) return
        text.insert(cursor, s)
        cursor += s.length
    }
}
