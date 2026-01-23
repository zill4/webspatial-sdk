package com.example.webspatiallib

import android.util.Log

/**
 * Base class for all spatial geometries.
 * Mirrors visionOS SpatialGeometry system.
 */
sealed class SpatialGeometry : SpatialObject() {
    companion object {
        const val TAG = "SpatialGeometry"
    }

    abstract val geometryType: String
}

/**
 * Box geometry with optional corner radius and split faces.
 */
class BoxGeometry(
    val width: Float = 1f,
    val height: Float = 1f,
    val depth: Float = 1f,
    val cornerRadius: Float = 0f,
    val splitFaces: Boolean = false
) : SpatialGeometry() {
    override val geometryType = "box"

    init {
        Log.d(TAG, "Created BoxGeometry: ${width}x${height}x${depth}, cornerRadius=$cornerRadius")
    }
}

/**
 * Plane geometry (2D surface in 3D space).
 */
class PlaneGeometry(
    val width: Float = 1f,
    val height: Float = 1f,
    val cornerRadius: Float = 0f
) : SpatialGeometry() {
    override val geometryType = "plane"

    init {
        Log.d(TAG, "Created PlaneGeometry: ${width}x${height}, cornerRadius=$cornerRadius")
    }
}

/**
 * Sphere geometry.
 */
class SphereGeometry(
    val radius: Float = 0.5f
) : SpatialGeometry() {
    override val geometryType = "sphere"

    init {
        Log.d(TAG, "Created SphereGeometry: radius=$radius")
    }
}

/**
 * Cone geometry.
 */
class ConeGeometry(
    val radius: Float = 0.5f,
    val height: Float = 1f
) : SpatialGeometry() {
    override val geometryType = "cone"

    init {
        Log.d(TAG, "Created ConeGeometry: radius=$radius, height=$height")
    }
}

/**
 * Cylinder geometry.
 */
class CylinderGeometry(
    val radius: Float = 0.5f,
    val height: Float = 1f
) : SpatialGeometry() {
    override val geometryType = "cylinder"

    init {
        Log.d(TAG, "Created CylinderGeometry: radius=$radius, height=$height")
    }
}
