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
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

class MyKeyboardService : InputMethodService() {

    private lateinit var rootView: View
    private var isCaps = false
    private var isCapsLock = false
    private var currentMode = 0
    private var vibrator: Vibrator? = null
    private var lastShiftTime = 0L
    private var lastSpaceTime = 0L
    private val deleteHandler = Handler(Looper.getMainLooper())
    private var shiftBtn: Button? = null
    private var pasteBtn: Button? = null

    private val deleteRunnable = object : Runnable {
        override fun run() {
            try {
                currentInputConnection?.deleteSurroundingText(1, 0)
                deleteHandler.postDelayed(this, 50)
            } catch (e: Exception) {}
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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
        } catch (e: Exception) {
            vibrator = null
        }
    }

    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_main, null)
        showLetters()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentMode = 0
        isCaps = false
        isCapsLock = false
        showLetters()
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

    private fun type(text: String) {
        try {
            currentInputConnection?.commitText(text, 1)
            vibrate()
        } catch (e: Exception) {}
    }

    private fun autoCapitalize() {
        try {
            val ic = currentInputConnection ?: return
            val before = ic.getTextBeforeCursor(2, 0) ?: return
            if (before.isEmpty() || before.last() in listOf('.', '!', '?')) {
                isCaps = true
                shiftBtn?.text = "⇧"
            }
        } catch (e: Exception) {}
    }

    private fun checkClipboard(): Boolean {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.hasPrimaryClip() && (cm.primaryClip?.itemCount ?: 0) > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getContainer(): LinearLayout {
        return rootView.findViewById(R.id.keyboard_container)
    }

    private fun makeBtn(
        label: String,
        weight: Float = 1f,
        textSize: Float = 15f,
        onClick: () -> Unit
    ): Button {
        val btn = Button(this)
        btn.text = label
        btn.textSize = textSize
        btn.setTextColor(resources.getColor(R.color.key_text, null))
        btn.background = resources.getDrawable(R.drawable.key_background, null)
        btn.setPadding(0, 0, 0, 0)
        val p = LinearLayout.LayoutParams(0, dp(46), weight)
        p.setMargins(dp(2), dp(3), dp(2), dp(3))
        btn.layoutParams = p
        btn.setOnClickListener { onClick() }
        return btn
    }

    private fun makeRow(margin: Int = 4): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(dp(margin), 0, dp(margin), 0)
        row.layoutParams = p
        return row
    }

    private fun handle(code: String) {
        try {
            val ic = currentInputConnection ?: return
            vibrate()
            when (code) {
                "DEL" -> {
                    val sel = ic.getSelectedText(0)
                    if (!sel.isNullOrEmpty()) ic.commitText("", 1)
                    else ic.deleteSurroundingText(1, 0)
                }
                "SHIFT" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastShiftTime < 300) {
                        isCapsLock = !isCapsLock
                        isCaps = isCapsLock
                        shiftBtn?.text = if (isCapsLock) "⇪" else "shift"
                    } else {
                        if (!isCapsLock) {
                            isCaps = !isCaps
                            shiftBtn?.text = if (isCaps) "⇧" else "shift"
                        }
                    }
                    lastShiftTime = now
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
                        shiftBtn?.text = "⇧"
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
                "SYM" -> showSymbols()
                "ABC" -> showLetters()
                "EMO" -> showEmoji()
                "KAO" -> showKaomoji()
                else -> {
                    val out = if ((isCaps || isCapsLock) && code.length == 1 && code[0].isLetter()) code.uppercase() else code
                    ic.commitText(out, 1)
                    if (isCaps && !isCapsLock) {
                        isCaps = false
                        shiftBtn?.text = "shift"
                    }
                    autoCapitalize()
                }
            }
        } catch (e: Exception) {}
    }

    private fun delBtn(): Button {
        val btn = makeBtn("⌫", 1.5f) { handle("DEL") }
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> deleteHandler.postDelayed(deleteRunnable, 500)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> deleteHandler.removeCallbacks(deleteRunnable)
            }
            false
        }
        return btn
    }

    private fun bottomRow(leftLabel: String, leftCode: String): LinearLayout {
        val row = makeRow(4)
        val lp = row.layoutParams as LinearLayout.LayoutParams
        lp.setMargins(dp(4), 0, dp(4), dp(4))
        row.layoutParams = lp

        val left = makeBtn(leftLabel, 1.5f) { handle(leftCode) }

        pasteBtn = makeBtn("pst", 1f) { handle("PASTE") }
        pasteBtn?.visibility = if (checkClipboard()) View.VISIBLE else View.GONE

        val space = makeBtn("space", 4f) { handle("SPACE") }
        val dot = makeBtn(".", 0.7f) { handle(".") }
        val enter = makeBtn("↵", 1.5f) { handle("ENTER") }

        row.addView(left)
        row.addView(pasteBtn)
        row.addView(space)
        row.addView(dot)
        row.addView(enter)
        return row
    }

    private fun showLetters() {
        currentMode = 0
        val c = getContainer()
        c.removeAllViews()

        val r1 = makeRow()
        listOf("q","w","e","r","t","y","u","i","o","p").forEach { key ->
            r1.addView(makeBtn(key) { handle(key) })
        }

        val r2 = makeRow(20)
        listOf("a","s","d","f","g","h","j","k","l").forEach { key ->
            r2.addView(makeBtn(key) { handle(key) })
        }

        val r3 = makeRow()
        shiftBtn = makeBtn("shift", 1.5f) { handle("SHIFT") }
        r3.addView(shiftBtn)
        listOf("z","x","c","v","b","n","m").forEach { key ->
            r3.addView(makeBtn(key) { handle(key) })
        }
        r3.addView(delBtn())

        val r4 = makeRow(4)
        val lp4 = r4.layoutParams as LinearLayout.LayoutParams
        lp4.setMargins(dp(4), 0, dp(4), dp(4))
        r4.layoutParams = lp4

        val sym = makeBtn("123", 1.2f) { handle("SYM") }
        val emo = makeBtn("☺", 1f) { handle("EMO") }
        val kao = makeBtn("(^)", 1f) { handle("KAO") }
        pasteBtn = makeBtn("pst", 1f) { handle("PASTE") }
        pasteBtn?.visibility = if (checkClipboard()) View.VISIBLE else View.GONE
        val space = makeBtn("space", 4f) { handle("SPACE") }
        val dot = makeBtn(".", 0.7f) { handle(".") }
        val enter = makeBtn("↵", 1.5f) { handle("ENTER") }

        r4.addView(sym)
        r4.addView(emo)
        r4.addView(kao)
        r4.addView(pasteBtn)
        r4.addView(space)
        r4.addView(dot)
        r4.addView(enter)

        c.addView(r1)
        c.addView(r2)
        c.addView(r3)
        c.addView(r4)
    }

    private fun showSymbols() {
        currentMode = 1
        val c = getContainer()
        c.removeAllViews()

        val r1 = makeRow()
        listOf("1","2","3","4","5","6","7","8","9","0").forEach { k -> r1.addView(makeBtn(k) { handle(k) }) }

        val r2 = makeRow()
        listOf("@","#","$","%","&","*","(",")","−","+").forEach { k -> r2.addView(makeBtn(k) { handle(k) }) }

        val r3 = makeRow()
        listOf("!","\"","'",":",";","/","?","=").forEach { k -> r3.addView(makeBtn(k) { handle(k) }) }
        r3.addView(delBtn())

        c.addView(r1)
        c.addView(r2)
        c.addView(r3)
        c.addView(bottomRow("ABC", "ABC"))
    }

    private fun showEmoji() {
        currentMode = 2
        val c = getContainer()
        c.removeAllViews()

        val emojis = listOf(
            "😂","🤣","😭","😍","🥰","😘","😊","😇",
            "😎","😜","😅","😌","😒","😔","🙃","😤",
            "❤️","💖","💕","💞","💝","🧡","💛","💚",
            "✨","🌹","🌸","🌺","🍀","🌙","⭐","🔥",
            "💪","👊","🤝","🙏","👍","👌","🫂","🤗",
            "🐐","🗿","📚","🎀","💃","🍻","🏠","🎵",
            "😀","😃","😄","😆","😋","🤭","🫶","💅"
        )

        val scroll = ScrollView(this)
        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        scroll.layoutParams = slp

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL

        emojis.chunked(8).forEach { chunk ->
            val row = makeRow(4)
            chunk.forEach { emoji ->
                val btn = makeBtn(emoji, 1f, 20f) { type(emoji) }
                row.addView(btn)
            }
            inner.addView(row)
        }

        scroll.addView(inner)

        val bot = makeRow(4)
        val blp = bot.layoutParams as LinearLayout.LayoutParams
        blp.setMargins(dp(4), 0, dp(4), dp(4))
        bot.layoutParams = blp
        bot.addView(makeBtn("ABC", 1f) { handle("ABC") })
        bot.addView(makeBtn("space", 3f) { handle("SPACE") })
        bot.addView(delBtn())
        bot.addView(makeBtn("↵", 1f) { handle("ENTER") })

        c.addView(scroll)
        c.addView(bot)
    }

    private fun showKaomoji() {
        currentMode = 3
        val c = getContainer()
        c.removeAllViews()

        val list = listOf(
            "(❤‿❤)" to "(❤‿❤)",
            "(｡♥‿♥｡)" to "(｡♥‿♥｡)",
            "(づ◕‿◕)づ" to "(づ◕‿◕)づ",
            "( ˘³˘)♥" to "( ˘³˘)♥",
            "(づ￣³￣)づ" to "(づ￣³￣)づ",
            "(´•ᵕ•`)♡" to "(´｡•ᵕ•｡`)♡",
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
            "lenny" to "( ͡° ͜ʖ ͡°)",
            "(¬‿¬)" to "(¬‿¬)",
            "(ಠ_ಠ)" to "(ಠ_ಠ)",
            "flip" to "(ノಠ益ಠ)ノ彡┻━┻",
            "hug" to "(つ≧▽≦)つ",
            "(〃ω〃)" to "(〃ω〃)",
            "<3" to "<3",
            "✧ﾟ" to "✧･ﾟ:✧･ﾟ:",
            "(⌐■■)" to "(⌐■■)",
            "☞ʖ☞" to "(☞ ͡° ͜ʖ ͡°)☞",
            "ง°ง" to "(ง ͠° ͟ل͜ ͡°)ง",
            "(ಠ‿ಠ)" to "(ಠ‿ಠ)"
        )

        val scroll = ScrollView(this)
        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        scroll.layoutParams = slp

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL

        list.chunked(3).forEach { chunk ->
            val row = makeRow(4)
            chunk.forEach { (label, output) ->
                val btn = makeBtn(label, 1f, 11f) { type(output) }
                row.addView(btn)
            }
            inner.addView(row)
        }

        scroll.addView(inner)

        val bot = makeRow(4)
        val blp = bot.layoutParams as LinearLayout.LayoutParams
        blp.setMargins(dp(4), 0, dp(4), dp(4))
        bot.layoutParams = blp
        bot.addView(makeBtn("ABC", 1f) { handle("ABC") })
        bot.addView(makeBtn("space", 3f) { handle("SPACE") })
        bot.addView(delBtn())
        bot.addView(makeBtn("↵", 1f) { handle("ENTER") })

        c.addView(scroll)
        c.addView(bot)
    }

    override fun onDestroy() {
        deleteHandler.removeCallbacks(deleteRunnable)
        super.onDestroy()
    }
}
