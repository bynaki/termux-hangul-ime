package io.github.bynaki.moa.hangul

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 오토마타 검증. 입력은 QWERTY 키를 그대로 적고(두벌식 자판 위치), 대문자는 Shift를 뜻한다.
 */
class HangulAutomatonTest {

    /** [keys]를 순서대로 입력한 뒤, 확정된 텍스트 + 아직 조합 중인 글자를 합쳐 돌려준다. */
    private fun type(keys: String): String {
        val automaton = HangulAutomaton()
        val out = StringBuilder()
        for (key in keys) {
            val jamo = DubeolsikKeyMap.jamoFor(key, shift = key.isUpperCase())
                ?: error("두벌식에 없는 키: $key")
            out.append(automaton.input(jamo))
        }
        return out.toString() + automaton.composing
    }

    @Test
    fun `기본 음절을 조합한다`() {
        assertEquals("안녕하세요", type("dkssudgktpdy"))
        assertEquals("한글", type("gksrmf"))
    }

    @Test
    fun `겹모음을 조합한다`() {
        assertEquals("왜", type("dho"))
        assertEquals("의", type("dml"))
        assertEquals("워", type("dnj"))
        assertEquals("최", type("chl"))
    }

    @Test
    fun `겹받침을 조합한다`() {
        assertEquals("값", type("rkqt"))
        assertEquals("몫", type("ahrt"))
        assertEquals("닭", type("ekfr"))
        assertEquals("삶", type("tkfa"))
    }

    @Test
    fun `쌍자음은 Shift로 입력한다`() {
        assertEquals("꽃", type("Rhc"))
        assertEquals("따", type("Ek"))
        assertEquals("빵", type("Qkd"))
    }

    @Test
    fun `종성 뒤에 모음이 오면 연음된다`() {
        assertEquals("하나", type("gksk"))
        assertEquals("먹어", type("ajrdj"))
    }

    @Test
    fun `겹받침 뒤에 모음이 오면 뒤쪽 자음만 넘어간다`() {
        assertEquals("갑시", type("rkqtl"))
        assertEquals("달기", type("ekfrl"))
    }

    @Test
    fun `겹받침 뒤에 자음이 오면 그대로 확정된다`() {
        assertEquals("없다", type("djqtek"))
        assertEquals("앉자", type("dkswwk"))
    }

    @Test
    fun `조합할 수 없는 자모는 각각 확정된다`() {
        // 자음 두 개는 겹치지 않는다
        assertEquals("ㄱㄱ", type("rr"))
        // 겹모음이 아닌 모음이 이어지면 초성 없는 모음으로 새로 시작한다
        assertEquals("가ㅏ", type("rkk"))
        // ㄸ·ㅃ·ㅉ은 종성이 될 수 없다
        assertEquals("가ㄸ", type("rkE"))
    }

    @Test
    fun `백스페이스는 자모 단위로 역분해한다`() {
        val automaton = HangulAutomaton()
        for (key in "rkqt") {
            automaton.input(DubeolsikKeyMap.jamoFor(key, shift = false)!!)
        }
        assertEquals("값", automaton.composing)

        assertEquals(true, automaton.backspace())
        assertEquals("갑", automaton.composing)
        assertEquals(true, automaton.backspace())
        assertEquals("가", automaton.composing)
        assertEquals(true, automaton.backspace())
        assertEquals("ㄱ", automaton.composing)
        assertEquals(true, automaton.backspace())
        assertEquals("", automaton.composing)

        // 조합 상태가 비면 호출자가 버퍼에서 지우도록 false를 돌려준다
        assertEquals(false, automaton.backspace())
    }

    @Test
    fun `백스페이스는 겹모음도 역분해한다`() {
        val automaton = HangulAutomaton()
        for (key in "dhk") {
            automaton.input(DubeolsikKeyMap.jamoFor(key, shift = false)!!)
        }
        assertEquals("와", automaton.composing)
        automaton.backspace()
        assertEquals("오", automaton.composing)
    }

    @Test
    fun `flush는 조합을 끝내고 상태를 비운다`() {
        val automaton = HangulAutomaton()
        for (key in "rk") {
            automaton.input(DubeolsikKeyMap.jamoFor(key, shift = false)!!)
        }
        assertEquals("가", automaton.flush())
        assertEquals("", automaton.composing)
        assertEquals(true, automaton.isEmpty)
    }
}
