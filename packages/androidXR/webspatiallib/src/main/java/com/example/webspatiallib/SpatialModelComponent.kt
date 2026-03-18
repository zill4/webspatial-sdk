package com.example.webspatiallib

import android.util.Log

/**
 * Model component that combines geometry with materials.
 * Mirrors visionOS SpatialModelComponent.
 */
class SpatialModelComponent(
    val geometry: SpatialGeometry,
    val materials: List<SpatialMaterial> = emptyList()
) : SpatialComponent() {

    companion object {
        private const val TAG = "SpatialModelComponent"
    }

    init {
        Log.d(TAG, "Created SpatialModelComponent: geometry=${geometry.geometryType}, materials=${materials.size}")
    }

    override fun onAddToEntity() {
        Log.d(TAG, "SpatialModelComponent added to entity: ${entity?.id}")
        // In Android XR, this would trigger rendering setup
    }

    override fun onDestroy() {
        Log.d(TAG, "SpatialModelComponent destroyed")
        super.onDestroy()
    }
}

/**
 * Model asset component for loading external 3D models (glTF/GLB).
 */
class SpatialModelAsset(
    val url: String,
    var isLoaded: Boolean = false,
    var loadError: String? = null
) : SpatialComponent() {

    companion object {
        private const val TAG = "SpatialModelAsset"
    }

    init {
        Log.d(TAG, "Created SpatialModelAsset: url=$url")
    }

    /**
     * Called when the model has finished loading.
     */
    fun onLoaded() {
        isLoaded = true
        loadError = null
        Log.d(TAG, "Model loaded: $url")
    }

    /**
     * Called when loading fails.
     */
    fun onLoadError(error: String) {
        isLoaded = false
        loadError = error
        Log.e(TAG, "Model load failed: $url - $error")
    }

    override fun onAddToEntity() {
        Log.d(TAG, "SpatialModelAsset added to entity: ${entity?.id}")
        // Trigger async model loading here
    }

    override fun onDestroy() {
        Log.d(TAG, "SpatialModelAsset destroyed: $url")
        super.onDestroy()
    }
}
