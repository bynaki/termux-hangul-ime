package io.github.bynaki.moa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView

/** 첫 실행 안내: 키보드 활성화 → 선택 → 핫키 등록 순서. */
class SetupActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var hotkeyValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_setup)

        status = findViewById(R.id.status)
        hotkeyValue = findViewById(R.id.hotkey_value)

        findViewById<Button>(R.id.enable_keyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.pick_keyboard).setOnClickListener {
            inputMethodManager.showInputMethodPicker()
        }
        findViewById<Button>(R.id.change_hotkey).setOnClickListener {
            startActivity(Intent(this, HotkeyCaptureActivity::class.java))
        }

        findViewById<CheckBox>(R.id.send_enter).apply {
            isChecked = prefs.sendEnterAfterCommit
            setOnCheckedChangeListener { _, checked -> prefs.sendEnterAfterCommit = checked }
        }
    }

    override fun onResume() {
        super.onResume()
        hotkeyValue.text = prefs.hotkeyLabel
        status.setText(
            when {
                isSelected -> R.string.status_selected
                isEnabled -> R.string.status_enabled
                else -> R.string.status_disabled
            }
        )
    }

    private val inputMethodManager: InputMethodManager
        get() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    /** 설정에서 이 키보드를 켰는지. */
    private val isEnabled: Boolean
        get() = inputMethodManager.enabledInputMethodList.any { it.packageName == packageName }

    /** 지금 실제로 쓰이는 키보드가 이 앱인지. */
    private val isSelected: Boolean
        get() = Settings.Secure
            .getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
}
