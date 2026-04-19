package com.example.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

class MyKeyboardService : InputMethodService() {

    private lateinit var keyboardView: View
    private var isCaps = false
    private var isCapsLock = false
    private var currentMode = MODE_LETTERS
    private var vibrator: Vibrator? = null
    private var lastShiftTime = 0L
    private var lastSpaceTime = 0L
    private val deleteHandler = Handler(Looper.getMainLooper())
    private var pasteBtn: Button? = null

    companion object {
        const val MODE_LETTERS = 0
        const val MODE_SYMBOLS = 1
        const val MODE_EMOJI = 2
        const val MODE_KAOMOJI = 3
    }

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
        keyboardView = layoutInflater.inflate(R.layout.keyboard_main, null)
        setupKeyboard()
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentMode = MODE_LETTERS
        isCaps = false
        isCapsLock = false
        showLetterKeys()
        updatePasteButton()
    }

    private fun setupKeyboard() {
        showLetterKeys()
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (e: Exception) {}
    }

    private fun commitText(text: String) {
        try {
            currentInputConnection?.commitText(text, 1)
            vibrate()
        } catch (e: Exception) {}
    }

    private fun autoCapitalize() {
        try {
            val ic = currentInputConnection ?: return
            val text = ic.getTextBeforeCursor(2, 0) ?: return
            if (text.isEmpty() || text.last() in listOf('.', '!', '?')) {
                isCaps = true
                updateShiftKey()
            }
        } catch (e: Exception) {}
    }

    private fun updateShiftKey() {
        try {
            val shiftBtn = keyboardView.findViewById<Button>(R.id.key_shift)
            shiftBtn?.text = if (isCapsLock) "⇪" else if (isCaps) "⇧" else "shift"
        } catch (e: Exception) {}
    }

    private fun updatePasteButton() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val hasClip = cm.hasPrimaryClip() && (cm.primaryClip?.itemCount ?: 0) > 0
            pasteBtn?.visibility = if (hasClip) View.VISIBLE else View.GONE
        } catch (e: Exception) {}
    }

    private fun handleKey(code: String) {
        try {
            val ic = currentInputConnection ?: return
            vibrate()
            when (code) {
                "DEL" -> {
                    val selected = ic.getSelectedText(0)
                    if (!selected.isNullOrEmpty()) ic.commitText("", 1)
                    else ic.deleteSurroundingText(1, 0)
                }
                "SHIFT" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastShiftTime < 300) {
                        isCapsLock = !isCapsLock
                        isCaps = isCapsLock
                    } else {
                        if (!isCapsLock) isCaps = !isCaps
                    }
                    lastShiftTime = now
                    updateShiftKey()
                    refreshLetterKeys()
                }
                "ENTER" -> {
                    val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        ic.performEditorAction(action)
                    } else {
                        ic.commitText("\n", 1)
                    }
                }
                "SPACE" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastSpaceTime < 300) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                        isCaps = true
                        updateShiftKey()
                    } else {
                        ic.commitText(" ", 1)
                        autoCapitalize()
                    }
                    lastSpaceTime = now
                }
                "PASTE" -> {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this) ?: return
                    ic.commitText(text, 1)
                    vibrate()
                }
                "SYM" -> { currentMode = MODE_SYMBOLS; showSymbolKeys() }
                "ABC" -> { currentMode = MODE_LETTERS; showLetterKeys() }
                "EMO" -> { currentMode = MODE_EMOJI; showEmojiKeys() }
                "KAO" -> { currentMode = MODE_KAOMOJI; showKaomojiKeys() }
                else -> {
                    val char = if ((isCaps || isCapsLock) && code.length == 1 && code[0].isLetter()) {
                        code.uppercase()
                    } else code
                    ic.commitText(char, 1)
                    if (isCaps && !isCapsLock) {
                        isCaps = false
                        updateShiftKey()
                        refreshLetterKeys()
                    }
                    autoCapitalize()
                }
            }
        } catch (e: Exception) {}
    }

    private fun makeKey(label: String, code: String, weight: Float = 1f): Button {
        val btn = Button(this)
        btn.text = label
        btn.textSize = 16f
        btn.setTextColor(resources.getColor(R.color.key_text, null))
        btn.background = resources.getDrawable(R.drawable.key_background, null)
        val params = LinearLayout.LayoutParams(0, dpToPx(46), weight)
        params.setMargins(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3))
        btn.layoutParams = params
        btn.setPadding(0, 0, 0, 0)
        btn.setOnClickListener { handleKey(code) }
        if (code == "DEL") {
            btn.setOnLongClickListener {
                deleteHandler.postDelayed(deleteRunnable, 400)
                true
            }
            btn.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    deleteHandler.removeCallbacks(deleteRunnable)
                }
                false
            }
        }
        return btn
    }

    private fun makeRow(vararg keys: Pair<String, String>, weights: FloatArray? = null): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowParams.setMargins(dpToPx(4), 0, dpToPx(4), 0)
        row.layoutParams = rowParams
        keys.forEachIndexed { i, (label, code) ->
            val w = weights?.getOrNull(i) ?: 1f
            row.addView(makeKey(label, code, w))
        }
        return row
    }

    private fun showLetterKeys() {
        val container = keyboardView.findViewById<LinearLayout>(R.id.keyboard_container)
        container.removeAllViews()

        val row1 = makeRow("q" to "q", "w" to "w", "e" to "e", "r" to "r", "t" to "t", "y" to "y", "u" to "u", "i" to "i", "o" to "o", "p" to "p")

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val r2p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        r2p.setMargins(dpToPx(20), 0, dpToPx(20), 0)
        row2.layoutParams = r2p
        listOf("a","s","d","f","g","h","j","k","l").forEach { c ->
            row2.addView(makeKey(c, c))
        }

        val row3 = makeRow(
            "shift" to "SHIFT", "z" to "z", "x" to "x", "c" to "c", "v" to "v", "b" to "b", "n" to "n", "m" to "m", "del" to "DEL",
            weights = floatArrayOf(1.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.5f)
        )

        val row4 = LinearLayout(this)
        row4.orientation = LinearLayout.HORIZONTAL
        val r4p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        r4p.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(4))
        row4.layoutParams = r4p

        val sym = makeKey("123", "SYM", 1.2f)
        val emo = makeKey("☺", "EMO", 1f)
        val kao = makeKey("(^)", "KAO", 1f)
        pasteBtn = makeKey("pst", "PASTE", 1f)
        pasteBtn?.visibility = View.GONE
        val space = makeKey("space", "SPACE", 4f)
        val dot = makeKey(".", ".", 0.8f)
        val enter = makeKey("enter", "ENTER", 1.5f)

        row4.addView(sym)
        row4.addView(emo)
        row4.addView(kao)
        row4.addView(pasteBtn)
        row4.addView(space)
        row4.addView(dot)
        row4.addView(enter)

        container.addView(row1)
        container.addView(row2)
        container.addView(row3)
        container.addView(row4)

        updateShiftKey()
        updatePasteButton()
    }

    private fun refreshLetterKeys() {
        if (currentMode == MODE_LETTERS) showLetterKeys()
    }

    private fun showSymbolKeys() {
        val container = keyboardView.findViewById<LinearLayout>(R.id.keyboard_container)
        container.removeAllViews()

        val row1 = makeRow("1" to "1","2" to "2","3" to "3","4" to "4","5" to "5","6" to "6","7" to "7","8" to "8","9" to "9","0" to "0")
        val row2 = makeRow("@" to "@","#" to "#","$" to "$","%" to "%","&" to "&","*" to "*","(" to "(",")" to ")","−" to "-","+" to "+")
        val row3 = makeRow("!" to "!","\"" to "\"","'" to "'",":" to ":",";" to ";","/" to "/","?" to "?","=" to "=","⌫" to "DEL",
            weights = floatArrayOf(1f,1f,1f,1f,1f,1f,1f,1f,1.5f))

        val row4 = LinearLayout(this)
        row4.orientation = LinearLayout.HORIZONTAL
        val r4p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        r4p.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(4))
        row4.layoutParams = r4p

        pasteBtn = makeKey("pst", "PASTE", 1f)
        pasteBtn?.visibility = View.GONE
        row4.addView(makeKey("ABC", "ABC", 1.5f))
        row4.addView(pasteBtn)
        row4.addView(makeKey("space", "SPACE", 4f))
        row4.addView(makeKey(".", ".", 0.8f))
        row4.addView(makeKey("enter", "ENTER", 1.5f))

        container.addView(row1)
        container.addView(row2)
        container.addView(row3)
        container.addView(row4)

        updatePasteButton()
    }

    private fun showEmojiKeys() {
        val container = keyboardView.findViewById<LinearLayout>(R.id.keyboard_container)
        container.removeAllViews()

        val scroll = android.widget.ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(200)
        )

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL

        val emojis = listOf(
            "😂","🤣","😭","😍","🥰","😘","😊","😇",
            "😎","😜","😅","😌","😒","😔","🙃","😤",
            "❤️","💖","💕","💞","💝","🧡","💛","💚",
            "✨","🌹","🌸","🌺","🍀","🌙","⭐","🔥",
            "💪","👊","🤝","🙏","👍","👌","🫂","🤗",
            "🐐","🗿","📚","🎀","💃","🍻","🏠","🎵"
        )

        emojis.chunked(8).forEach { chunk ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.setMargins(dpToPx(4), 0, dpToPx(4), 0)
            row.layoutParams = rp
            chunk.forEach { emoji ->
                val btn = Button(this)
                btn.text = emoji
                btn.textSize = 20f
                btn.background = resources.getDrawable(R.drawable.key_background, null)
                val bp = LinearLayout.LayoutParams(0, dpToPx(48), 1f)
                bp.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                btn.layoutParams = bp
                btn.setPadding(0, 0, 0, 0)
                btn.setOnClickListener { commitText(emoji) }
                row.addView(btn)
            }
            inner.addView(row)
        }

        scroll.addView(inner)

        val bottomRow = LinearLayout(this)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        val bp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bp.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(4))
        bottomRow.layoutParams = bp
        bottomRow.addView(makeKey("ABC", "ABC", 1f))
        bottomRow.addView(makeKey("space", "SPACE", 3f))
        bottomRow.addView(makeKey("del", "DEL", 1f))
        bottomRow.addView(makeKey("enter", "ENTER", 1f))

        container.addView(scroll)
        container.addView(bottomRow)
    }

    private fun showKaomojiKeys() {
        val container = keyboardView.findViewById<LinearLayout>(R.id.keyboard_container)
        container.removeAllViews()

        val scroll = android.widget.ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(200)
        )

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL

        val kaoList = listOf(
            "(❤‿❤)" to "(❤‿❤)",
            "(｡♥‿♥｡)" to "(｡♥‿♥｡)",
            "(づ◕‿◕)づ" to "(づ◕‿◕)づ",
            "( ˘³˘)♥" to "( ˘³˘)♥",
            "(づ￣³￣)づ" to "(づ￣³￣)づ",
            "(´｡•ᵕ•｡`)♡" to "(´｡•ᵕ•｡`)♡",
            "(＾▽＾)" to "(＾▽＾)",
            "(≧◡≦)" to "(≧◡≦)",
            "(⌒‿⌒)" to "(⌒‿⌒)",
            "(◕‿◕)" to "(◕‿◕)",
            "(✿◠‿◠)" to "(✿◠‿◠)",
            "(≧▽≦)" to "(≧▽≦)",
            "(╥﹏╥)" to "(╥﹏╥)",
            "(ಥ﹏ಥ)" to "(ಥ﹏ಥ)",
            "(T_T)" to "(T_T)",
            "(；＿；)" to "(；＿；)",
            "(｡•́︿•̀｡)" to "(｡•́︿•̀｡)",
            "( ͡°ʖ ͡°)" to "( ͡° ͜ʖ ͡°)",
            "(¬‿¬)" to "(¬‿¬)",
            "(ಠ_ಠ)" to "(ಠ_ಠ)",
            "(ノಠ益ಠ)ノ彡┻━┻" to "(ノಠ益ಠ)ノ彡┻━┻",
            "(ง°ل°)ง" to "(ง ͠° ͟ل͜ ͡°)ง",
            "(☞ʖ☞)" to "(☞ ͡° ͜ʖ ͡°)☞",
            "(つ≧▽≦)つ" to "(つ≧▽≦)つ",
            "(〃ω〃)" to "(〃ω〃)",
            "<3" to "<3",
            "✧ﾟ✧" to "✧･ﾟ:✧･ﾟ:",
            "(⌐■■)" to "(⌐■■)"
        )

        kaoList.chunked(3).forEach { chunk ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.setMargins(dpToPx(4), 0, dpToPx(4), 0)
            row.layoutParams = rp
            chunk.forEach { (label, output) ->
                val btn = Button(this)
                btn.text = label
                btn.textSize = 11f
                btn.setTextColor(resources.getColor(R.color.key_text, null))
                btn.background = resources.getDrawable(R.drawable.key_background, null)
                val bp = LinearLayout.LayoutParams(0, dpToPx(46), 1f)
                bp.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                btn.layoutParams = bp
                btn.setPadding(dpToPx(2), 0, dpToPx(2), 0)
                btn.setOnClickListener { commitText(output) }
                row.addView(btn)
            }
            inner.addView(row)
        }

        scroll.addView(inner)

        val bottomRow = LinearLayout(this)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        val bp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bp.setMargins(dpToPx(4), 0, dpToPx(4)
