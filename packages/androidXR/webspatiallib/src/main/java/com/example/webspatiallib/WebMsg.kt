package com.example.webspatiallib

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WebMsg system for sending events from native to JavaScript.
 * Mirrors visionOS WebMsgCommand system.
 */
object WebMsg {

    private const val TAG = "WebMsg"

    private val json = Json { encodeDefaults = true }

    /**
     * Send a message to the JavaScript side.
     * The message is sent via evaluateJavascript calling a global handler.
     */
    fun send(webview: NativeWebView, objectId: String, type: String, detail: Any? = null) {
        val detailJson = when (detail) {
            null -> "{}"
            is String -> "\"$detail\""
            is Number -> detail.toString()
            is Boolean -> detail.toString()
            else -> try {
                json.encodeToString(detail as? Map<String, Any?> ?: emptyMap())
            } catch (e: Exception) {
                "{}"
            }
        }

        val message = """{
            "objectId": "$objectId",
            "type": "$type",
            "detail": $detailJson
        }"""

        sendRaw(webview, message)
    }

    /**
     * Send a raw JSON message.
     */
    fun sendRaw(webview: NativeWebView, messageJson: String) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                // Call the global WebSpatial message handler
                val script = """
                    if (window.__WebSpatialMessage) {
                        window.__WebSpatialMessage($messageJson);
                    } else {
                        console.warn('[WebSpatial] __WebSpatialMessage handler not found');
                    }
                """.trimIndent()

                webview.webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "Sent WebMsg: ${messageJson.take(100)}...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send WebMsg: ${e.message}")
            }
        }
    }

    // === Gesture Events ===

    fun sendTapEvent(
        webview: NativeWebView,
        elementId: String,
        location3D: Triple<Float, Float, Float>
    ) {
        val detail = mapOf(
            "location3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            )
        )
        send(webview, elementId, "spatialtap", detail)
    }

    fun sendDragStartEvent(
        webview: NativeWebView,
        elementId: String,
        location3D: Triple<Float, Float, Float>
    ) {
        val detail = mapOf(
            "location3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            ),
            "startLocation3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            ),
            "translation3D" to mapOf("x" to 0f, "y" to 0f, "z" to 0f)
        )
        send(webview, elementId, "spatialdragstart", detail)
    }

    fun sendDragEvent(
        webview: NativeWebView,
        elementId: String,
        location3D: Triple<Float, Float, Float>,
        startLocation3D: Triple<Float, Float, Float>,
        translation3D: Triple<Float, Float, Float>
    ) {
        val detail = mapOf(
            "location3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            ),
            "startLocation3D" to mapOf(
                "x" to startLocation3D.first,
                "y" to startLocation3D.second,
                "z" to startLocation3D.third
            ),
            "translation3D" to mapOf(
                "x" to translation3D.first,
                "y" to translation3D.second,
                "z" to translation3D.third
            )
        )
        send(webview, elementId, "spatialdrag", detail)
    }

    fun sendDragEndEvent(
        webview: NativeWebView,
        elementId: String,
        location3D: Triple<Float, Float, Float>,
        translation3D: Triple<Float, Float, Float>
    ) {
        val detail = mapOf(
            "location3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            ),
            "translation3D" to mapOf(
                "x" to translation3D.first,
                "y" to translation3D.second,
                "z" to translation3D.third
            )
        )
        send(webview, elementId, "spatialdragend", detail)
    }

    fun sendRotateEvent(
        webview: NativeWebView,
        elementId: String,
        type: String, // "spatialrotatestart", "spatialrotate", "spatialrotateend"
        rotationRadians: Float,
        location3D: Triple<Float, Float, Float>
    ) {
        val detail = mapOf(
            "rotation" to rotationRadians,
            "location3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            )
        )
        send(webview, elementId, type, detail)
    }

    fun sendMagnifyEvent(
        webview: NativeWebView,
        elementId: String,
        type: String, // "spatialmagnifystart", "spatialmagnify", "spatialmagnifyend"
        magnification: Float,
        location3D: Triple<Float, Float, Float>
    ) {
        val detail = mapOf(
            "magnification" to magnification,
            "location3D" to mapOf(
                "x" to location3D.first,
                "y" to location3D.second,
                "z" to location3D.third
            )
        )
        send(webview, elementId, type, detail)
    }

    // === Lifecycle Events ===

    fun sendObjectDestroy(webview: NativeWebView, objectId: String) {
        send(webview, objectId, "objectdestroy", null)
    }

    fun sendModelLoaded(webview: NativeWebView, elementId: String) {
        send(webview, elementId, "modelload", mapOf("success" to true))
    }

    fun sendModelLoadError(webview: NativeWebView, elementId: String, error: String) {
        send(webview, elementId, "modelload", mapOf("success" to false, "error" to error))
    }

    fun sendSceneStateChange(webview: NativeWebView, sceneId: String, state: String) {
        send(webview, sceneId, "scenestate", mapOf("state" to state))
    }
}
