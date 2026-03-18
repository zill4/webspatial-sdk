package com.example.webspatiallib

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.lang.ref.WeakReference

/**
 * Gesture handler for spatial elements.
 * Routes touch/pointer events to the appropriate spatial element and sends events to JavaScript.
 *
 * Implements the same gesture types as visionOS:
 * - Tap: Single tap on an element
 * - Drag: Pan/move gesture with start, move, end phases
 * - Rotate: Two-finger rotation gesture
 * - Magnify: Pinch-to-zoom gesture
 */
class GestureHandler(
    private val webviewRef: WeakReference<NativeWebView>
) {
    companion object {
        private const val TAG = "GestureHandler"
    }

    // Drag state tracking
    private var isDragging = false
    private var dragTargetId: String? = null
    private var dragStartLocation: Triple<Float, Float, Float>? = null
    private var lastDragLocation: Triple<Float, Float, Float>? = null

    // Rotate state tracking
    private var isRotating = false
    private var rotateTargetId: String? = null
    private var lastRotationAngle: Float = 0f
    private var rotationStartAngle: Float = 0f

    // Magnify state tracking
    private var isMagnifying = false
    private var magnifyTargetId: String? = null
    private var lastMagnification: Float = 1f

    /**
     * Handle a tap gesture at the given 3D location.
     */
    fun handleTap(elementId: String, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return
        val element = SpatialObject.get(elementId) as? Spatialized2DElement

        if (element?.enableTapGesture == true) {
            WebMsg.sendTapEvent(webview, elementId, location3D)
        }
    }

    /**
     * Handle drag gesture start.
     */
    fun handleDragStart(elementId: String, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return
        val element = SpatialObject.get(elementId) as? Spatialized2DElement

        if (element?.enableDragStartGesture == true || element?.enableDragGesture == true) {
            isDragging = true
            dragTargetId = elementId
            dragStartLocation = location3D
            lastDragLocation = location3D

            WebMsg.sendDragStartEvent(webview, elementId, location3D)
        }
    }

    /**
     * Handle drag gesture update.
     */
    fun handleDrag(elementId: String, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return

        if (!isDragging || dragTargetId != elementId) {
            // If not already dragging this element, start a drag
            handleDragStart(elementId, location3D)
            return
        }

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element?.enableDragGesture == true) {
            val startLoc = dragStartLocation ?: location3D
            val translation = Triple(
                location3D.first - startLoc.first,
                location3D.second - startLoc.second,
                location3D.third - startLoc.third
            )

            WebMsg.sendDragEvent(webview, elementId, location3D, startLoc, translation)
            lastDragLocation = location3D
        }
    }

    /**
     * Handle drag gesture end.
     */
    fun handleDragEnd(elementId: String, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return

        if (!isDragging || dragTargetId != elementId) return

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element?.enableDragEndGesture == true || element?.enableDragGesture == true) {
            val startLoc = dragStartLocation ?: location3D
            val translation = Triple(
                location3D.first - startLoc.first,
                location3D.second - startLoc.second,
                location3D.third - startLoc.third
            )

            WebMsg.sendDragEndEvent(webview, elementId, location3D, translation)
        }

        // Reset drag state
        isDragging = false
        dragTargetId = null
        dragStartLocation = null
        lastDragLocation = null
    }

    /**
     * Handle rotate gesture start.
     */
    fun handleRotateStart(elementId: String, rotation: Float, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return
        val element = SpatialObject.get(elementId) as? Spatialized2DElement

        if (element?.enableRotateStartGesture == true || element?.enableRotateGesture == true) {
            isRotating = true
            rotateTargetId = elementId
            rotationStartAngle = rotation
            lastRotationAngle = rotation

            WebMsg.sendRotateEvent(webview, elementId, "spatialrotatestart", rotation, location3D)
        }
    }

    /**
     * Handle rotate gesture update.
     */
    fun handleRotate(elementId: String, rotation: Float, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return

        if (!isRotating || rotateTargetId != elementId) {
            handleRotateStart(elementId, rotation, location3D)
            return
        }

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element?.enableRotateGesture == true) {
            // Send delta rotation since start
            val deltaRotation = rotation - rotationStartAngle

            WebMsg.sendRotateEvent(webview, elementId, "spatialrotate", deltaRotation, location3D)
            lastRotationAngle = rotation
        }
    }

    /**
     * Handle rotate gesture end.
     */
    fun handleRotateEnd(elementId: String, rotation: Float, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return

        if (!isRotating || rotateTargetId != elementId) return

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element?.enableRotateEndGesture == true || element?.enableRotateGesture == true) {
            val deltaRotation = rotation - rotationStartAngle

            WebMsg.sendRotateEvent(webview, elementId, "spatialrotateend", deltaRotation, location3D)
        }

        // Reset rotate state
        isRotating = false
        rotateTargetId = null
        rotationStartAngle = 0f
        lastRotationAngle = 0f
    }

    /**
     * Handle magnify (pinch) gesture start.
     */
    fun handleMagnifyStart(elementId: String, magnification: Float, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return
        val element = SpatialObject.get(elementId) as? Spatialized2DElement

        if (element?.enableMagnifyStartGesture == true || element?.enableMagnifyGesture == true) {
            isMagnifying = true
            magnifyTargetId = elementId
            lastMagnification = magnification

            WebMsg.sendMagnifyEvent(webview, elementId, "spatialmagnifystart", magnification, location3D)
        }
    }

    /**
     * Handle magnify (pinch) gesture update.
     */
    fun handleMagnify(elementId: String, magnification: Float, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return

        if (!isMagnifying || magnifyTargetId != elementId) {
            handleMagnifyStart(elementId, magnification, location3D)
            return
        }

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element?.enableMagnifyGesture == true) {
            WebMsg.sendMagnifyEvent(webview, elementId, "spatialmagnify", magnification, location3D)
            lastMagnification = magnification
        }
    }

    /**
     * Handle magnify (pinch) gesture end.
     */
    fun handleMagnifyEnd(elementId: String, magnification: Float, location3D: Triple<Float, Float, Float>) {
        val webview = webviewRef.get() ?: return

        if (!isMagnifying || magnifyTargetId != elementId) return

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element?.enableMagnifyEndGesture == true || element?.enableMagnifyGesture == true) {
            WebMsg.sendMagnifyEvent(webview, elementId, "spatialmagnifyend", magnification, location3D)
        }

        // Reset magnify state
        isMagnifying = false
        magnifyTargetId = null
        lastMagnification = 1f
    }

    /**
     * Cancel any ongoing gestures.
     */
    fun cancelAllGestures() {
        if (isDragging && dragTargetId != null) {
            val webview = webviewRef.get()
            val lastLoc = lastDragLocation ?: Triple(0f, 0f, 0f)
            if (webview != null) {
                handleDragEnd(dragTargetId!!, lastLoc)
            }
        }

        if (isRotating && rotateTargetId != null) {
            val webview = webviewRef.get()
            if (webview != null) {
                handleRotateEnd(rotateTargetId!!, lastRotationAngle, Triple(0f, 0f, 0f))
            }
        }

        if (isMagnifying && magnifyTargetId != null) {
            val webview = webviewRef.get()
            if (webview != null) {
                handleMagnifyEnd(magnifyTargetId!!, lastMagnification, Triple(0f, 0f, 0f))
            }
        }

        // Reset all state
        isDragging = false
        dragTargetId = null
        dragStartLocation = null
        lastDragLocation = null
        isRotating = false
        rotateTargetId = null
        rotationStartAngle = 0f
        lastRotationAngle = 0f
        isMagnifying = false
        magnifyTargetId = null
        lastMagnification = 1f
    }
}

/**
 * Extension functions for creating Point3D from various sources.
 */
object GestureUtils {

    /**
     * Convert screen coordinates to 3D space coordinates.
     * This is a simplified version - actual implementation depends on
     * the camera/projection setup in Android XR.
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param depth Estimated depth (Z coordinate)
     * @param panelWidth Width of the panel in pixels
     * @param panelHeight Height of the panel in pixels
     * @return 3D location in meters
     */
    fun screenTo3D(
        screenX: Float,
        screenY: Float,
        depth: Float = 0f,
        panelWidth: Float = 1920f,
        panelHeight: Float = 1080f
    ): Triple<Float, Float, Float> {
        // Normalize to -1 to 1 range, then scale to meters
        // Assuming 1 meter = 1000 pixels at z=0
        val normalizedX = (screenX / panelWidth - 0.5f) * 2f
        val normalizedY = -(screenY / panelHeight - 0.5f) * 2f // Flip Y

        // Convert to meters (assuming panel is ~1 meter wide at z=0)
        val metersX = normalizedX * 0.5f
        val metersY = normalizedY * 0.5f * (panelHeight / panelWidth)

        return Triple(metersX, metersY, depth)
    }

    /**
     * Find the element at a given screen position.
     * Returns the element ID if found, null otherwise.
     */
    fun hitTest(
        screenX: Float,
        screenY: Float,
        scene: SpatialScene
    ): String? {
        // Check elements in reverse depth order (front to back)
        val sortedElements = scene.getElementsSortedByDepth().reversed()

        for (element in sortedElements) {
            if (!element.visible) continue

            // Check if point is within element bounds
            if (screenX >= element.clientX &&
                screenX <= element.clientX + element.width &&
                screenY >= element.clientY &&
                screenY <= element.clientY + element.height) {
                return element.id
            }
        }

        return null
    }
}
