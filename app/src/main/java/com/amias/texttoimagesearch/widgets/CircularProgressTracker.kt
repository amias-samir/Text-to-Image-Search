package com.amias.texttoimagesearch.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import com.amias.texttoimagesearch.R
import kotlin.math.min

class CircularProgressTracker
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rectF = RectF()

        private var progress = 0f
        private var maxProgress = 100f
        private var strokeWidth = 5f
        private var textSize = 16f
        private var showText = true

        init {
            context.withStyledAttributes(attrs, R.styleable.CircularProgressTracker) {
                backgroundPaint.color =
                    getColor(
                        R.styleable.CircularProgressTracker_backgroundColor,
                        Color.LTGRAY,
                    )
                progressPaint.color =
                    getColor(
                        R.styleable.CircularProgressTracker_progressColor,
                        Color.BLUE,
                    )
                textPaint.color =
                    getColor(
                        R.styleable.CircularProgressTracker_textColor,
                        Color.BLACK,
                    )
                strokeWidth =
                    getDimension(
                        R.styleable.CircularProgressTracker_strokeWidth,
                        strokeWidth,
                    )
                textSize =
                    getDimension(
                        R.styleable.CircularProgressTracker_textSize,
                        textSize,
                    )
                showText =
                    getBoolean(
                        R.styleable.CircularProgressTracker_showText,
                        true,
                    )
                progress = getFloat(R.styleable.CircularProgressTracker_progress, 0f)
                maxProgress = getFloat(R.styleable.CircularProgressTracker_maxProgress, 100f)
            }

            backgroundPaint.style = Paint.Style.STROKE
            backgroundPaint.strokeWidth = strokeWidth

            progressPaint.style = Paint.Style.STROKE
            progressPaint.strokeWidth = strokeWidth
            progressPaint.strokeCap = Paint.Cap.ROUND

            textPaint.textSize = textSize
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val size = min(width, height)
            val halfStroke = strokeWidth / 2
            val padding = halfStroke + 10

            // Draw background circle
            rectF.set(padding, padding, size - padding, size - padding)
            canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)

            // Draw progress arc
            val sweepAngle = 360 * (progress / maxProgress)
            canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

            // Draw progress text
            if (showText) {
                val text = "${(progress / maxProgress * 100).toInt()}%"
                val yPos = (height / 2 - (textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText(text, width / 2f, yPos, textPaint)
            }
        }

        fun setProgress(
            progress: Float,
            animate: Boolean = true,
        ) {
            val clampedProgress = progress.coerceIn(0f, maxProgress)
            if (animate) {
                ValueAnimator.ofFloat(this.progress, clampedProgress).apply {
                    duration = 1000
                    addUpdateListener {
                        this@CircularProgressTracker.progress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                this.progress = clampedProgress
                invalidate()
            }
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val size = min(width, height)
            setMeasuredDimension(size, size)
        }
    }
