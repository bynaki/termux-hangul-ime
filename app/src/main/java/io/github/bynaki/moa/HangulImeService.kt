package io.github.bynaki.moa

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import io.github.bynaki.moa.hangul.DubeolsikKeyMap

/**
 * 물리 키보드용 한글 입력기.
 *
 * Termux의 TerminalView는 IME에 자신의 inputType을 `TYPE_NULL`로 보고한다. 그래서 한글 IME가
 * 조합을 시작하지 않고, Termux는 받은 키 이벤트를 `getUnicodeChar()`로 변환해 ASCII만 얻는다.
 * 반면 Termux의 InputConnection은 `commitText()`를 그대로 터미널로 흘려보내므로,
 * 조합을 우리가 직접 한 뒤 완성된 문자열만 커밋하면 한글이 들어간다.
 *
 * 그래서 이 서비스는 화면 자판을 그리지 않는다. 물리 키 이벤트를 받아 팝업 버퍼에 쌓다가
 * Enter를 누르면 [android.view.inputmethod.InputConnection.commitText]로 한 번에 내보낸다.
 */
class HangulImeService : InputMethodService() {

    private lateinit var prefs: Prefs
    private val buffer = InputBuffer()

    /** 팝업이 열려 있는 동안에만 키를 가로챈다. 닫혀 있으면 모든 키가 앱으로 그대로 흘러간다. */
    private var popupActive = false

    /** onKeyDown에서 소비한 키는 짝이 되는 onKeyUp도 삼켜야 앱에 반쪽짜리 이벤트가 새지 않는다. */
    private val consumedKeys = mutableSetOf<Int>()

    private var bufferView: TextView? = null
    private var hintView: TextView? = null
    private var popupLayer: View? = null
    private var noKeysLayer: View? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.popup_input, null)
        bufferView = root.findViewById(R.id.buffer)
        hintView = root.findViewById(R.id.hint)
        popupLayer = root.findViewById(R.id.popup_layer)
        noKeysLayer = root.findViewById(R.id.nokeys_layer)
        root.findViewById<Button>(R.id.switch_keyboard).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        updateLayers()
        render()
        return root
    }

    /**
     * 물리 키보드가 붙어 있으면 IME 창은 기본적으로 뜨지 않으므로, 팝업이 열린 동안만 강제로 띄운다.
     *
     * 반대로 물리 키보드가 없으면 이 입력기로는 한 글자도 칠 수 없다. 그때도 창을 띄워
     * [R.id.nokeys_layer]로 상황과 빠져나갈 방법을 보여준다. 아무것도 뜨지 않으면 사용자는
     * 입력기를 바꿀 방법조차 화면에서 찾지 못한다.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return popupActive || !hasHardwareKeyboard()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateLayers()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 키보드를 뽑은 순간 조합 중이던 팝업은 더 이상 이어갈 수 없다. 창은 그대로 두고
        // 안내 화면으로 바꾼다 (여기서 창까지 내리면 안내를 볼 기회가 사라진다).
        if (!hasHardwareKeyboard()) closePopup(hideWindow = false)
        updateLayers()
        updateInputViewShown()
    }

    private fun hasHardwareKeyboard(): Boolean =
        resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS

    private fun updateLayers() {
        val noKeys = !hasHardwareKeyboard()
        popupLayer?.visibility = if (noKeys) View.GONE else View.VISIBLE
        noKeysLayer?.visibility = if (noKeys) View.VISIBLE else View.GONE
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInput() {
        closePopup()
        super.onFinishInput()
    }

    // 사용자가 뒤로가기 등으로 IME 창을 직접 내렸을 때도 팝업 상태를 놓지 않으면
    // 창은 사라졌는데 키는 계속 가로채는 상태가 된다.
    override fun onWindowHidden() {
        closePopup(hideWindow = false)
        super.onWindowHidden()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.v(TAG, "onKeyDown code=$keyCode meta=${event.metaState} popup=$popupActive")

        if (!popupActive) {
            if (Hotkey.matches(event, prefs.hotkeyKeyCode, prefs.hotkeyMeta)) {
                openPopup()
                consumedKeys += keyCode
                return true
            }
            // 팝업이 닫혀 있을 때는 무엇도 가로채지 않는다 (기존 영문 타이핑·Ctrl+C·화살표 보존)
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode in PASS_THROUGH_KEYS) return super.onKeyDown(keyCode, event)

        val handled = handlePopupKey(keyCode, event)
        if (handled) consumedKeys += keyCode
        return handled
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (consumedKeys.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handlePopupKey(keyCode: Int, event: KeyEvent): Boolean {
        // 팝업 안에서 핫키를 다시 누르면 한/영을 토글한다
        if (Hotkey.matches(event, prefs.hotkeyKeyCode, prefs.hotkeyMeta)) {
            buffer.flushComposing()
            buffer.koreanMode = !buffer.koreanMode
            render()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                closePopup()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Shift+Enter는 줄바꿈, 그냥 Enter는 전송
                if (event.isShiftPressed) buffer.insert("\n") else commitAndClose()
                render()
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                buffer.backspace()
                render()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                buffer.moveCursor(-1)
                render()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                buffer.moveCursor(1)
                render()
                return true
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                buffer.moveToStart()
                render()
                return true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                buffer.moveToEnd()
                render()
                return true
            }
        }

        if (buffer.koreanMode) {
            val jamo = DubeolsikKeyMap.jamoForKeyCode(keyCode, event.isShiftPressed)
            if (jamo != null) {
                buffer.typeJamo(jamo)
                render()
                return true
            }
        }

        // 자모가 아닌 문자(숫자·문장부호·공백)는 그대로 넣는다
        val unicode = event.getUnicodeChar(event.metaState)
        if (unicode != 0 && !Character.isISOControl(unicode)) {
            buffer.insert(unicode.toChar().toString())
            render()
            return true
        }

        // 팝업이 열린 동안 나머지 키는 삼켜서 Termux로 새지 않게 한다
        return true
    }

    private fun openPopup() {
        popupActive = true
        buffer.clear()
        buffer.koreanMode = true
        updateInputViewShown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) requestShowSelf(0)
        render()
    }

    /**
     * 팝업을 닫고 IME 창까지 내린다.
     *
     * [updateInputViewShown]만으로는 [onEvaluateInputViewShown]이 false가 되어 입력 뷰만 빠지고,
     * [requestShowSelf]로 띄운 창 자체는 남아 화면을 가릴 수 있다. 그래서 전송·취소 뒤에는
     * [requestHideSelf]로 창을 명시적으로 내린다. (이미 창이 내려가서 호출된 경우엔 생략)
     */
    private fun closePopup(hideWindow: Boolean = true) {
        if (!popupActive) return
        popupActive = false
        buffer.clear()
        consumedKeys.clear()
        updateInputViewShown()
        if (hideWindow) requestHideSelf(0)
    }

    private fun commitAndClose() {
        val text = buffer.take()
        val ic = currentInputConnection
        if (text.isNotEmpty() && ic != null) {
            ic.beginBatchEdit()
            ic.commitText(text, 1)
            ic.endBatchEdit()
            if (prefs.sendEnterAfterCommit) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }
        closePopup()
    }

    private fun render() {
        val target = bufferView ?: return
        val display = buffer.display
        val spanned = SpannableStringBuilder(display)

        // 조합 중인 글자는 밑줄로 표시한다
        val composing = buffer.composingRange
        if (!composing.isEmpty()) {
            spanned.setSpan(
                UnderlineSpan(), composing.first, composing.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 커서는 터미널처럼 블록으로 그린다. 끝에 있으면 칸을 하나 만들어 칠한다.
        val cursor = buffer.displayCursor
        if (cursor >= spanned.length) spanned.append(' ')
        spanned.setSpan(
            BackgroundColorSpan(CURSOR_COLOR), cursor, cursor + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        target.text = spanned
        hintView?.text = getString(
            if (buffer.koreanMode) R.string.hint_korean else R.string.hint_latin,
            prefs.hotkeyLabel
        )
    }

    private companion object {
        const val TAG = "HangulIme"
        const val CURSOR_COLOR = 0xFFC5107A.toInt()

        /** 팝업이 열려 있어도 가로채면 안 되는 시스템 키. */
        val PASS_THROUGH_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH
        )
    }
}
