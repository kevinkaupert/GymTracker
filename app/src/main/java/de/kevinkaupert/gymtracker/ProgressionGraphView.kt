package de.kevinkaupert.gymtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Locale

class ProgressionGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF8FAFC.toInt() // Slate 50 (onSurface)
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF475569.toInt() // Slate 600 (outline)
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val zoneTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.RIGHT
    }

    enum class GraphMode { ONE_RM, VOLUME }
    private var currentMode = GraphMode.ONE_RM
    private var currentReference1RM: Double = 0.0

    private var sessionData: Map<String, List<Double>> = emptyMap()
    private var sessionMax1RMs: Map<String, Double> = emptyMap()
    
    private val setColors = intArrayOf(
        Color.parseColor("#06B6D4"), // Cyan 500
        Color.parseColor("#3B82F6"), // Blue 500
        Color.parseColor("#22D3EE"), // Cyan 400
        Color.parseColor("#60A5FA"), // Blue 400
        Color.parseColor("#0891B2"), // Cyan 600
        Color.parseColor("#2563EB")  // Blue 600
    )

    fun setSessionData(data: Map<String, List<Double>>, mode: GraphMode = GraphMode.ONE_RM, max1RMs: Map<String, Double> = emptyMap()) {
        this.sessionData = data.toSortedMap()
        this.currentMode = mode
        this.sessionMax1RMs = max1RMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sessionData.isEmpty()) return

        val paddingLeft = 100f
        val paddingRight = 60f
        val paddingTop = 60f
        val paddingBottom = 100f
        
        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom

        val allValues = sessionData.values.flatten()
        val minVal = (allValues.minOrNull() ?: 0.0) * 0.7
        val maxVal = (allValues.maxOrNull() ?: 100.0) * 1.3
        val range = (maxVal - minVal).coerceAtLeast(10.0)

        val dates = sessionData.keys.toList()
        val xStep = if (dates.size > 1) graphWidth / (dates.size - 1) else graphWidth / 2

        // Draw zones for 1RM
        if (currentMode == GraphMode.ONE_RM && sessionMax1RMs.isNotEmpty()) {
            drawDynamicIntensityZones(canvas, dates, xStep, paddingLeft, paddingTop, graphHeight, minVal, range)
        }

        // Draw Y axis labels & Grid
        for (i in 0..5) {
            val yVal = minVal + (range / 5 * i)
            val y = paddingTop + graphHeight - (i * graphHeight / 5)
            canvas.drawText(String.format(Locale.getDefault(), "%.0f", yVal), paddingLeft - 40f, y + 10f, textPaint)
            canvas.drawLine(paddingLeft, y, paddingLeft + graphWidth, y, gridPaint)
        }

        // Draw data per session
        dates.forEachIndexed { dateIndex, date ->
            val x = if (dates.size > 1) paddingLeft + dateIndex * xStep else paddingLeft + graphWidth / 2
            val values = sessionData[date] ?: emptyList()
            
            // Draw date label
            val shortDate = if (date.length >= 10) date.substring(5) else date // MM-DD
            canvas.drawText(shortDate, x, paddingTop + graphHeight + 60f, textPaint)

            values.forEachIndexed { setIndex, value ->
                val y = paddingTop + graphHeight - ((value - minVal) / range * graphHeight).toFloat()
                
                val color = setColors[setIndex % setColors.size]
                pointPaint.color = color
                canvas.drawCircle(x, y, 12f, pointPaint)
                
                // Draw line to next point of same set if exists
                if (dateIndex < dates.size - 1) {
                    val nextDate = dates[dateIndex + 1]
                    val nextValues = sessionData[nextDate] ?: emptyList()
                    if (setIndex < nextValues.size) {
                        val nextX = paddingLeft + (dateIndex + 1) * xStep
                        val nextY = paddingTop + graphHeight - ((nextValues[setIndex] - minVal) / range * graphHeight).toFloat()
                        linePaint.color = color
                        canvas.drawLine(x, y, nextX, nextY, linePaint)
                    }
                }
            }
        }
    }

    private fun drawDynamicIntensityZones(canvas: Canvas, dates: List<String>, xStep: Float, left: Float, top: Float, height: Float, minVal: Double, range: Double) {
        val zoneConfigs = listOf(
            Triple(0.0, 0.5, Color.parseColor("#164E63")), // Ausdauer (Cyan 900)
            Triple(0.5, 0.7, Color.parseColor("#0E7490")), // Kraftausdauer (Cyan 700)
            Triple(0.7, 0.85, Color.parseColor("#1E3A8A")), // Hypertrophie (Blue 900)
            Triple(0.85, 1.1, Color.parseColor("#1D4ED8"))  // Maximalkraft (Blue 700)
        )
        val zoneLabels = listOf("Ausdauer", "Kraftausdauer", "Hypertrophie", "Max-Kraft")

        zoneConfigs.forEachIndexed { zoneIndex, config ->
            val path = android.graphics.Path()
            var firstPoint = true

            dates.forEachIndexed { dateIndex, date ->
                val x = if (dates.size > 1) left + dateIndex * xStep else left + 50f
                val sessionMax = sessionMax1RMs[date] ?: 0.0
                
                val zStartVal = sessionMax * config.first
                val zEndVal = sessionMax * config.second
                
                val yTop = top + height - ((zEndVal - minVal) / range * height).toFloat()
                val yBottom = top + height - ((zStartVal - minVal) / range * height).toFloat()

                if (firstPoint) {
                    path.moveTo(x, yBottom)
                    path.lineTo(x, yTop)
                    firstPoint = false
                } else {
                    path.lineTo(x, yTop)
                }
            }

            // Path back to close the polygon
            for (i in dates.size - 1 downTo 0) {
                val x = if (dates.size > 1) left + i * xStep else left + 50f
                val sessionMax = sessionMax1RMs[dates[i]] ?: 0.0
                val zStartVal = sessionMax * config.first
                val yBottom = top + height - ((zStartVal - minVal) / range * height).toFloat()
                path.lineTo(x, yBottom)
            }
            path.close()

            zonePaint.color = config.third
            zonePaint.alpha = 35 // Subtile background ribbons
            canvas.drawPath(path, zonePaint)

            // Label for the zone at the last point
            if (dates.isNotEmpty()) {
                val lastDate = dates.last()
                val lastMax = sessionMax1RMs[lastDate] ?: 0.0
                val labelY = top + height - ((lastMax * (config.first + config.second) / 2.0 - minVal) / range * height).toFloat()
            zoneTextPaint.color = config.third
            zoneTextPaint.alpha = 200
            zoneTextPaint.typeface = android.graphics.Typeface.MONOSPACE
            canvas.drawText(zoneLabels[zoneIndex], left + (dates.size - 1) * xStep - 10f, labelY, zoneTextPaint)
            }
        }
    }

    private fun drawIntensityZones(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, minVal: Double, range: Double) {
        // Keep as fallback or remove if not needed. Removing to clean up.
    }
}
