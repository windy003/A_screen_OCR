package com.screenocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val imgMatrix = Matrix()
    private val savedMatrix = Matrix()

    private val matrixValues = FloatArray(9)

    private var minScale = 0.5f
    private var maxScale = 5f

    private var origWidth = 0f
    private var origHeight = 0f
    private var viewWidth = 0
    private var viewHeight = 0
    private var fitted = false

    var onTapListener: ((Float, Float) -> Unit)? = null

    // Listener to sync overlay with image matrix
    var onMatrixChangeListener: ((Matrix) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val currentScale = getCurrentScale()
                val newScale = currentScale * scaleFactor

                if (newScale in minScale..maxScale) {
                    imgMatrix.postScale(
                        scaleFactor, scaleFactor,
                        detector.focusX, detector.focusY
                    )
                    imageMatrix = imgMatrix
                    onMatrixChangeListener?.invoke(imgMatrix)
                }
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Convert screen coordinates to image coordinates
                val inverse = Matrix()
                imgMatrix.invert(inverse)
                val pts = floatArrayOf(e.x, e.y)
                inverse.mapPoints(pts)
                onTapListener?.invoke(pts[0], pts[1])
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                imgMatrix.postTranslate(-distanceX, -distanceY)
                imageMatrix = imgMatrix
                onMatrixChangeListener?.invoke(imgMatrix)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val currentScale = getCurrentScale()
                if (currentScale > 1.5f) {
                    fitToView()
                } else {
                    imgMatrix.postScale(2f, 2f, e.x, e.y)
                    imageMatrix = imgMatrix
                    onMatrixChangeListener?.invoke(imgMatrix)
                }
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        if (!fitted) fitToView()
    }

    fun fitToView() {
        val drawable = drawable ?: return
        origWidth = drawable.intrinsicWidth.toFloat()
        origHeight = drawable.intrinsicHeight.toFloat()

        if (origWidth == 0f || origHeight == 0f) return

        val scaleX = viewWidth / origWidth
        val scaleY = viewHeight / origHeight
        val scale = minOf(scaleX, scaleY)

        imgMatrix.reset()
        imgMatrix.postScale(scale, scale)
        imgMatrix.postTranslate(
            (viewWidth - origWidth * scale) / 2f,
            (viewHeight - origHeight * scale) / 2f
        )

        imageMatrix = imgMatrix
        onMatrixChangeListener?.invoke(imgMatrix)
        fitted = true
    }

    private fun getCurrentScale(): Float {
        imgMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    fun getImageMatrix2(): Matrix = Matrix(imgMatrix)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}
