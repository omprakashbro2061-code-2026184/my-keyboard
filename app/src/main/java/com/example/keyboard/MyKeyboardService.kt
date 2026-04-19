package com.example.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private var isCaps = false
    private var isSymbols = false
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            vibrator = null
        }
    }

    override fun onCreateInputView(): View {
        return try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
            keyboard = Keyboard(this, R.xml.keys_letters)
            keyboardView.keyboard = keyboard
            keyboardView.setOnKeyboardActionListener(this)
            keyboardView.isPreviewEnabled = false
            keyboardView
        } catch (e: Exception) {
            KeyboardView(this, null)
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            isSymbols = false
            isCaps = false
            keyboard = Keyboard(this, R.xml.keys_letters)
            keyboardView.keyboard = keyboard
            keyboardView.invalidateAllKeys()
        } catch (e: Exception) {}
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (e: Exception) {}
    }

    private fun isPasswordField(): Boolean {
        return try {
            val inputType = currentInputEditorInfo?.inputType ?: return false
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } catch (e: Exception) {
            false
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        try {
            val ic = currentInputConnection ?: return
            vibrate()

            when (primaryCode) {
                -5, Keyboard.KEYCODE_DELETE -> {
                    val selectedText = ic.getSelectedText(0)
                    if (selectedText.isNullOrEmpty()) {
                        ic.deleteSurroundingText(1, 0)
                    } else {
                        ic.commitText("", 1)
                    }
                }
                -1, Keyboard.KEYCODE_SHIFT -> {
                    isCaps = !isCaps
                    keyboard.isShifted = isCaps
                    keyboardView.invalidateAllKeys()
                }
                10 -> {
                    val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    if (action != null && action != EditorInfo.IME_ACTION_NONE) {
                        ic.performEditorAction(action)
                    } else {
                        ic.commitText("\n", 1)
                    }
                }
                32 -> {
                    ic.commitText(" ", 1)
                }
                100500 -> {
                    isSymbols = !isSymbols
                    keyboard = if (isSymbols) {
                        Keyboard(this, R.xml.keys_symbols)
                    } else {
                        Keyboard(this, R.xml.keys_letters)
                    }
                    keyboardView.keyboard = keyboard
                }
                else -> {
                    var code = primaryCode.toChar()
                    if (isCaps && !isPasswordField()) {
                        code = code.uppercaseChar()
                    }
                    ic.commitText(code.toString(), 1)
                    if (isCaps) {
                        isCaps = false
                        keyboard.isShifted = false
                        keyboardView.invalidateAllKeys()
                    }
                }
            }
        } catch (e: Exception) {}
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeDown() {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}
}
