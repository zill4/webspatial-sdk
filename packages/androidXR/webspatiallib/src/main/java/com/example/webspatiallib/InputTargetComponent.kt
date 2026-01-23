package com.example.webspatiallib

import android.util.Log

/**
 * Input target component for handling gestures on entities.
 * Mirrors visionOS InputTargetComponent.
 *
 * This component marks an entity as being interactive and capable of
 * receiving gesture events (tap, drag, rotate, magnify).
 */
class InputTargetComponent(
    /**
     * Whether to allow indirect (hover/ray-based) input.
     */
    var allowsIndirectInput: Boolean = true,

    /**
     * Whether to allow direct (hand/controller touch) input.
     */
    var allowsDirectInput: Boolean = true
) : SpatialComponent() {

    companion object {
        private const val TAG = "InputTargetComponent"
    }

    // Gesture enabled flags
    var enableTapGesture: Boolean = false
    var enableDragStartGesture: Boolean = false
    var enableDragGesture: Boolean = false
    var enableDragEndGesture: Boolean = false
    var enableRotateStartGesture: Boolean = false
    var enableRotateGesture: Boolean = false
    var enableRotateEndGesture: Boolean = false
    var enableMagnifyStartGesture: Boolean = false
    var enableMagnifyGesture: Boolean = false
    var enableMagnifyEndGesture: Boolean = false

    /**
     * Returns true if any gesture is enabled.
     */
    val hasGesturesEnabled: Boolean
        get() = enableTapGesture ||
                enableDragStartGesture || enableDragGesture || enableDragEndGesture ||
                enableRotateStartGesture || enableRotateGesture || enableRotateEndGesture ||
                enableMagnifyStartGesture || enableMagnifyGesture || enableMagnifyEndGesture

    init {
        Log.d(TAG, "Created InputTargetComponent: id=$id")
    }

    /**
     * Update gesture flags from properties map.
     */
    fun updateGestureFlags(
        tap: Boolean? = null,
        dragStart: Boolean? = null,
        drag: Boolean? = null,
        dragEnd: Boolean? = null,
        rotateStart: Boolean? = null,
        rotate: Boolean? = null,
        rotateEnd: Boolean? = null,
        magnifyStart: Boolean? = null,
        magnify: Boolean? = null,
        magnifyEnd: Boolean? = null
    ) {
        tap?.let { enableTapGesture = it }
        dragStart?.let { enableDragStartGesture = it }
        drag?.let { enableDragGesture = it }
        dragEnd?.let { enableDragEndGesture = it }
        rotateStart?.let { enableRotateStartGesture = it }
        rotate?.let { enableRotateGesture = it }
        rotateEnd?.let { enableRotateEndGesture = it }
        magnifyStart?.let { enableMagnifyStartGesture = it }
        magnify?.let { enableMagnifyGesture = it }
        magnifyEnd?.let { enableMagnifyEndGesture = it }

        Log.d(TAG, "Updated gesture flags: tap=$enableTapGesture, drag=$enableDragGesture, " +
                "rotate=$enableRotateGesture, magnify=$enableMagnifyGesture")
    }

    override fun onAddToEntity() {
        Log.d(TAG, "InputTargetComponent added to entity: ${entity?.id}")
    }

    override fun onDestroy() {
        Log.d(TAG, "InputTargetComponent destroyed: $id")
        super.onDestroy()
    }
}

/**
 * Collision component for hit testing.
 * Defines the shape used for gesture/input hit detection.
 */
class CollisionComponent(
    /**
     * The shape to use for collision detection.
     */
    val shape: CollisionShape = CollisionShape.Box(1f, 1f, 1f),

    /**
     * Collision filter/group for determining what can interact.
     */
    var group: String = "default"
) : SpatialComponent() {

    companion object {
        private const val TAG = "CollisionComponent"
    }

    init {
        Log.d(TAG, "Created CollisionComponent: shape=${shape.type}, id=$id")
    }

    override fun onAddToEntity() {
        Log.d(TAG, "CollisionComponent added to entity: ${entity?.id}")
    }

    override fun onDestroy() {
        Log.d(TAG, "CollisionComponent destroyed: $id")
        super.onDestroy()
    }
}

/**
 * Collision shape types for hit testing.
 */
sealed class CollisionShape {
    abstract val type: String

    /**
     * Box collision shape.
     */
    data class Box(
        val width: Float,
        val height: Float,
        val depth: Float
    ) : CollisionShape() {
        override val type: String = "box"
    }

    /**
     * Sphere collision shape.
     */
    data class Sphere(
        val radius: Float
    ) : CollisionShape() {
        override val type: String = "sphere"
    }

    /**
     * Capsule collision shape.
     */
    data class Capsule(
        val radius: Float,
        val height: Float
    ) : CollisionShape() {
        override val type: String = "capsule"
    }

    /**
     * Uses the entity's model geometry for collision.
     */
    object FromModel : CollisionShape() {
        override val type: String = "model"
    }
}
