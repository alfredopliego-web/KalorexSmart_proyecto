package com.kalorex.smart

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity() {
    companion object { private const val WRAP = -2 }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var ipInput: EditText
    private lateinit var status: TextView
    private lateinit var angleText: TextView
    private lateinit var dial: ServoDial
    private var angle = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.rgb(16, 24, 32))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "KalorexSmart"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, match(WRAP))
        root.addView(TextView(this).apply {
            text = "CONTROL DE SERVOMOTOR"; textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }, match(WRAP))
        status = TextView(this).apply { text = "● Sin comprobar conexión"; textSize = 14f; setTextColor(Color.YELLOW); gravity = Gravity.CENTER; setPadding(0, 18, 0, 8) }
        root.addView(status, match(WRAP))
        ipInput = EditText(this).apply {
            setSingleLine(true); hint = "IP del ESP8266 (ej. 192.168.1.120)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setPadding(18, 8, 18, 8); setBackgroundColor(Color.rgb(37, 49, 61)); setText("192.168.1.120")
        }
        root.addView(ipInput, match(WRAP))
        val check = Button(this).apply { text = "PROBAR CONEXIÓN"; setOnClickListener { checkConnection() } }
        root.addView(check, match(WRAP))
        angleText = TextView(this).apply { text = "0°"; textSize = 42f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 10, 0, 0) }
        root.addView(angleText, match(WRAP))
        dial = ServoDial(this) { value -> angle = value; angleText.text = "$value°"; sendAngle(value) }
        root.addView(dial, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply { text = "0°"; setOnClickListener { setAngle(0) } })
        buttons.addView(Button(this).apply { text = "90°"; setOnClickListener { setAngle(90) } })
        buttons.addView(Button(this).apply { text = "180°"; setOnClickListener { setAngle(180) } })
        root.addView(buttons, match(WRAP))
        root.addView(TextView(this).apply {
            text = "Celular y ESP8266 deben estar conectados a la misma red Wi-Fi. Usa solo en banco de pruebas; no conectes este prototipo a una válvula de gas."
            textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(8, 16, 8, 0)
        }, match(WRAP))
        setContentView(root)
    }

    private fun match(height: Int) = LinearLayout.LayoutParams(-1, height)
    private fun setAngle(value: Int) { angle = value; angleText.text = "$value°"; dial.setValue(value); sendAngle(value) }
    private fun host(): String = ipInput.text.toString().trim().removePrefix("http://").removeSuffix("/")

    private fun checkConnection() {
        val address = host()
        if (address.isBlank()) { Toast.makeText(this, "Escribe la IP del ESP8266", Toast.LENGTH_SHORT).show(); return }
        status.text = "Comprobando..."
        executor.execute {
            val ok = request("http://$address/status") != null
            runOnUiThread { status.text = if (ok) "● ESP8266 conectado" else "● Sin respuesta: revisa IP y Wi-Fi"; status.setTextColor(if (ok) Color.GREEN else Color.YELLOW) }
        }
    }

    private fun sendAngle(value: Int) {
        val address = host()
        if (address.isBlank()) return
        executor.execute {
            val result = request("http://$address/mover?angulo=$value")
            runOnUiThread {
                if (result != null) { status.text = "● Orden enviada: $value°"; status.setTextColor(Color.GREEN) }
                else { status.text = "● No responde el ESP8266"; status.setTextColor(Color.YELLOW) }
            }
        }
    }

    private fun request(address: String): String? {
        return try {
            val connection = (URL(address).openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 1800; readTimeout = 1800 }
            val result = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect(); result
        } catch (_: Exception) { null }
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    private class ServoDial(ctx: android.content.Context, val onChange: (Int) -> Unit) : View(ctx) {
        private val track = Paint(3).apply { color = Color.rgb(57, 72, 86); style = Paint.Style.STROKE; strokeWidth = 24f; strokeCap = Paint.Cap.ROUND }
        private val active = Paint(3).apply { color = Color.rgb(22, 166, 160); style = Paint.Style.STROKE; strokeWidth = 24f; strokeCap = Paint.Cap.ROUND }
        private val knob = Paint(3).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val bounds = RectF()
        private var value = 0
        fun setValue(v: Int) { value = v.coerceIn(0,180); invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = min(width * .78f, height * .78f); val cx = width / 2f; val cy = height / 2f
            bounds.set(cx-size/2, cy-size/2, cx+size/2, cy+size/2)
            canvas.drawArc(bounds, 135f, 270f, false, track)
            canvas.drawArc(bounds, 135f, value * 1.5f, false, active)
            val radians = Math.toRadians((135 + value * 1.5).toDouble())
            canvas.drawCircle(cx + (size/2)*cos(radians).toFloat(), cy + (size/2)*sin(radians).toFloat(), 17f, knob)
            val p = Paint(3).apply { color = Color.LTGRAY; textSize = 30f; textAlign = Paint.Align.CENTER }
            canvas.drawText("GIRA PARA AJUSTAR", cx, cy + size*.62f, p)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP) {
                val cx=width/2f; val cy=height/2f
                var deg = Math.toDegrees(atan2((event.y-cy).toDouble(), (event.x-cx).toDouble()))
                deg = (deg + 360) % 360
                val relative = if (deg >= 135) deg - 135 else deg + 225
                value = (relative / 1.5).toInt().coerceIn(0,180)
                invalidate()
                if (event.action != MotionEvent.ACTION_MOVE || event.eventTime % 70L < 20L) onChange(value)
                return true
            }
            return true
        }
    }
}
