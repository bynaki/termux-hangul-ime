package io.github.bynaki.moa.hangul

/**
 * 두벌식 한글 조합 오토마타.
 *
 * 조합 중인 음절 하나만 상태로 들고 있다가, 조합이 끝나 확정된 텍스트는 [input]의 반환값으로 흘려보낸다.
 * 호출자는 반환된 문자열을 버퍼에 이어 붙이고, 아직 조합 중인 부분은 [composing]으로 따로 그린다.
 *
 * 안드로이드 의존성이 없는 순수 로직이라 JVM 유닛 테스트로 검증한다.
 */
class HangulAutomaton {

    private var cho = -1   // 초성 index, 없으면 -1
    private var jung = -1  // 중성 index, 없으면 -1
    private var jong = 0   // 종성 index, 없으면 0

    val isEmpty: Boolean get() = cho < 0 && jung < 0

    /** 아직 조합 중이라 확정되지 않은 부분. 터미널로 내보내지 않고 팝업에만 그린다. */
    val composing: String get() = render(cho, jung, jong)

    /** 자모 하나를 입력하고, 이번 입력으로 확정된 텍스트를 반환한다 (없으면 빈 문자열). */
    fun input(jamo: Char): String =
        if (JUNG.indexOf(jamo) >= 0) inputVowel(jamo) else inputConsonant(jamo)

    /**
     * 백스페이스를 자모 단위로 역분해한다.
     * @return 조합 중인 글자를 지웠으면 true, 조합 상태가 비어 있어 호출자가 버퍼에서 지워야 하면 false
     */
    fun backspace(): Boolean = when {
        jong != 0 -> {
            jong = JONG_SPLIT[JONG[jong]]?.let { JONG.indexOf(it.first) } ?: 0
            true
        }
        jung >= 0 -> {
            jung = VOWEL_SPLIT[JUNG[jung]]?.let { JUNG.indexOf(it.first) } ?: -1
            true
        }
        cho >= 0 -> {
            cho = -1
            true
        }
        else -> false
    }

    /** 조합을 강제로 끝내고 그때까지의 글자를 반환한다. 커서 이동·전송·모드 전환 시 호출한다. */
    fun flush(): String {
        val out = composing
        reset()
        return out
    }

    private fun inputConsonant(c: Char): String {
        val choIdx = CHO.indexOf(c)
        val jongIdx = JONG.indexOf(c)

        // 빈 상태 → 초성으로 시작
        if (cho < 0 && jung < 0) {
            if (choIdx >= 0) {
                cho = choIdx
                return ""
            }
            return c.toString()
        }
        // 초성만 있음 → 자음끼리는 못 붙으므로 확정하고 새 음절 시작
        if (jung < 0) return finishAndRestart(render(cho, -1, 0), c, choIdx)
        // 중성까지 있고 종성이 빔 → 종성으로 받을 수 있으면 받는다 (ㄸ·ㅃ·ㅉ은 종성 불가)
        if (jong == 0) {
            if (jongIdx > 0) {
                jong = jongIdx
                return ""
            }
            return finishAndRestart(render(cho, jung, 0), c, choIdx)
        }
        // 종성이 이미 있음 → 겹받침 시도
        val combined = JONG_COMBINE[JONG[jong] to c]
        if (combined != null) {
            jong = JONG.indexOf(combined)
            return ""
        }
        return finishAndRestart(render(cho, jung, jong), c, choIdx)
    }

    private fun inputVowel(v: Char): String {
        val jungIdx = JUNG.indexOf(v)

        // 종성이 있으면 연음: 종성(겹받침이면 뒤쪽 자음)을 다음 음절의 초성으로 넘긴다
        if (jong != 0) {
            val jongChar = JONG[jong]
            val split = JONG_SPLIT[jongChar]
            val moved: Char
            if (split != null) {
                jong = JONG.indexOf(split.first)
                moved = split.second
            } else {
                jong = 0
                moved = jongChar
            }
            val out = render(cho, jung, jong)
            reset()
            cho = CHO.indexOf(moved)
            jung = jungIdx
            return out
        }
        // 초성만 있음 → 중성을 붙여 음절 완성
        if (jung < 0) {
            jung = jungIdx
            return ""
        }
        // 중성이 이미 있음 → 겹모음 시도
        val combined = VOWEL_COMBINE[JUNG[jung] to v]
        if (combined != null) {
            jung = JUNG.indexOf(combined)
            return ""
        }
        // 겹모음이 안 되면 확정하고 초성 없는 모음으로 새로 시작 (가 + ㅏ → "가ㅏ")
        val out = render(cho, jung, 0)
        reset()
        jung = jungIdx
        return out
    }

    /** 현재 음절을 [finished]로 확정하고, [c]로 새 음절을 시작한다. */
    private fun finishAndRestart(finished: String, c: Char, choIdx: Int): String {
        reset()
        if (choIdx < 0) return finished + c
        cho = choIdx
        return finished
    }

    private fun reset() {
        cho = -1
        jung = -1
        jong = 0
    }

    private fun render(cho: Int, jung: Int, jong: Int): String = when {
        cho >= 0 && jung >= 0 -> (0xAC00 + (cho * 21 + jung) * 28 + jong).toChar().toString()
        cho >= 0 -> CHO[cho].toString()
        jung >= 0 -> JUNG[jung].toString()
        else -> ""
    }

    private companion object {
        val CHO = charArrayOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ',
            'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ',
            'ㅌ', 'ㅍ', 'ㅎ'
        )
        val JUNG = charArrayOf(
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ',
            'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ',
            'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
        )
        // index 0은 "종성 없음" 자리라 실제 자모와 겹치지 않는 문자를 채워 둔다
        val JONG = charArrayOf(
            '\u0000', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ',
            'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ',
            'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ',
            'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        val VOWEL_COMBINE = mapOf(
            ('ㅗ' to 'ㅏ') to 'ㅘ',  // ㅗ + ㅏ = ㅘ
            ('ㅗ' to 'ㅐ') to 'ㅙ',  // ㅗ + ㅐ = ㅙ
            ('ㅗ' to 'ㅣ') to 'ㅚ',  // ㅗ + ㅣ = ㅚ
            ('ㅜ' to 'ㅓ') to 'ㅝ',  // ㅜ + ㅓ = ㅝ
            ('ㅜ' to 'ㅔ') to 'ㅞ',  // ㅜ + ㅔ = ㅞ
            ('ㅜ' to 'ㅣ') to 'ㅟ',  // ㅜ + ㅣ = ㅟ
            ('ㅡ' to 'ㅣ') to 'ㅢ'   // ㅡ + ㅣ = ㅢ
        )
        val VOWEL_SPLIT = VOWEL_COMBINE.entries.associate { (parts, whole) -> whole to parts }

        val JONG_COMBINE = mapOf(
            ('ㄱ' to 'ㅅ') to 'ㄳ',  // ㄱ + ㅅ = ㄳ
            ('ㄴ' to 'ㅈ') to 'ㄵ',  // ㄴ + ㅈ = ㄵ
            ('ㄴ' to 'ㅎ') to 'ㄶ',  // ㄴ + ㅎ = ㄶ
            ('ㄹ' to 'ㄱ') to 'ㄺ',  // ㄹ + ㄱ = ㄺ
            ('ㄹ' to 'ㅁ') to 'ㄻ',  // ㄹ + ㅁ = ㄻ
            ('ㄹ' to 'ㅂ') to 'ㄼ',  // ㄹ + ㅂ = ㄼ
            ('ㄹ' to 'ㅅ') to 'ㄽ',  // ㄹ + ㅅ = ㄽ
            ('ㄹ' to 'ㅌ') to 'ㄾ',  // ㄹ + ㅌ = ㄾ
            ('ㄹ' to 'ㅍ') to 'ㄿ',  // ㄹ + ㅍ = ㄿ
            ('ㄹ' to 'ㅎ') to 'ㅀ',  // ㄹ + ㅎ = ㅀ
            ('ㅂ' to 'ㅅ') to 'ㅄ'   // ㅂ + ㅅ = ㅄ
        )
        val JONG_SPLIT = JONG_COMBINE.entries.associate { (parts, whole) -> whole to parts }
    }
}
