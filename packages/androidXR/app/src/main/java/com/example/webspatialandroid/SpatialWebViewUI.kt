package com.example.webspatialandroid

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.example.webspatiallib.GestureHandler
import com.example.webspatiallib.GestureUtils
import com.example.webspatiallib.RotationGestureDetector
import com.example.webspatiallib.SpatialScene
import com.example.webspatiallib.SpatialWindowComponent
import java.lang.ref.WeakReference

val transparentColor = Color.Transparent
val glassColor = Color.Gray.copy(0.5f)
val standardColor = Color.White

@Composable
fun SpatialWebViewUI(swc: SpatialWindowComponent, modifier: Modifier = Modifier) {
    val id = remember { ++SpatialWindowComponent.mountIdCounter }
    val bgColor = remember { mutableStateOf(standardColor) }

    // Create gesture handler for this webview
    val gestureHandler = remember {
        GestureHandler(WeakReference(swc.nativeWebView))
    }

    // Cleanup on dispose
    DisposableEffect(swc) {
        onDispose {
            gestureHandler.cancelAllGestures()
        }
    }

    Box(modifier = modifier) {
        // Since the androidView doesn't seem to get destroyed right away we need to remove the webview from its parent before adding it in its new UI
        // Without this we can get a crash when switching from home to full space modes
        if (swc.mountedId == 0 || swc.mountedId == id) {
            swc.mountedId = id
            AndroidView(
                modifier = Modifier
                    .background(bgColor.value)
                    .align(Alignment.TopStart),
                factory = { ctx ->
                    // Set up gesture detectors on the WebView
                    val webView = swc.nativeWebView.webView

                    // Standard gesture detector for tap and drag
                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            handleTapAtPosition(swc, gestureHandler, e.x, e.y)
                            return true
                        }

                        override fun onScroll(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            distanceX: Float,
                            distanceY: Float
                        ): Boolean {
                            // Handle drag gestures
                            handleDragAtPosition(swc, gestureHandler, e2.x, e2.y, e1?.x ?: e2.x, e1?.y ?: e2.y)
                            return true
                        }
                    })

                    // Scale gesture detector for pinch/magnify
                    val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        private var currentElementId: String? = null

                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            val scene = getSceneForWebView(swc)
                            if (scene != null) {
                                currentElementId = GestureUtils.hitTest(detector.focusX, detector.focusY, scene)
                                if (currentElementId != null) {
                                    val location3D = GestureUtils.screenTo3D(
                                        detector.focusX, detector.focusY, 0f,
                                        webView.width.toFloat(), webView.height.toFloat()
                                    )
                                    gestureHandler.handleMagnifyStart(currentElementId!!, detector.scaleFactor, location3D)
                                }
                            }
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            currentElementId?.let { elementId ->
                                val location3D = GestureUtils.screenTo3D(
                                    detector.focusX, detector.focusY, 0f,
                                    webView.width.toFloat(), webView.height.toFloat()
                                )
                                gestureHandler.handleMagnify(elementId, detector.scaleFactor, location3D)
                            }
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            currentElementId?.let { elementId ->
                                val location3D = GestureUtils.screenTo3D(
                                    detector.focusX, detector.focusY, 0f,
                                    webView.width.toFloat(), webView.height.toFloat()
                                )
                                gestureHandler.handleMagnifyEnd(elementId, detector.scaleFactor, location3D)
                            }
                            currentElementId = null
                        }
                    })

                    // Custom rotation gesture detector for two-finger rotation
                    val rotationDetector = RotationGestureDetector(object : RotationGestureDetector.SimpleOnRotationGestureListener() {
                        private var currentElementId: String? = null

                        override fun onRotationBegin(detector: RotationGestureDetector): Boolean {
                            val scene = getSceneForWebView(swc)
                            if (scene != null) {
                                currentElementId = GestureUtils.hitTest(detector.focusX, detector.focusY, scene)
                                if (currentElementId != null) {
                                    val location3D = GestureUtils.screenTo3D(
                                        detector.focusX, detector.focusY, 0f,
                                        webView.width.toFloat(), webView.height.toFloat()
                                    )
                                    gestureHandler.handleRotateStart(currentElementId!!, detector.rotation, location3D)
                                    return true
                                }
                            }
                            return false
                        }

                        override fun onRotation(detector: RotationGestureDetector): Boolean {
                            currentElementId?.let { elementId ->
                                val location3D = GestureUtils.screenTo3D(
                                    detector.focusX, detector.focusY, 0f,
                                    webView.width.toFloat(), webView.height.toFloat()
                                )
                                gestureHandler.handleRotate(elementId, detector.rotation, location3D)
                            }
                            return true
                        }

                        override fun onRotationEnd(detector: RotationGestureDetector) {
                            currentElementId?.let { elementId ->
                                val location3D = GestureUtils.screenTo3D(
                                    detector.focusX, detector.focusY, 0f,
                                    webView.width.toFloat(), webView.height.toFloat()
                                )
                                gestureHandler.handleRotateEnd(elementId, detector.rotation, location3D)
                            }
                            currentElementId = null
                        }
                    })

                    webView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Store gesture detectors for touch event handling
                        // We use tags to access them in the touch listener
                        setTag(R.id.gesture_detector, gestureDetector)
                        setTag(R.id.scale_detector, scaleDetector)
                        setTag(R.id.rotation_detector, rotationDetector)
                    }
                }, update = { webView ->
                    if (swc.backgroundStyle == "none") {
                        swc.nativeWebView.webView.setBackgroundColor(standardColor.toArgb())
                        bgColor.value = standardColor
                    } else if (swc.backgroundStyle == "translucent") {
                        swc.nativeWebView.webView.setBackgroundColor(transparentColor.toArgb())
                        bgColor.value = glassColor
                    } else if (swc.backgroundStyle == "transparent") {
                        swc.nativeWebView.webView.setBackgroundColor(transparentColor.toArgb())
                        bgColor.value = transparentColor
                    }
                },
                onRelease = { view ->
                    swc.mountedId = 0
                    gestureHandler.cancelAllGestures()
                }
            )
        }
    }
}

/**
 * Handle a tap at the given screen position.
 */
private fun handleTapAtPosition(
    swc: SpatialWindowComponent,
    gestureHandler: GestureHandler,
    x: Float,
    y: Float
) {
    val scene = getSceneForWebView(swc) ?: return
    val elementId = GestureUtils.hitTest(x, y, scene) ?: return

    val webView = swc.nativeWebView.webView
    val location3D = GestureUtils.screenTo3D(
        x, y, 0f,
        webView.width.toFloat(), webView.height.toFloat()
    )

    gestureHandler.handleTap(elementId, location3D)
}

/**
 * Handle a drag at the given screen position.
 */
private fun handleDragAtPosition(
    swc: SpatialWindowComponent,
    gestureHandler: GestureHandler,
    currentX: Float,
    currentY: Float,
    startX: Float,
    startY: Float
) {
    val scene = getSceneForWebView(swc) ?: return
    val elementId = GestureUtils.hitTest(startX, startY, scene) ?: return

    val webView = swc.nativeWebView.webView
    val location3D = GestureUtils.screenTo3D(
        currentX, currentY, 0f,
        webView.width.toFloat(), webView.height.toFloat()
    )

    gestureHandler.handleDrag(elementId, location3D)
}

/**
 * Get the SpatialScene associated with a SpatialWindowComponent.
 */
private fun getSceneForWebView(swc: SpatialWindowComponent): SpatialScene? {
    // Get all scenes and find the one for this webview
    return SpatialScene.getAllScenes().find { scene ->
        scene.sourceWebView.get() == swc.nativeWebView
    }
}