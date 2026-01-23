package com.example.webspatiallib

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.lang.ref.WeakReference

/**
 * Scene state enum matching visionOS.
 */
enum class SpatialSceneState {
    IDLE,       // Initial state
    PENDING,    // Scene is being set up
    WILL_VISIBLE, // About to become visible
    VISIBLE,    // Scene is visible and active
    FAIL        // Scene setup failed
}

/**
 * Represents a spatial scene that contains spatial elements.
 * Mirrors visionOS SpatialScene.
 */
class SpatialScene(
    val sourceWebView: WeakReference<NativeWebView>
) : SpatialObject() {

    companion object {
        private const val TAG = "SpatialScene"

        // Global scene registry
        private val scenes = mutableMapOf<String, SpatialScene>()

        fun getScene(id: String): SpatialScene? = scenes[id]
        fun getAllScenes(): Collection<SpatialScene> = scenes.values
    }

    // Scene state
    var state = mutableStateOf(SpatialSceneState.VISIBLE)

    // Scene properties
    var cornerRadius: Double = 0.0
    var opacity: Double = 1.0
    var backgroundMaterial: String = "none"

    // Scene configuration
    var defaultWidth: Double = 1280.0
    var defaultHeight: Double = 800.0
    var resizable: Boolean = true
    var worldScaling: String = "automatic"
    var worldAlignment: String = "automatic"

    // Children - observable for Compose
    val spatialized2DElements = mutableStateListOf<Spatialized2DElement>()
    val static3DElements = mutableStateListOf<SpatializedStatic3DElement>()
    val dynamic3DElements = mutableStateListOf<SpatializedDynamic3DElement>()

    init {
        scenes[id] = this
        Log.d(TAG, "Created SpatialScene: $id")
    }

    /**
     * Update scene properties.
     */
    fun updateProperties(
        cornerRadius: Double? = null,
        opacity: Double? = null,
        backgroundMaterial: String? = null
    ) {
        cornerRadius?.let { this.cornerRadius = it }
        opacity?.let { this.opacity = it }
        backgroundMaterial?.let { this.backgroundMaterial = it }
        Log.d(TAG, "Updated scene $id: cornerRadius=$cornerRadius, opacity=$opacity, material=$backgroundMaterial")
    }

    /**
     * Update scene configuration.
     */
    fun updateConfig(
        defaultWidth: Double? = null,
        defaultHeight: Double? = null,
        resizable: Boolean? = null,
        worldScaling: String? = null,
        worldAlignment: String? = null
    ) {
        defaultWidth?.let { this.defaultWidth = it }
        defaultHeight?.let { this.defaultHeight = it }
        resizable?.let { this.resizable = it }
        worldScaling?.let { this.worldScaling = it }
        worldAlignment?.let { this.worldAlignment = it }
        Log.d(TAG, "Updated scene config $id: size=${this.defaultWidth}x${this.defaultHeight}")
    }

    // === Spatialized2DElement Management ===

    fun addSpatialized2DElement(element: Spatialized2DElement) {
        if (!spatialized2DElements.any { it.id == element.id }) {
            spatialized2DElements.add(element)
            element.parentScene = this
            Log.d(TAG, "Added 2D element ${element.id} to scene $id")
        }
    }

    fun removeSpatialized2DElement(elementId: String) {
        spatialized2DElements.removeAll { it.id == elementId }
        Log.d(TAG, "Removed 2D element $elementId from scene $id")
    }

    fun getSpatialized2DElement(elementId: String): Spatialized2DElement? {
        return spatialized2DElements.find { it.id == elementId }
    }

    // === Static3DElement Management ===

    fun addStatic3DElement(element: SpatializedStatic3DElement) {
        if (!static3DElements.any { it.id == element.id }) {
            static3DElements.add(element)
            element.parentScene = this
            Log.d(TAG, "Added static 3D element ${element.id} to scene $id")
        }
    }

    fun removeStatic3DElement(elementId: String) {
        static3DElements.removeAll { it.id == elementId }
        Log.d(TAG, "Removed static 3D element $elementId from scene $id")
    }

    // === Dynamic3DElement Management ===

    fun addDynamic3DElement(element: SpatializedDynamic3DElement) {
        if (!dynamic3DElements.any { it.id == element.id }) {
            dynamic3DElements.add(element)
            element.parentScene = this
            Log.d(TAG, "Added dynamic 3D element ${element.id} to scene $id")
        }
    }

    fun removeDynamic3DElement(elementId: String) {
        dynamic3DElements.removeAll { it.id == elementId }
        Log.d(TAG, "Removed dynamic 3D element $elementId from scene $id")
    }

    fun getDynamic3DElement(elementId: String): SpatializedDynamic3DElement? {
        return dynamic3DElements.find { it.id == elementId }
    }

    // === State Management ===

    fun getStateString(): String {
        return when (state.value) {
            SpatialSceneState.IDLE -> "idle"
            SpatialSceneState.PENDING -> "pending"
            SpatialSceneState.WILL_VISIBLE -> "willVisible"
            SpatialSceneState.VISIBLE -> "visible"
            SpatialSceneState.FAIL -> "fail"
        }
    }

    /**
     * Get maximum backOffset of all elements (for panel depth calculation).
     */
    fun getMaxBackOffset(): Double {
        return spatialized2DElements.maxOfOrNull { it.backOffset } ?: 0.0
    }

    /**
     * Get elements sorted by depth for rendering.
     */
    fun getElementsSortedByDepth(): List<Spatialized2DElement> {
        return spatialized2DElements.sortedBy { it.zIndex + it.backOffset }
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying SpatialScene: $id")
        scenes.remove(id)

        // Destroy all children
        spatialized2DElements.toList().forEach { it.destroy() }
        static3DElements.toList().forEach { it.destroy() }
        dynamic3DElements.toList().forEach { it.destroy() }

        spatialized2DElements.clear()
        static3DElements.clear()
        dynamic3DElements.clear()
    }
}
