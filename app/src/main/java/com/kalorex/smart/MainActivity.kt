
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        checkConnection()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun createInterface() {

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }

        // ENCABEZADO

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

        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        statusText = TextView(this).apply {
            text = "● Desconectado"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        header.addView(statusText)

        val ipButton = makeButton("IP") {
            showIpDialog()
        }

        header.addView(ipButton)

        mainLayout.addView(header)

        // CONTENIDO PRINCIPAL

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        mainLayout.addView(
            contentLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // NAVEGACION INFERIOR

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
            makeButton("◉\nControl") { showControl() },
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

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {

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

    // ==========================================
    // HOME
    // ==========================================

    private fun showHome() {

        contentLayout.removeAllViews()

        addLabel(contentLayout, "CONTROL DE TEMPERATURA", 15f)

        addLabel(contentLayout, "Día", 20f)

        angleText = addLabel(
            contentLayout,
            "$currentAngle°",
            34f
        ).apply {
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        addLabel(contentLayout, "Paso de selección: 5°", 13f)

        // TEMPERATURA

        addLabel(contentLayout, "Temperatura", 20f)

        temperatureText = addLabel(
            contentLayout,
            "$selectedTemperature °C",
            24f
        ).apply {
            setTextColor(cyan)
        }

        val temperatures = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        for (temp in 10..80 step 10) {

            val button = Button(this).apply {
                text = "$temp"
                textSize = 11f
                isAllCaps = false
                setTextColor(
                    if (temp == selectedTemperature) cyan else white
                )
                setBackgroundColor(
                    if (temp == selectedTemperature)
                        Color.rgb(20, 65, 100)
                    else cardColor
                )

                setOnClickListener {
                    selectedTemperature = temp
                    temperatureText.text = "$temp °C"
                    showHome()
                }
            }

            temperatures.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f
                )
            )
        }

        contentLayout.addView(temperatures)

        // ENCENDIDO / APAGADO

        val powerButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val offButton = Button(this).apply {
            text = "⏻ Apagado"
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(
                if (!powerOn) Color.DKGRAY else cardColor
            )

            setOnClickListener {
                powerOn = false
                showHome()
                Toast.makeText(
                    this@MainActivity,
                    "Modo apagado seleccionado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val onButton = Button(this).apply {
            text = "⏻ Encendido"
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(
                if (powerOn) green else cardColor
            )

            setOnClickListener {
                powerOn = true
                showHome()
                Toast.makeText(
                    this@MainActivity,
                    "Modo encendido seleccionado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        powerButtons.addView(
            offButton,
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        powerButtons.addView(
            onButton,
            LinearLayout.LayoutParams(0, dp(55), 1f)
        )

        contentLayout.addView(powerButtons)
    }

    // ==========================================
    // CONTROL
    // ==========================================

    private fun showControl() {

        contentLayout.removeAllViews()

        addLabel(contentLayout, "CONTROL MANUAL", 22f)

        addLabel(
            contentLayout,
            "Posición solicitada: $currentAngle°",
            20f
        )

        val minus = makeButton("− 5°") {
            changeAngle(-5)
        }

        val plus = makeButton("+ 5°") {
            changeAngle(5)
        }

        contentLayout.addView(minus)
        contentLayout.addView(plus)

        addLabel(
            contentLayout,
            "Temperatura seleccionada: $selectedTemperature °C"
        )

        val send = makeButton("ENVIAR POSICIÓN") {
            sendAngle(currentAngle)
        }

        contentLayout.addView(send)
    }

    private fun changeAngle(amount: Int) {

        currentAngle = (currentAngle + amount).coerceIn(0, 180)

        sendAngle(currentAngle)

        showControl()
    }

    // ==========================================
    // AJUSTES
    // ==========================================

    private fun showSettings() {

        contentLayout.removeAllViews()

        addLabel(contentLayout, "AJUSTES", 23f)

        addLabel(
            contentLayout,
            "Dirección del ESP8266"
        )

        contentLayout.addView(
            makeButton("Configurar dirección IP") {
                showIpDialog()
            }
        )

        contentLayout.addView(
            makeButton("Probar conexión") {
                checkConnection()
            }
        )

        contentLayout.addView(
            makeButton("Consultar estado") {
                checkConnection()
            }
        )

        addLabel(
            contentLayout,
            "IP actual: $deviceIp",
            14f
        )
    }

    // ==========================================
    // CONFIGURAR IP
    // ==========================================

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

                if (newIp.matches(
                        Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
                    )
                ) {
                    deviceIp = newIp
                    checkConnection()
                    Toast.makeText(
                        this,
                        "Dirección guardada",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Dirección IP inválida",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ==========================================
    // COMUNICACION CON ESP8266
    // ==========================================

    private fun sendAngle(angle: Int) {

        val safeAngle = angle.coerceIn(0, 180)

        executor.execute {

            var connection: HttpURLConnection? = null

            try {

                val url = URL(
                    "http://$deviceIp/mover?angulo=$safeAngle"
                )

                connection = url.openConnection()
                    as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.useCaches = false

                val responseCode = connection.responseCode

                runOnUiThread {

                    if (responseCode in 200..299) {
                        setConnectionState(true)
                    } else {
                        setConnectionState(false)
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    setConnectionState(false)
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun checkConnection() {

        executor.execute {

            var connection: HttpURLConnection? = null

            try {

                val url = URL("http://$deviceIp/status")

                connection = url.openConnection()
                    as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.useCaches = false

                val responseCode = connection.responseCode

                runOnUiThread {
                    setConnectionState(responseCode in 200..299)
                }

            } catch (e: Exception) {

                runOnUiThread {
                    setConnectionState(false)
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun setConnectionState(connected: Boolean) {

        isConnected = connected

        if (::statusText.isInitialized) {

            statusText.text =
                if (connected) "● Conectado"
                else "● Desconectado"

            statusText.setTextColor(
                if (connected) green else Color.LTGRAY
            )
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // ==========================================
    // DIAL PERSONALIZADO
    // ==========================================

    inner class ServoDial(context: android.content.Context) :
        View(context) {

        var angle = 90
        var onAngleChanged: ((Int) -> Unit)? = null
        var onAngleSelected: ((Int) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var centerX = 0f
        private var centerY = 0f
        private var radius = 0f

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            centerX = width / 2f
            centerY = height / 2f

            radius = min(width, height) * 0.36f

            val rect = RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )

            // ARCO DE FONDO

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(9).toFloat()
            paint.color = Color.rgb(55, 80, 115)

            canvas.drawArc(
                rect,
                135f,
                270f,
                false,
                paint
            )

            // ARCO ACTIVO

            paint.color = cyan

            canvas.drawArc(
                rect,
                135f,
                angle / 180f * 270f,
                false,
                paint
            )

            // MARCAS CADA 5 GRADOS

            for (value in 0..180 step 5) {

                val degrees = 135f + value / 180f * 270f
                val radians = Math.toRadians(degrees.toDouble())

                val outer = radius + dp(12)
                val inner = radius - dp(5)

                val x1 = centerX + cos(radians).toFloat() * inner
                val y1 = centerY + sin(radians).toFloat() * inner

                val x2 = centerX + cos(radians).toFloat() * outer
                val y2 = centerY + sin(radians).toFloat() * outer

                paint.color =
                    if (value <= angle) cyan else Color.LTGRAY

                paint.strokeWidth =
                    if (value % 15 == 0) dp(3).toFloat()
                    else dp(1).toFloat()

                canvas.drawLine(x1, y1, x2, y2, paint)

                if (value % 15 == 0) {

                    paint.style = Paint.Style.FILL
                    paint.textSize = dp(11).toFloat()
                    paint.textAlign = Paint.Align.CENTER

                    val labelRadius = radius + dp(31)

                    val tx = centerX +
                        cos(radians).toFloat() * labelRadius

                    val ty = centerY +
                        sin(radians).toFloat() * labelRadius +
                        dp(4)

                    canvas.drawText(
                        "$value°",
                        tx,
                        ty,
                        paint
                    )

                    paint.style = Paint.Style.STROKE
                }
            }

            // SELECTOR

            val pointerDegrees = 135f + angle / 180f * 270f
            val pointerRadians =
                Math.toRadians(pointerDegrees.toDouble())

            val px = centerX +
                cos(pointerRadians).toFloat() * radius

            val py = centerY +
                sin(pointerRadians).toFloat() * radius

            paint.style = Paint.Style.FILL
            paint.color = cyan

            canvas.drawCircle(
                px,
                py,
                dp(10).toFloat(),
                paint
            )

            // VALOR CENTRAL

            paint.color = white
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(24).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

            canvas.drawText(
                "$angle°",
                centerX,
                centerY + dp(8),
                paint
            )

            paint.textSize = dp(12).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT

            canvas.drawText(
                "PASO 5°",
                centerX,
                centerY + dp(32),
                paint
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {

            when (event.action) {

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {

                    val dx = event.x - centerX
                    val dy = event.y - centerY

                    var degrees = Math.toDegrees(
                        atan2(dy, dx).toDouble()
                    ).toFloat()

                    if (degrees < 0) degrees += 360f

                    var normalized = degrees - 135f

                    if (normalized < 0) {
                        normalized += 360f
                    }

                    normalized = normalized.coerceIn(0f, 270f)

                    val rawAngle =
                        (normalized / 270f * 180f).roundToInt()

                    val snappedAngle =
                        ((rawAngle + 2) / 5 * 5).coerceIn(0, 180)

                    angle = snappedAngle

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
