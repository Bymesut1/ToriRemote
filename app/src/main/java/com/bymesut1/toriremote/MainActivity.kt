package com.bymesut1.toriremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

data class Key(
    val id: String,
    val label: String,
    val def: Int,
    val color: Int = 0xFF3A3F47.toInt(),
    val repeat: Boolean = false
)

class MainActivity : Activity() {

    private var ir: ConsumerIrManager? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val red = 0xFFD32F2F.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val purple = 0xFF8E24AA.toInt()

    private val keys = listOf(
        Key("power", "⏻", 0x45, red),
        Key("mode", "MODE", 0x46),
        Key("mute", "🔇", 0x47, red),
        Key("play", "⏯", 0x44, blue),
        Key("prev", "⏮", 0x40),
        Key("next", "⏭", 0x43),
        Key("eq", "EQ", 0x07, purple),
        Key("volm", "VOL−", 0x15, repeat = true),
        Key("volp", "VOL+", 0x09, repeat = true),
        Key("n0", "0", 0x16),
        Key("rpt", "RPT", 0x19),
        Key("usb", "USB/SD", 0x0D),
        Key("n1", "1", 0x0C),
        Key("n2", "2", 0x18),
        Key("n3", "3", 0x5E),
        Key("n4", "4", 0x08),
        Key("n5", "5", 0x1C),
        Key("n6", "6", 0x5A),
        Key("n7", "7", 0x42),
        Key("n8", "8", 0x52),
        Key("n9", "9", 0x4A)
    )

    private val rows = listOf(
        listOf("power", "mode", "mute"),
        listOf("play", "prev", "next"),
        listOf("eq", "volm", "volp"),
        listOf("n0", "rpt", "usb"),
        listOf("n1", "n2", "n3"),
        listOf("n4", "n5", "n6"),
        listOf("n7", "n8", "n9")
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addr() = prefs.getInt("addr", 0x00)
    private fun cmdOf(k: Key) = prefs.getInt("cmd_" + k.id, k.def)
    private fun hex(v: Int) = String.format("%02X", v and 0xFF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("tori", Context.MODE_PRIVATE)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        buildUi()
    }

    private fun roundBg(color: Int, radius: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101216.toInt())
            setPadding(dp(14), dp(20), dp(14), dp(20))
        }

        val title = TextView(this).apply {
            text = "TORIMA Kumanda"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title)

        status = TextView(this).apply {
            setTextColor(0xFFB0B6BF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(status)
        val hasIr = ir?.hasIrEmitter() == true
        status.text = if (hasIr) "IR vericisi hazır • Adres 0x${hex(addr())}"
        else "Bu telefonda IR vericisi bulunamadı"

        val byId = keys.associateBy { it.id }
        for (r in rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (id in r) {
                val k = byId.getValue(id)
                row.addView(makeKeyButton(k), LinearLayout.LayoutParams(0, dp(66), 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            root.addView(row)
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        bottom.addView(toolButton("⚙ Kodları düzenle") { editDialog() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        bottom.addView(toolButton("🔍 Kod tara") { pickKeyForScan() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        root.addView(bottom)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF101216.toInt())
            addView(root)
        }
        setContentView(scroll)
    }

    private fun toolButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 13f
        background = roundBg(0xFF262B33.toInt(), 12)
        setOnClickListener { onClick() }
    }

    private fun makeKeyButton(k: Key): Button {
        val b = Button(this)
        b.text = k.label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.textSize = if (k.label.length > 2) 15f else 22f
        b.typeface = Typeface.DEFAULT_BOLD
        b.background = roundBg(k.color)
        if (k.repeat) {
            val repeater = object : Runnable {
                override fun run() {
                    sendKey(k)
                    handler.postDelayed(this, 170)
                }
            }
            b.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        sendKey(k)
                        handler.postDelayed(repeater, 400)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        handler.removeCallbacks(repeater)
                }
                true
            }
        } else {
            b.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                sendKey(k)
            }
        }
        return b
    }

    private fun sendKey(k: Key) {
        send(addr(), cmdOf(k))
    }

    /** NEC protokolü, 38 kHz */
    private fun send(address: Int, command: Int) {
        val e = ir
        if (e == null || !e.hasIrEmitter()) {
            Toast.makeText(this, "IR vericisi yok", Toast.LENGTH_SHORT).show()
            return
        }
        val p = ArrayList<Int>()
        p.add(9000); p.add(4500)
        fun putByte(b: Int) {
            for (i in 0..7) {
                p.add(560)
                p.add(if (((b shr i) and 1) == 1) 1690 else 560)
            }
        }
        putByte(address and 0xFF)
        putByte((address and 0xFF).inv() and 0xFF)
        putByte(command and 0xFF)
        putByte((command and 0xFF).inv() and 0xFF)
        p.add(560); p.add(20000)
        try {
            e.transmit(38000, p.toIntArray())
        } catch (ex: Exception) {
            Toast.makeText(this, "Gönderilemedi: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Kod düzenleme ----------

    private fun hexField(initial: Int): EditText = EditText(this).apply {
        setText(hex(initial))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        setSingleLine()
        gravity = Gravity.CENTER
    }

    private fun parseHex(s: String, fallback: Int): Int =
        (s.trim().removePrefix("0x").toIntOrNull(16) ?: fallback) and 0xFF

    private fun editDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        fun line(label: String, field: EditText) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            r.addView(TextView(this).apply { text = label; textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(field, LinearLayout.LayoutParams(dp(90), -2))
            box.addView(r)
        }
        val addrField = hexField(addr())
        line("Adres (hex)", addrField)
        val fields = keys.map { k ->
            val f = hexField(cmdOf(k))
            line(k.label + "  " + k.id, f)
            k to f
        }
        val sv = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("Kodları düzenle (hex)")
            .setView(sv)
            .setPositiveButton("Kaydet") { _, _ ->
                val ed = prefs.edit()
                ed.putInt("addr", parseHex(addrField.text.toString(), addr()))
                for ((k, f) in fields) ed.putInt("cmd_" + k.id, parseHex(f.text.toString(), cmdOf(k)))
                ed.apply()
                status.text = "IR vericisi hazır • Adres 0x${hex(addr())}"
            }
            .setNeutralButton("Varsayılana dön") { _, _ ->
                prefs.edit().clear().apply()
                status.text = "IR vericisi hazır • Adres 0x${hex(addr())}"
                Toast.makeText(this, "Varsayılan kodlar yüklendi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ---------- Kod tarama ----------

    private fun pickKeyForScan() {
        val names = keys.map { it.label + "  (şu an 0x" + hex(cmdOf(it)) + ")" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Hangi tuşun kodunu arayalım?")
            .setItems(names) { _, i -> scanDialog(keys[i]) }
            .show()
    }

    private fun scanDialog(k: Key) {
        var cur = 0
        var running = false
        var lastSent = -1

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        val info = TextView(this).apply {
            text = "Telefonun üstünü hoparlöre doğrultun. Başlat'a basın; hoparlör ${k.label} tuşuna tepki verdiği anda Kaydet'e basın. ◀ ▶ ile tek tek de gezebilirsiniz."
            textSize = 14f
        }
        val tv = TextView(this).apply {
            text = "—"
            textSize = 34f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(16), 0, dp(16))
        }
        box.addView(info)
        box.addView(tv)

        fun sendStep(c: Int) {
            lastSent = c and 0xFF
            send(addr(), lastSent)
            tv.text = "0x" + hex(lastSent)
        }

        val runner = object : Runnable {
            override fun run() {
                if (!running) return
                if (cur > 0xFF) {
                    running = false
                    tv.text = "Tarama bitti"
                    return
                }
                sendStep(cur)
                cur++
                handler.postDelayed(this, 500)
            }
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = Button(this).apply { text = "Başlat"; isAllCaps = false }
        val backBtn = Button(this).apply { text = "◀"; isAllCaps = false }
        val fwdBtn = Button(this).apply { text = "▶"; isAllCaps = false }
        startBtn.setOnClickListener {
            running = !running
            startBtn.text = if (running) "Durdur" else "Başlat"
            if (running) handler.post(runner) else handler.removeCallbacks(runner)
        }
        backBtn.setOnClickListener {
            running = false; startBtn.text = "Başlat"; handler.removeCallbacks(runner)
            cur = ((if (lastSent < 0) 0 else lastSent) - 1).coerceAtLeast(0)
            sendStep(cur); cur++
        }
        fwdBtn.setOnClickListener {
            running = false; startBtn.text = "Başlat"; handler.removeCallbacks(runner)
            cur = ((if (lastSent < 0) -1 else lastSent) + 1).coerceAtMost(0xFF)
            sendStep(cur); cur++
        }
        row.addView(backBtn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(startBtn, LinearLayout.LayoutParams(0, -2, 2f))
        row.addView(fwdBtn, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row)

        val dialog = AlertDialog.Builder(this)
            .setTitle("${k.label} kodunu tara • adres 0x${hex(addr())}")
            .setView(box)
            .setPositiveButton("Kaydet", null)
            .setNegativeButton("Kapat", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (lastSent < 0) {
                    Toast.makeText(this, "Önce bir kod gönderin", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putInt("cmd_" + k.id, lastSent).apply()
                    Toast.makeText(this, "${k.label} = 0x${hex(lastSent)} kaydedildi", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.setOnDismissListener {
            running = false
            handler.removeCallbacks(runner)
        }
        dialog.show()
    }
}
