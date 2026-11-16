package com.kalorex.smart

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.json.JSONObject

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("kalorex_settings", MODE_PRIVATE) }

    private var deviceIp = "192.168.1.188"
    private var currentAngle = 90
    private var isConnected = false
    private var themeIndex = 0
    private var timerDurationMs = 30L * 60L * 1000L
    private var remainingMs = timerDurationMs
    private var countdown: CountDownTimer? = null
    private var timerRunning = false
    private var selectedDay = 0

    private val dayNames = arrayOf(
        "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    )

    private data class DayPlan(
        var enabled: Boolean = false,
        var start: String = "06:00 AM",
        var end: String = "10:00 PM"
    )

    private val plans = MutableList(7) { DayPlan() }

    private var bgColor = Color.rgb(7, 20, 43)
    private var panelColor = Color.rgb(14, 34, 64)
    private var accentColor = Color.rgb(0, 220, 255)
    private var textColor = Color.WHITE
    private var successColor = Color.rgb(30, 220, 75)

    private lateinit var mainLayout: LinearLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var clockText: TextView
    private lateinit var angleText: TextView
    private lateinit var timerText: TextView
    private lateinit var dial: ServoDial

    private val clockRunnable = object : Runnable {
        override fun run() {
            if (::clockText.isInitialized) {
                clockText.text = SimpleDateFormat("hh:mm:ss a", Locale("es", "MX"))
                    .format(Date()).uppercase(Locale("es", "MX"))
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceIp = prefs.getString("device_ip", deviceIp) ?: deviceIp
        themeIndex = prefs.getInt("theme", 0)
        loadPlans()
        applyTheme()
        createShell()
        showHome()
        handler.post(clockRunnable)
        checkConnection()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applyTheme() {
        when (themeIndex) {
            0 -> {
                bgColor = Color.rgb(7, 20, 43)
                panelColor = Color.rgb(14, 34, 64)
                accentColor = Color.rgb(0, 220, 255)
                textColor = Color.WHITE
                successColor = Color.rgb(30, 220, 75)
            }
            1 -> {
                bgColor = Color.rgb(12, 12, 14)
                panelColor = Color.rgb(31, 31, 34)
                accentColor = Color.rgb(235, 235, 235)
                textColor = Color.WHITE
                successColor = Color.rgb(80, 210, 130)
            }
            else -> {
                bgColor = Color.rgb(248, 247, 245)
                panelColor = Color.WHITE
                accentColor = Color.rgb(190, 20, 48)
                textColor = Color.rgb(45, 48, 52)
                successColor = Color.rgb(30, 155, 75)
            }
        }
    }

    private fun createShell() {
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(14), dp(8), dp(14), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "🔥 KalorexSmart"
            textSize = 23f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        statusText = TextView(this).apply {
            text = "● Revisando"
            textSize = 12f
            setTextColor(textColor)
        }
        header.addView(statusText)
        header.addView(makeButton("IP") { showIpDialog() })
        mainLayout.addView(header)

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        mainLayout.addView(contentLayout, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(panelColor)
        }
        listOf(
            "⌂\nHome" to { showHome() },
            "▦\nCalendario" to { showCalendar() },
            "◷\nTemporizador" to { showTimer() },
            "⚙\nAjustes" to { showSettings() }
        ).forEach { item ->
            nav.addView(makeButton(item.first, item.second),
                LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        mainLayout.addView(nav)
        setContentView(mainLayout)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(textColor)
            setBackgroundColor(panelColor)
            isAllCaps = false
            minHeight = dp(42)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener { action() }
        }

    private fun label(parent: LinearLayout, value: String, size: Float = 17f): TextView {
        val t = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        parent.addView(t)
        return t
    }

    private fun space(parent: LinearLayout, height: Int = 6) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun showHome() {
        contentLayout.removeAllViews()
        label(contentLayout, "CONTROL MANUAL DE POSICIÓN", 14f)
        clockText = label(contentLayout, "12:00:00 PM", 25f).apply {
            setTextColor(accentColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        label(contentLayout, "Hora local del teléfono", 11f)
        space(contentLayout, 3)
        label(contentLayout, "Posición de prueba", 15f)
        angleText = label(contentLayout, "$currentAngle°", 31f).apply {
            setTextColor(accentColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        dial = ServoDial(this).apply {
            angle = currentAngle
            onAngleChanged = { value ->
                currentAngle = value
                angleText.text = "$value°"
            }
            onAngleSelected = { value ->
                currentAngle = value
                angleText.text = "$value°"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Control manual de prueba")
                    .setMessage("Confirma que el servo está sin carga y no está acoplado a una válvula, perilla de gas ni mecanismo del quemador.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Enviar posición") { _, _ -> sendAngle(value) }
                    .show()
            }
        }
        contentLayout.addView(dial, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        label(contentLayout, "Desliza el indicador para seleccionar una posición", 11f)
        contentLayout.addView(makeButton("REGRESAR INDICADOR A 0°") {
            currentAngle = 0
            angleText.text = "0°"
            dial.angle = 0
            dial.invalidate()
            Toast.makeText(this, "Indicador en cero. No se envió movimiento al servo.", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
    }

    private fun showCalendar() {
        contentLayout.removeAllViews()
        label(contentLayout, "CALENDARIO SEMANAL", 21f)
        label(contentLayout, "Configura una ventana horaria por día", 12f)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val daySpinner = Spinner(this)
        daySpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, dayNames)
        daySpinner.setSelection(selectedDay)
        list.addView(daySpinner)

        val enabled = CheckBox(this).apply {
            text = "Programación activa este día"
            setTextColor(textColor)
            buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
        }
        val startButton = makeButton("") {}
        val endButton = makeButton("") {}
        fun refreshEditor() {
            val p = plans[selectedDay]
            enabled.isChecked = p.enabled
            startButton.text = "INICIO: ${p.start}"
            endButton.text = "FIN: ${p.end}"
        }
        daySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDay = position
                refreshEditor()
            }
        }
        list.addView(enabled)
        list.addView(startButton)
        list.addView(endButton)
        startButton.setOnClickListener { chooseTime(plans[selectedDay].start) {
            plans[selectedDay].start = it
            refreshEditor()
        } }
        endButton.setOnClickListener { chooseTime(plans[selectedDay].end) {
            plans[selectedDay].end = it
            refreshEditor()
        } }
        list.addView(makeButton("GUARDAR DÍA") {
            plans[selectedDay].enabled = enabled.isChecked
            savePlans()
            Toast.makeText(this, "Horario guardado en este teléfono.", Toast.LENGTH_LONG).show()
            refreshEditor()
        })
        list.addView(makeButton("DESACTIVAR DÍA") {
            plans[selectedDay].enabled = false
            enabled.isChecked = false
            savePlans()
            Toast.makeText(this, "Día desactivado.", Toast.LENGTH_SHORT).show()
        })
        space(list, 8)
        label(list, "Resumen semanal", 17f)
        val summary = TextView(this).apply {
            setTextColor(textColor)
            textSize = 13f
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        list.addView(summary)
        fun refreshSummary() {
            summary.text = plans.mapIndexed { i, p ->
                "${dayNames[i]}: " + if (p.enabled) "${p.start} – ${p.end}" else "Desactivado"
            }.joinToString("\n")
        }
        refreshEditor()
        refreshSummary()
        list.addView(makeButton("ACTUALIZAR RESUMEN") { refreshSummary() })
        label(list, "Nota: esta versión guarda la agenda localmente; aún no la ejecuta el ESP8266.", 11f)
        scroll.addView(list)
        contentLayout.addView(scroll, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
    }

    private fun chooseTime(current: String, onPicked: (String) -> Unit) {
        val parts = current.trim().split(Regex("\\s+"))
        val hm = parts[0].split(":")
        var hour = hm[0].toIntOrNull() ?: 6
        val minute = hm.getOrNull(1)?.toIntOrNull() ?: 0
        if (parts.getOrNull(1) == "PM" && hour < 12) hour += 12
        if (parts.getOrNull(1) == "AM" && hour == 12) hour = 0
        TimePickerDialog(this, { _, h, m ->
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = when (val v = h % 12) { 0 -> 12; else -> v }
            onPicked(String.format(Locale.US, "%02d:%02d %s", h12, m, suffix))
        }, hour, minute, false).show()
    }

    private fun showTimer() {
        contentLayout.removeAllViews()
        label(contentLayout, "TEMPORIZADOR", 22f)
        label(contentLayout, "Cuenta regresiva independiente", 13f)
        timerText = label(contentLayout, formatDuration(remainingMs), 38f).apply {
            setTextColor(accentColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val hours = NumberPicker(this).apply { minValue = 0; maxValue = 23; value = (timerDurationMs / 3600000L).toInt() }
        val minutes = NumberPicker(this).apply { minValue = 0; maxValue = 59; value = ((timerDurationMs / 60000L) % 60).toInt() }
        val seconds = NumberPicker(this).apply { minValue = 0; maxValue = 59; value = 0 }
        val pickers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(hours, LinearLayout.LayoutParams(0, dp(110), 1f))
            addView(minutes, LinearLayout.LayoutParams(0, dp(110), 1f))
            addView(seconds, LinearLayout.LayoutParams(0, dp(110), 1f))
        }
        contentLayout.addView(pickers)
        label(contentLayout, "Horas       Minutos       Segundos", 11f)
        contentLayout.addView(makeButton("INICIAR / REANUDAR") {
            if (!timerRunning) {
                if (countdown == null || remainingMs <= 0L) {
                    timerDurationMs = hours.value * 3600000L + minutes.value * 60000L + seconds.value * 1000L
                    remainingMs = timerDurationMs
                }
                if (remainingMs <= 0L) {
                    Toast.makeText(this, "Selecciona una duración mayor que cero.", Toast.LENGTH_SHORT).show()
                } else startCountdown()
            }
        })
        contentLayout.addView(makeButton("PAUSAR") { pauseCountdown() })
        contentLayout.addView(makeButton("CANCELAR / REINICIAR") {
            countdown?.cancel()
            countdown = null
            timerRunning = false
            remainingMs = timerDurationMs
            timerText.text = formatDuration(remainingMs)
        })
        label(contentLayout, "Al finalizar, la app avisará y cerrará el temporizador. No acciona el quemador ni una válvula de gas.", 11f)
    }

    private fun startCountdown() {
        countdown?.cancel()
        timerRunning = true
        countdown = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                if (::timerText.isInitialized) timerText.text = formatDuration(remainingMs)
            }
            override fun onFinish() {
                remainingMs = 0L
                timerRunning = false
                countdown = null
                if (::timerText.isInitialized) timerText.text = "00:00:00"
                Toast.makeText(this@MainActivity, "Temporizador terminado.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun pauseCountdown() {
        if (timerRunning) {
            countdown?.cancel()
            countdown = null
            timerRunning = false
        }
    }

    private fun formatDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        return String.format(Locale.US, "%02d:%02d:%02d",
            total / 3600L, (total % 3600L) / 60L, total % 60L)
    }

    private fun showSettings() {
        contentLayout.removeAllViews()
        label(contentLayout, "AJUSTES", 22f)
        label(contentLayout, "Carátula de la aplicación", 16f)
        val themes = arrayOf("Industrial · Azul y cian", "Minimalista · Negro y blanco", "Calorex · Rojo y blanco")
        val themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, themes)
            setSelection(themeIndex)
        }
        contentLayout.addView(themeSpinner)
        contentLayout.addView(makeButton("APLICAR Y GUARDAR CARÁTULA") {
            themeIndex = themeSpinner.selectedItemPosition
            prefs.edit().putInt("theme", themeIndex).apply()
            applyTheme()
            createShell()
            showSettings()
            Toast.makeText(this, "Carátula guardada.", Toast.LENGTH_SHORT).show()
        })
        space(contentLayout, 12)
        label(contentLayout, "Controlador ESP8266", 16f)
        contentLayout.addView(makeButton("CONFIGURAR DIRECCIÓN IP") { showIpDialog() })
        contentLayout.addView(makeButton("PROBAR CONEXIÓN") { checkConnection() })
        label(contentLayout, "IP actual: $deviceIp", 13f)
        label(contentLayout, "La agenda semanal de esta entrega se guarda localmente. La sincronización y ejecución en ESP8266 requieren actualizar también su firmware.", 12f)
    }

    private fun showIpDialog() {
        val input = EditText(this).apply {
            setText(deviceIp)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Dirección IP del ESP8266")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
                    deviceIp = ip
                    prefs.edit().putString("device_ip", ip).apply()
                    checkConnection()
                } else Toast.makeText(this, "Dirección IP inválida.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendAngle(angle: Int) {
        val safe = angle.coerceIn(0, 180)
        request("http://$deviceIp/mover?angulo=$safe") { code, _ ->
            setConnectionState(code in 200..299)
        }
    }

    private fun checkConnection() {
        request("http://$deviceIp/status") { code, body ->
            setConnectionState(code in 200..299)
            if (code in 200..299) {
                try {
                    val status = JSONObject(body)
                    val synced = status.optBoolean("clock_synced", false)
                    val controllerTime = status.optString("time", "")
                    // The large home clock remains the phone's live 12-hour clock.
                    if (!synced || controllerTime.isBlank()) {
                        statusText.text = "● Conectado · reloj ESP sin sincronizar"
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun request(urlText: String, callback: (Int, String) -> Unit) {
        executor.execute {
            var connection: HttpURLConnection? = null
            var code = -1
            var body = ""
            try {
                connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    useCaches = false
                }
                code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                if (stream != null) body = stream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                body = e.message ?: "Error de conexión"
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                setConnectionState(code in 200..299)
                callback(code, body)
            }
        }
    }

    private fun setConnectionState(connected: Boolean) {
        isConnected = connected
        if (::statusText.isInitialized) {
            statusText.text = if (connected) "● Conectado" else "● Desconectado"
            statusText.setTextColor(if (connected) successColor else Color.LTGRAY)
        }
    }

    private fun savePlans() {
        val editor = prefs.edit()
        plans.forEachIndexed { i, p ->
            editor.putBoolean("day_${i}_enabled", p.enabled)
            editor.putString("day_${i}_start", p.start)
            editor.putString("day_${i}_end", p.end)
        }
        editor.apply()
    }

    private fun loadPlans() {
        plans.forEachIndexed { i, p ->
            p.enabled = prefs.getBoolean("day_${i}_enabled", false)
            p.start = prefs.getString("day_${i}_start", p.start) ?: p.start
            p.end = prefs.getString("day_${i}_end", p.end) ?: p.end
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockRunnable)
        countdown?.cancel()
        executor.shutdownNow()
        super.onDestroy()
    }

    inner class ServoDial(context: android.content.Context) : View(context) {
        var angle = 90
        var onAngleChanged: ((Int) -> Unit)? = null
        var onAngleSelected: ((Int) -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var cx = 0f
        private var cy = 0f
        private var radius = 0f

        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            cx = width / 2f
            cy = height / 2f
            radius = min(width, height) * 0.34f
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(9).toFloat()
            paint.color = Color.rgb(65, 82, 105)
            canvas.drawArc(rect, 135f, 270f, false, paint)
            paint.color = accentColor
            canvas.drawArc(rect, 135f, angle / 180f * 270f, false, paint)

            for (v in 0..180 step 5) {
                val a = Math.toRadians((135f + v / 180f * 270f).toDouble())
                val inner = radius - dp(5)
                val outer = radius + dp(11)
                paint.color = if (v <= angle) accentColor else Color.LTGRAY
                paint.strokeWidth = if (v % 15 == 0) dp(3).toFloat() else dp(1).toFloat()
                canvas.drawLine(
                    cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                    cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer, paint
                )
            }
            val pointer = Math.toRadians((135f + angle / 180f * 270f).toDouble())
            paint.style = Paint.Style.FILL
            paint.color = accentColor
            canvas.drawCircle(cx + cos(pointer).toFloat() * radius,
                cy + sin(pointer).toFloat() * radius, dp(9).toFloat(), paint)
            paint.color = textColor
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(23).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("$angle°", cx, cy + dp(8), paint)
            paint.textSize = dp(11).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText("PRUEBA SIN CARGA", cx, cy + dp(30), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - cx
                    val dy = event.y - cy
                    var degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    if (degrees < 0) degrees += 360f
                    var normalized = degrees - 135f
                    if (normalized < 0) normalized += 360f
                    normalized = normalized.coerceIn(0f, 270f)
                    val raw = (normalized / 270f * 180f).roundToInt()
                    angle = ((raw + 2) / 5 * 5).coerceIn(0, 180)
                    onAngleChanged?.invoke(angle)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    onAngleSelected?.invoke(angle)
                    performClick()
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
