package com.example.webspatiallib

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import java.lang.ref.WeakReference
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SpatialWindowComponent(context: Context) : SpatialComponent() {
    companion object {
        var mountIdCounter = 1
        private const val TAG = "SpatialWindowComponent"
    }

    val nativeWebView = NativeWebView(context)

    var mountedId by mutableIntStateOf(0)
    var backgroundStyle by mutableStateOf("none")

    init {
        nativeWebView.windowComponent = WeakReference(this)

        // Set up callback for webspatial:// URL handling
        nativeWebView.spatialElementCreatedCallback = object : SpatialElementCreatedCallback {
            override fun onSpatialElementCreated(elementId: String, command: String, params: Map<String, String>) {
                Log.i("WebSpatial", "=== onSpatialElementCreated CALLED ===")
                Log.i("WebSpatial", "elementId=$elementId, command=$command, params=$params")

                try {
                    when (command) {
                        "createSpatialized2DElement" -> {
                            // Create a Spatialized2DElement with the pre-generated ID
                            // The SDK will send UpdateSpatialized2DElementProperties later with actual properties
                            Log.i("WebSpatial", "Creating Spatialized2DElement with id=$elementId")
                            val element = Spatialized2DElement(WeakReference(nativeWebView), elementId)
                            Log.i("WebSpatial", "Created Spatialized2DElement: ${element.id}, registered in objects: ${SpatialObject.get(elementId) != null}")
                        }
                        "createSpatializedStatic3DElement" -> {
                            val modelURL = params["modelURL"] ?: ""
                            val element = SpatializedStatic3DElement(modelURL, elementId)
                            Log.i("WebSpatial", "Created SpatializedStatic3DElement: $elementId, modelURL=$modelURL")
                        }
                        "createSpatializedDynamic3DElement" -> {
                            val element = SpatializedDynamic3DElement(elementId)
                            Log.i("WebSpatial", "Created SpatializedDynamic3DElement: $elementId")
                        }
                        else -> {
                            Log.w("WebSpatial", "Unknown webspatial command: $command")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSpatial", "ERROR in onSpatialElementCreated: ${e.message}", e)
                }
            }
        }
    }

    fun loadURL(url: String) {
        nativeWebView.navigateToURL(url)
    }
}