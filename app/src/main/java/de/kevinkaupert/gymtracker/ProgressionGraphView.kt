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
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#404040")
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var sessionData: Map<String, List<Double>> = emptyMap()
    
    private val setColors = intArrayOf(
        Color.parseColor("#4D94FF"), // Modern Blue
        Color.parseColor("#4ade80"), // Modern Green
        Color.parseColor("#fbbf24"), // Amber
        Color.parseColor("#f87171"), // Red
        Color.parseColor("#c084fc"), // Purple
        Color.parseColor("#22d3ee")  // Cyan
    )

    fun setSessionData(data: Map<String, List<Double>>) {
        this.sessionData = data.toSortedMap()
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
        val minVal = (allValues.minOrNull() ?: 0.0) * 0.9
        val maxVal = (allValues.maxOrNull() ?: 100.0) * 1.1
        val range = (maxVal - minVal).coerceAtLeast(10.0)

        val dates = sessionData.keys.toList()
        val xStep = if (dates.size > 1) graphWidth / (dates.size - 1) else graphWidth / 2

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
            val shortDate = date.substring(5) // MM-DD
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
}
