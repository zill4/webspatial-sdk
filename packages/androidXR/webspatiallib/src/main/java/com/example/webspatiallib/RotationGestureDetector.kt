package com.example.webspatiallib

import android.view.MotionEvent
import kotlin.math.atan2

/**
 * Custom rotation gesture detector for two-finger rotation.
 * Android doesn't have a built-in rotation gesture detector, so we implement our own.
 */
class RotationGestureDetector(
    private val listener: OnRotationGestureListener
) {
    interface OnRotationGestureListener {
        fun onRotationBegin(detector: RotationGestureDetector): Boolean
        fun onRotation(detector: RotationGestureDetector): Boolean
        fun onRotationEnd(detector: RotationGestureDetector)
    }

    /**
     * Simple listener implementation with empty default methods.
     */
    open class SimpleOnRotationGestureListener : OnRotationGestureListener {
        override fun onRotationBegin(detector: RotationGestureDetector): Boolean = true
        override fun onRotation(detector: RotationGestureDetector): Boolean = true
        override fun onRotationEnd(detector: RotationGestureDetector) {}
    }

    // Current rotation angle in radians
    var rotation: Float = 0f
        private set

    // Focus point (center between two fingers)
    var focusX: Float = 0f
        private set
    var focusY: Float = 0f
        private set

    // Whether rotation is in progress
    var isInProgress: Boolean = false
        private set

    // Previous angle for calculating delta
    private var previousAngle: Float = 0f
    private var initialAngle: Float = 0f

    /**
     * Process a motion event and detect rotation gestures.
     * @return true if the event was consumed
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        // Rotation requires exactly 2 pointers
        if (event.pointerCount != 2) {
            if (isInProgress) {
                isInProgress = false
                listener.onRotationEnd(this)
            }
            return false
        }

        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)

        // Calculate focus point (center between fingers)
        focusX = (x0 + x1) / 2f
        focusY = (y0 + y1) / 2f

        // Calculate angle between the two fingers
        val currentAngle = calculateAngle(x0, y0, x1, y1)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down - start rotation
                if (event.pointerCount == 2) {
                    initialAngle = currentAngle
                    previousAngle = currentAngle
                    rotation = 0f
                    isInProgress = listener.onRotationBegin(this)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isInProgress) {
                    // Calculate rotation delta
                    val deltaAngle = currentAngle - previousAngle
                    rotation += deltaAngle

                    // Notify listener
                    listener.onRotation(this)

                    previousAngle = currentAngle
                }
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isInProgress) {
                    isInProgress = false
                    listener.onRotationEnd(this)
                }
            }
        }

        return isInProgress
    }

    /**
     * Calculate the angle between two points in radians.
     */
    private fun calculateAngle(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        return atan2(dy, dx)
    }

    /**
     * Get the cumulative rotation since the gesture started, in radians.
     */
    fun getRotationRadians(): Float = rotation

    /**
     * Get the cumulative rotation since the gesture started, in degrees.
     */
    fun getRotationDegrees(): Float = Math.toDegrees(rotation.toDouble()).toFloat()
}
