package com.kalorex.demo

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.json.JSONObject

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private val backgroundColor = Color.rgb(7, 20, 43)
    private val cardColor = Color.rgb(14, 34, 64)
    private val cyan = Color.rgb(0, 220, 255)
    private val white = Color.WHITE
    private val green = Color.rgb(30, 220, 75)

    private var deviceIp = "192.168.1.188"
    private var currentAngle = 90
    private var selectedTemperature = 50
    private var isConnected = false
    private var powerOn = false

    private lateinit var statusText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var angleText: TextView
    private lateinit var dial: ServoDial
    private lateinit var mainLayout: LinearLayout
    private lateinit var contentLayout: LinearLayout

    private val dayNames = arrayOf(
        "Lunes", "Martes", "Miércoles", "Jueves",
        "Viernes", "Sábado", "Domingo"
    )

    private var selectedDay = 0
    private var selectedSlot = 0
    private var calendarRows: List<CalendarEntry> = emptyList()

    data class CalendarEntry(
        val day: Int,
        val slot: Int,
        val active: Boolean,
        val hour: String,
        val temperature: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createInterface()
        checkConnection()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createInterface() {
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "🔥 KalorexSmart"
            textSize = 24f
            setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(55), 1f))

        statusText = TextView(this).apply {
            text = "● Desconectado"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        header.addView(statusText)
        header.addView(makeButton("IP") { showIpDialog() })
        mainLayout.addView(header)

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        mainLayout.addView(
            contentLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(cardColor)
        }
        navigation.addView(
            makeButton("⌂\nHome") { showHome() },
            LinearLayout.LayoutParams(0, dp(60), 1f)
        )
        navigation.addView(
            makeButton("▦\nCalendario") { showCalendar() },
            LinearLayout.LayoutParams(0, dp(60), 1f)
        )
        navigation.addView(
            makeButton("⚙\nAjustes") { showSettings() },
            LinearLayout.LayoutParams(0, dp(60), 1f)
        )
        mainLayout.addView(navigation)

        setContentView(mainLayout)
        showHome()
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(white)
            setBackgroundColor(cardColor)
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun addLabel(
        parent: LinearLayout,
        value: String,
        size: Float = 18f
    ): TextView {
        val text = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        parent.addView(text)
        return text
    }

    private fun addSpace(parent: LinearLayout, height: Int = 8) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun showHome() {
        contentLayout.removeAllViews()
        addLabel(contentLayout, "CONTROL DE TEMPERATURA", 15f)
        addLabel(contentLayout, "Posición solicitada", 16f)

        angleText = addLabel(contentLayout, "$currentAngle°", 34f).apply {
            setTextColor(cyan)
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
                sendAngle(value)
            }
        }
        contentLayout.addView(
            dial,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        addLabel(contentLayout, "Paso de selección: 5°", 13f)
        addLabel(contentLayout, "Temperatura", 20f)
        temperatureText = addLabel(
            contentLayout, "$selectedTemperature °C", 24f
        ).apply { setTextColor(cyan) }

        val temperatures = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (temp in 30..70 step 5) {
            val button = Button(this).apply {
                text = "$temp"
                textSize = 10f
                isAllCaps = false
                setTextColor(if (temp == selectedTemperature) cyan else white)
                setBackgroundColor(
                    if (temp == selectedTemperature) Color.rgb(20, 65, 100)
                    else cardColor
                )
                setOnClickListener {
                    selectedTemperature = temp
                    temperatureText.text = "$temp °C"
                    showHome()
                }
            }
            temperatures.addView(
                button, LinearLayout.LayoutParams(0, dp(44), 1f)
            )
        }
        contentLayout.addView(temperatures)

        val powerButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val offButton = Button(this).apply {
            text = "⏻ Apagado"
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(if (!powerOn) Color.DKGRAY else cardColor)
            setOnClickListener {
                powerOn = false
                showHome()
                Toast.makeText(
                    this@MainActivity,
                    "Estado seleccionado en la aplicación. No controla el quemador.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val onButton = Button(this).apply {
            text = "⏻ Encendido"
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(if (powerOn) green else cardColor)
            setOnClickListener {
                powerOn = true
                showHome()
                Toast.makeText(
                    this@MainActivity,
                    "Estado seleccionado en la aplicación. No controla el quemador.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        powerButtons.addView(offButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        powerButtons.addView(onButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        contentLayout.addView(powerButtons)
    }

    private fun showControl() {
        contentLayout.removeAllViews()
        addLabel(contentLayout, "CONTROL MANUAL", 22f)
        addLabel(contentLayout, "Posición solicitada: $currentAngle°", 20f)
        contentLayout.addView(makeButton("− 5°") { changeAngle(-5) })
        contentLayout.addView(makeButton("+ 5°") { changeAngle(5) })
        addLabel(contentLayout, "Temperatura seleccionada: $selectedTemperature °C")
        contentLayout.addView(makeButton("ENVIAR POSICIÓN") { sendAngle(currentAngle) })
    }

    private fun changeAngle(amount: Int) {
        currentAngle = (currentAngle + amount).coerceIn(0, 180)
        sendAngle(currentAngle)
        showControl()
    }

    private fun showCalendar() {
        contentLayout.removeAllViews()
        addLabel(contentLayout, "CALENDARIO SEMANAL", 21f)
        addLabel(contentLayout, "Programa hasta 8 horarios independientes por día", 12f)

        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        addLabel(panel, "Día de la semana", 16f)
        val daySpinner = Spinner(this)
        daySpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, dayNames
        )
        daySpinner.setSelection(selectedDay)
        daySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?,
                position: Int, id: Long
            ) {
                selectedDay = position
                if (::listContainer.isInitialized) {
                    updateCalendarList(listContainer)
                }
            }
        }
        panel.addView(daySpinner)

        addLabel(panel, "Número de horario", 16f)
        val slotSpinner = Spinner(this)
        slotSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (1..8).map { "Horario $it" }
        )
        slotSpinner.setSelection(selectedSlot)
        slotSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?,
                position: Int, id: Long
            ) {
                selectedSlot = position
                loadEntryIntoEditor()
            }
        }
        panel.addView(slotSpinner)

        addLabel(panel, "Hora (formato HH:MM)", 16f)
        val timeInput = EditText(this).apply {
            tag = "timeInput"
            hint = "06:30"
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        panel.addView(timeInput)

        addLabel(panel, "AM / PM", 16f)
        val periodSpinner = Spinner(this).apply { tag = "periodSpinner" }
        periodSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, arrayOf("AM", "PM")
        )
        panel.addView(periodSpinner)

        addLabel(panel, "Temperatura", 16f)
        val tempSpinner = Spinner(this).apply { tag = "tempSpinner" }
        tempSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (30..70 step 5).map { "$it °C" }
        )
        panel.addView(tempSpinner)

        val activeCheck = CheckBox(this).apply {
            tag = "activeCheck"
            text = "Horario activado"
            setTextColor(white)
            isChecked = true
        }
        panel.addView(activeCheck)

        val saveButton = makeButton("GUARDAR HORARIO") {
            val time = timeInput.text.toString().trim()
            val temp = 30 + tempSpinner.selectedItemPosition * 5
            if (!time.matches(Regex("^(0[1-9]|1[0-2]):[0-5][0-9]$"))) {
                Toast.makeText(this, "Escribe la hora como 06:30", Toast.LENGTH_LONG).show()
            } else {
                val period = periodSpinner.selectedItem.toString()
                saveSchedule(selectedDay, selectedSlot, "$time $period", temp, activeCheck.isChecked)
            }
        }
        panel.addView(saveButton)

        panel.addView(makeButton("ELIMINAR / DESACTIVAR HORARIO") {
            deleteSchedule(selectedDay, selectedSlot)
        })

        panel.addView(makeButton("ACTUALIZAR CALENDARIO") {
            fetchCalendar()
        })

        addLabel(panel, "Horarios guardados", 18f)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        panel.addView(listContainer)

        scroll.addView(panel)
        contentLayout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        fetchCalendar()
    }

    private lateinit var listContainer: LinearLayout

    private fun loadEntryIntoEditor() {
        if (!::listContainer.isInitialized) return
        val entry = calendarRows.firstOrNull {
            it.day == selectedDay && it.slot == selectedSlot
        } ?: return
        val timeParts = entry.hour.split(" ")
        if (timeParts.size == 2) {
            val timeField = contentLayout.findViewWithTag<EditText>("timeInput")
            timeField?.setText(timeParts[0])
            val period = contentLayout.findViewWithTag<Spinner>("periodSpinner")
            period?.setSelection(if (timeParts[1] == "PM") 1 else 0)
            val temp = contentLayout.findViewWithTag<Spinner>("tempSpinner")
            temp?.setSelection(((entry.temperature - 30) / 5).coerceIn(0, 8))
            val active = contentLayout.findViewWithTag<CheckBox>("activeCheck")
            active?.isChecked = entry.active
        }
    }

    private fun updateCalendarList(container: LinearLayout) {
        container.removeAllViews()
        val entries = calendarRows.filter { it.day == selectedDay }
        if (entries.isEmpty()) {
            addLabel(container, "No hay horarios recibidos todavía.", 13f)
            return
        }
        entries.sortedBy { it.slot }.forEach { entry ->
            val line = TextView(this).apply {
                text = "Horario ${entry.slot + 1}: ${entry.hour} · ${entry.temperature} °C · " +
                    if (entry.active) "ACTIVO" else "INACTIVO"
                textSize = 14f
                setTextColor(if (entry.active) cyan else Color.LTGRAY)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    selectedSlot = entry.slot
                    loadEntryIntoEditor()
                    Toast.makeText(
                        this@MainActivity,
                        "Seleccionaste el horario ${entry.slot + 1}.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            container.addView(line)
        }
    }

    private fun saveSchedule(
        day: Int, slot: Int, hour: String, temperature: Int, active: Boolean
    ) {
        val encodedHour = URLEncoder.encode(hour, "UTF-8")
        val url = "http://$deviceIp/programar?dia=$day&slot=$slot" +
            "&hora=$encodedHour&temp=$temperature&activo=${if (active) 1 else 0}"
        request(url) { code, body ->
            if (code in 200..299) {
                Toast.makeText(this, "Horario guardado en el ESP8266.", Toast.LENGTH_LONG).show()
                fetchCalendar()
            } else {
                Toast.makeText(this, "No se pudo guardar ($code): $body", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteSchedule(day: Int, slot: Int) {
        request("http://$deviceIp/borrar?dia=$day&slot=$slot") { code, _ ->
            if (code in 200..299) {
                Toast.makeText(this, "Horario desactivado.", Toast.LENGTH_SHORT).show()
                fetchCalendar()
            } else {
                Toast.makeText(this, "No se pudo eliminar el horario.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchCalendar() {
        request("http://$deviceIp/calendario") { code, body ->
            if (code !in 200..299) {
                Toast.makeText(this, "No se pudo consultar el calendario.", Toast.LENGTH_LONG).show()
                return@request
            }
            try {
                val root = JSONObject(body)
                val array = root.getJSONArray("calendar")
                val result = mutableListOf<CalendarEntry>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    result.add(
                        CalendarEntry(
                            day = item.getInt("dia"),
                            slot = item.getInt("slot"),
                            active = item.getBoolean("activo"),
                            hour = item.getString("hora"),
                            temperature = item.getInt("temperatura")
                        )
                    )
                }
                calendarRows = result
                if (::listContainer.isInitialized) {
                    updateCalendarList(listContainer)
                    loadEntryIntoEditor()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Respuesta de calendario inválida: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSettings() {
        contentLayout.removeAllViews()
        addLabel(contentLayout, "AJUSTES", 23f)
        addLabel(contentLayout, "Dirección del ESP8266")
        contentLayout.addView(makeButton("Configurar dirección IP") { showIpDialog() })
        contentLayout.addView(makeButton("Probar conexión") { checkConnection() })
        contentLayout.addView(makeButton("Consultar estado") { checkConnection() })
        addLabel(contentLayout, "IP actual: $deviceIp", 14f)
        addLabel(contentLayout, "Zona horaria: Hermosillo (UTC−7)", 13f)
    }

    private fun showIpDialog() {
        val input = EditText(this).apply {
            setText(deviceIp)
            setSingleLine(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Dirección IP del ESP8266")
            .setMessage("Introduce la dirección local del controlador")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newIp = input.text.toString().trim()
                if (newIp.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
                    deviceIp = newIp
                    checkConnection()
                    Toast.makeText(this, "Dirección guardada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Dirección IP inválida", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendAngle(angle: Int) {
        val safeAngle = angle.coerceIn(0, 180)
        request("http://$deviceIp/mover?angulo=$safeAngle") { code, _ ->
            setConnectionState(code in 200..299)
        }
    }

    private fun checkConnection() {
        request("http://$deviceIp/status") { code, _ ->
            setConnectionState(code in 200..299)
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
            statusText.setTextColor(if (connected) green else Color.LTGRAY)
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    inner class ServoDial(context: android.content.Context) : View(context) {
        var angle = 90
        var onAngleChanged: ((Int) -> Unit)? = null
        var onAngleSelected: ((Int) -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var centerX = 0f
        private var centerY = 0f
        private var radius = 0f

        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            centerX = width / 2f
            centerY = height / 2f
            radius = min(width, height) * 0.36f
            val rect = RectF(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(9).toFloat()
            paint.color = Color.rgb(55, 80, 115)
            canvas.drawArc(rect, 135f, 270f, false, paint)
            paint.color = cyan
            canvas.drawArc(rect, 135f, angle / 180f * 270f, false, paint)

            for (value in 0..180 step 5) {
                val degrees = 135f + value / 180f * 270f
                val radians = Math.toRadians(degrees.toDouble())
                val outer = radius + dp(12)
                val inner = radius - dp(5)
                val x1 = centerX + cos(radians).toFloat() * inner
                val y1 = centerY + sin(radians).toFloat() * inner
                val x2 = centerX + cos(radians).toFloat() * outer
                val y2 = centerY + sin(radians).toFloat() * outer
                paint.color = if (value <= angle) cyan else Color.LTGRAY
                paint.strokeWidth = if (value % 15 == 0) dp(3).toFloat() else dp(1).toFloat()
                canvas.drawLine(x1, y1, x2, y2, paint)
                if (value % 15 == 0) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = dp(11).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    val labelRadius = radius + dp(31)
                    val tx = centerX + cos(radians).toFloat() * labelRadius
                    val ty = centerY + sin(radians).toFloat() * labelRadius + dp(4)
                    canvas.drawText("$value°", tx, ty, paint)
                    paint.style = Paint.Style.STROKE
                }
            }

            val pointerDegrees = 135f + angle / 180f * 270f
            val pointerRadians = Math.toRadians(pointerDegrees.toDouble())
            val px = centerX + cos(pointerRadians).toFloat() * radius
            val py = centerY + sin(pointerRadians).toFloat() * radius
            paint.style = Paint.Style.FILL
            paint.color = cyan
            canvas.drawCircle(px, py, dp(10).toFloat(), paint)
            paint.color = white
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(24).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("$angle°", centerX, centerY + dp(8), paint)
            paint.textSize = dp(12).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText("PASO 5°", centerX, centerY + dp(32), paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    var degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    if (degrees < 0) degrees += 360f
                    var normalized = degrees - 135f
                    if (normalized < 0) normalized += 360f
                    normalized = normalized.coerceIn(0f, 270f)
                    val rawAngle = (normalized / 270f * 180f).roundToInt()
                    angle = ((rawAngle + 2) / 5 * 5).coerceIn(0, 180)
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
