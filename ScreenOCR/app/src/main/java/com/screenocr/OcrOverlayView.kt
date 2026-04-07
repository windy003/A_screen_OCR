package com.screenocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.text.Text

class OcrOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var textBlocks: List<Text.TextBlock> = emptyList()
    private var imageMatrix: Matrix = Matrix()
    private val selectedElements = mutableSetOf<Text.Element>()

    private val blockPaint = Paint().apply {
        color = Color.parseColor("#3000BCD4")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#6000BCD4")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val selectedPaint = Paint().apply {
        color = Color.parseColor("#60FFEB3B")
        style = Paint.Style.FILL
    }

    fun setTextBlocks(blocks: List<Text.TextBlock>) {
        textBlocks = blocks
        invalidate()
    }

    fun setImageMatrix(matrix: Matrix) {
        imageMatrix = Matrix(matrix)
        invalidate()
    }

    fun clearSelection() {
        selectedElements.clear()
        invalidate()
    }

    /**
     * Find element at image coordinates (x, y) and return it if found.
     */
    fun findElementAt(imgX: Float, imgY: Float): Text.Element? {
        for (block in textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    if (box.contains(imgX.toInt(), imgY.toInt())) {
                        toggleElement(element)
                        return element
                    }
                }
            }
        }
        // If no element found, try lines
        for (block in textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                if (box.contains(imgX.toInt(), imgY.toInt())) {
                    for (element in line.elements) {
                        toggleElement(element)
                    }
                    return line.elements.firstOrNull()
                }
            }
        }
        return null
    }

    private fun toggleElement(element: Text.Element) {
        if (selectedElements.contains(element)) {
            selectedElements.remove(element)
        } else {
            selectedElements.add(element)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (block in textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val rectF = RectF(box)
                    val mapped = RectF()
                    imageMatrix.mapRect(mapped, rectF)

                    if (selectedElements.contains(element)) {
                        canvas.drawRect(mapped, selectedPaint)
                    } else {
                        canvas.drawRect(mapped, blockPaint)
                    }
                    canvas.drawRect(mapped, borderPaint)
                }
            }
        }
    }
}
