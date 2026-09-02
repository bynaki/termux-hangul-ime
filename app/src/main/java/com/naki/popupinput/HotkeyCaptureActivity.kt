package com.naki.popupinput

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView

/**
 * 핫키를 실제로 눌러서 등록하는 화면.
 *
 * 한/영 키가 보내는 키코드는 기기·키보드마다 다르고, 어떤 키는 시스템이 먼저 가져가 앱까지 오지도 않는다.
 * 그래서 후보를 목록으로 고르게 하지 않고, 눌린 키를 그대로 보여주며 등록한다.
 * 여기서 반응이 없는 키는 IME에서도 잡을 수 없는 키다.
 */
class HotkeyCaptureActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var captured: TextView

    /** 수식키를 누른 뒤 다른 키가 눌렸는지. Shift+Space를 등록할 때 Shift만 저장되는 것을 막는다. */
    private var otherKeyPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_hotkey)

        captured = findViewById(R.id.captured)
        captured.text = getString(R.string.hotkey_current, prefs.hotkeyLabel)

        findViewById<Button>(R.id.done).setOnClickListener { finish() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 뒤로가기는 화면을 빠져나가는 용도로 남겨 둔다
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)

        val isModifier = KeyEvent.isModifierKey(event.keyCode)
        when (event.action) {
            KeyEvent.ACTION_DOWN ->
                if (isModifier) otherKeyPressed = false else save(event)
            KeyEvent.ACTION_UP ->
                // 수식키 단독 누름(오른쪽 Alt 등)도 핫키로 쓸 수 있게 한다
                if (isModifier && !otherKeyPressed) save(event)
        }
        return true
    }

    private fun save(event: KeyEvent) {
        otherKeyPressed = true
        prefs.hotkeyKeyCode = event.keyCode
        prefs.hotkeyMeta = Hotkey.metaFlags(event)
        captured.text = getString(R.string.hotkey_saved, prefs.hotkeyLabel)
    }
}
