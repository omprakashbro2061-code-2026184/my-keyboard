package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var lettersKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private var isCaps = false
    private var isSymbols = false

    companion object {
        const val CODE_SPACE = 32
        const val CODE_TOGGLE = 100500
        const val CODE_SYMBOLS2 = 100501
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        lettersKeyboard = Keyboard(this, R.xml.keys_letters)
        symbolsKeyboard = Keyboard(this, R.xml.keys_symbols)
        keyboardView.keyboard = lettersKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        return keyboardView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {

            Keyboard.KEYCODE_DELETE -> {
                val selectedText = ic.getSelectedText(0)
                if (selectedText.isNullOrEmpty()) {
                    ic.deleteSurroundingText(1, 0)
                } else {
                    ic.commitText("", 1)
                }
            }

            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                lettersKeyboard.isShifted = isCaps
                keyboardView.invalidateAllKeys()
            }

            Keyboard.KEYCODE_DONE -> {
                ic.commitText("\n", 1)
            }

            CODE_SPACE -> {
                ic.commitText(" ", 1)
            }

            CODE_TOGGLE -> {
                isSymbols = true
                keyboardView.keyboard = symbolsKeyboard
            }

            CODE_SYMBOLS2 -> {
                isSymbols = false
                keyboardView.keyboard = lettersKeyboard
                isCaps = false
                lettersKeyboard.isShifted = false
                keyboardView.invalidateAllKeys()
            }

            else -> {
                var char = primaryCode.toChar()
                if (!isSymbols && isCaps) {
                    char = char.uppercaseChar()
                }
                ic.commitText(char.toString(), 1)
                if (!isSymbols && isCaps) {
                    isCaps = false
                    lettersKeyboard.isShifted = false
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
