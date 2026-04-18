package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private var isCaps = false
    private var isSymbols = false

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboard = Keyboard(this, R.xml.keys_letters)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        return keyboardView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            -5, Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
            }
            -1, Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard.isShifted = isCaps
                keyboardView.invalidateAllKeys()
            }
            10 -> {
                ic.commitText("\n", 1)
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
                if (isCaps) code = code.uppercaseChar()
                ic.commitText(code.toString(), 1)
                if (isCaps) {
                    isCaps = false
                    keyboard.isShifted = false
                    keyboardView.invalidateAllKeys()
                }
            }
        }
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeDown() {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}
}
