package com.example.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private var isCaps = false
    private var isCapsLock = false
    private var isSymbols = false
    private var isEmoji = false
    private var isKaomoji = false
    private var vibrator: Vibrator? = null
    private var lastShiftTime = 0L
    private var lastSpaceTime = 0L
    private val deleteHandler = Handler(Looper.getMainLooper())

    private val deleteRunnable = object : Runnable {
        override fun run() {
            try {
                currentInputConnection?.deleteSurroundingText(1, 0)
                deleteHandler.postDelayed(this, 50)
            } catch (e: Exception) {}
        }
    }

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
        } catch (e: Exception) { vibrator = null }
    }

    override fun onCreateInputView(): View {
        return try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
            keyboard = Keyboard(this, R.xml.keys_letters)
            keyboardView.keyboard = keyboard
            keyboardView.setOnKeyboardActionListener(this)
            keyboardView.isPreviewEnabled = false
            keyboardView
        } catch (e: Exception) { KeyboardView(this, null) }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            isSymbols = false
            isEmoji = false
            isKaomoji = false
            isCaps = false
            isCapsLock = false
            keyboard = Keyboard(this, R.xml.keys_letters)
            keyboardView.keyboard = keyboard
            keyboardView.invalidateAllKeys()
        } catch (e: Exception) {}
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (e: Exception) {}
    }

    private fun autoCapitalize() {
        try {
            val ic = currentInputConnection ?: return
            val text = ic.getTextBeforeCursor(2, 0) ?: return
            if (text.isEmpty()) {
                isCaps = true
                keyboard.isShifted = true
                keyboardView.invalidateAllKeys()
                return
            }
            if (text.last() in listOf('.', '!', '?')) {
                isCaps = true
                keyboard.isShifted = true
                keyboardView.invalidateAllKeys()
            }
        } catch (e: Exception) {}
    }

    private fun hasClipboard(): Boolean {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.hasPrimaryClip() && cm.primaryClip!!.itemCount > 0
        } catch (e: Exception) { false }
    }

    private fun pasteFromClipboard() {
        try {
            if (!hasClipboard()) return
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this) ?: return
            currentInputConnection?.commitText(text, 1)
            vibrate()
        } catch (e: Exception) {}
    }

    override fun onText(text: CharSequence?) {
        try {
            val ic = currentInputConnection ?: return
            if (!text.isNullOrEmpty()) {
                ic.commitText(text, 1)
                vibrate()
            }
        } catch (e: Exception) {}
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        try {
            val ic = currentInputConnection ?: return
            vibrate()

            when (primaryCode) {

                -10 -> {
                    // handled by onText via keyOutputText
                }

                -5, Keyboard.KEYCODE_DELETE -> {
                    val selected = ic.getSelectedText(0)
                    if (!selected.isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                }

                -1, Keyboard.KEYCODE_SHIFT -> {
                    val now = System.currentTimeMillis()
                    if (now - lastShiftTime < 300) {
                        isCapsLock = !isCapsLock
                        isCaps = isCapsLock
                    } else {
                        if (!isCapsLock) isCaps = !isCaps
                    }
                    lastShiftTime = now
                    keyboard.isShifted = isCaps
                    keyboardView.invalidateAllKeys()
                }

                10 -> {
                    val action = currentInputEditorInfo
                        ?.imeOptions
                        ?.and(EditorInfo.IME_MASK_ACTION)
                    if (action != null
                        && action != EditorInfo.IME_ACTION_NONE
                        && action != EditorInfo.IME_ACTION_UNSPECIFIED
                    ) {
                        ic.performEditorAction(action)
                    } else {
                        ic.commitText("\n", 1)
                    }
                }

                32 -> {
                    val now = System.currentTimeMillis()
                    if (now - lastSpaceTime < 300) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                        isCaps = true
                        keyboard.isShifted = true
                        keyboardView.invalidateAllKeys()
                    } else {
                        ic.commitText(" ", 1)
                        autoCapitalize()
                    }
                    lastSpaceTime = now
                }

                100500 -> {
                    isSymbols = !isSymbols
                    isEmoji = false
                    isKaomoji = false
                    keyboard = if (isSymbols) {
                        Keyboard(this, R.xml.keys_symbols)
                    } else {
                        Keyboard(this, R.xml.keys_letters)
                    }
                    keyboardView.keyboard = keyboard
                }

                100600 -> {
                    isEmoji = !isEmoji
                    isSymbols = false
                    isKaomoji = false
                    keyboard = if (isEmoji) {
                        Keyboard(this, R.xml.keys_emoji)
                    } else {
                        Keyboard(this, R.xml.keys_letters)
                    }
                    keyboardView.keyboard = keyboard
                }

                100700 -> {
                    isKaomoji = !isKaomoji
                    isSymbols = false
                    isEmoji = false
                    keyboard = if (isKaomoji) {
                        Keyboard(this, R.xml.keys_kaomoji)
                    } else {
                        Keyboard(this, R.xml.keys_letters)
                    }
                    keyboardView.keyboard = keyboard
                }

                100800 -> {
                    pasteFromClipboard()
                }

                else -> {
                    var code = primaryCode.toChar()
                    if ((isCaps || isCapsLock) && code.isLetter()) {
                        code = code.uppercaseChar()
                    }
                    ic.commitText(code.toString(), 1)
                    if (isCaps && !isCapsLock) {
                        isCaps = false
                        keyboard.isShifted = false
                        keyboardView.invalidateAllKeys()
                    }
                    autoCapitalize()
                }
            }
        } catch (e: Exception) {}
    }

    override fun onPress(primaryCode: Int) {
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            deleteHandler.postDelayed(deleteRunnable, 400)
        }
    }

    override fun onRelease(primaryCode: Int) {
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            deleteHandler.removeCallbacks(deleteRunnable)
        }
    }

    override fun onDestroy() {
        deleteHandler.removeCallbacks(deleteRunnable)
        super.onDestroy()
    }

    override fun swipeDown() {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}
}
