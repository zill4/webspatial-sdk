package com.example.webspatiallib

import android.util.Log
import java.lang.ref.WeakReference

/**
 * Represents an HTML element that has been spatialized (has enable-xr attribute).
 * Mirrors visionOS Spatialized2DElement.
 *
 * In visionOS, each Spatialized2DElement gets its own WKWebView.
 * In Android XR, we track properties and apply them via CSS transforms
 * or by creating overlay views.
 */
class Spatialized2DElement : SpatialObject {

    companion object {
        private const val TAG = "Spatialized2DElement"
    }

    val sourceWebView: WeakReference<NativeWebView>

    /**
     * Create with auto-generated ID.
     */
    constructor(sourceWebView: WeakReference<NativeWebView>) : super() {
        this.sourceWebView = sourceWebView
    }

    /**
     * Create with custom ID - used when the ID is pre-generated
     * (e.g., from window.open webspatial:// URL handling)
     */
    constructor(sourceWebView: WeakReference<NativeWebView>, customId: String) : super(customId) {
        this.sourceWebView = sourceWebView
    }

    // Position and size (in CSS pixels)
    var clientX: Double = 0.0
    var clientY: Double = 0.0
    var width: Double = 0.0
    var height: Double = 0.0
    var depth: Double = 0.0

    // Transform properties
    var backOffset: Double = 0.0  // Z offset (positive = behind)
    var opacity: Double = 1.0
    var visible: Boolean = true
    var scrollWithParent: Boolean = true
    var zIndex: Double = 0.0
    var cornerRadius: Double = 0.0

    // Background material (glass effect)
    var backgroundMaterial: String = "none"

    // Bitmap content for rendering (base64 encoded PNG)
    // On Android, we capture HTML content as bitmaps since we can't create
    // multiple real WebViews like visionOS does with WKWebView
    var bitmapData: String? = null

    // 4x4 transform matrix (column-major, 16 elements)
    var transformMatrix: DoubleArray? = null

    // Gesture flags
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

    // Parent element for nesting
    var parentElement: Spatialized2DElement? = null
    private val childElements = mutableMapOf<String, Spatialized2DElement>()

    // Parent scene
    var parentScene: SpatialScene? = null

    init {
        Log.d(TAG, "Created Spatialized2DElement: $id")
    }

    /**
     * Update properties from command data.
     */
    fun updateProperties(
        clientX: Double? = null,
        clientY: Double? = null,
        width: Double? = null,
        height: Double? = null,
        depth: Double? = null,
        backOffset: Double? = null,
        opacity: Double? = null,
        visible: Boolean? = null,
        scrollWithParent: Boolean? = null,
        zIndex: Double? = null,
        cornerRadius: Double? = null,
        backgroundMaterial: String? = null,
        bitmapData: String? = null
    ) {
        clientX?.let { this.clientX = it }
        clientY?.let { this.clientY = it }
        width?.let { this.width = it }
        height?.let { this.height = it }
        depth?.let { this.depth = it }
        backOffset?.let { this.backOffset = it }
        opacity?.let { this.opacity = it }
        visible?.let { this.visible = it }
        scrollWithParent?.let { this.scrollWithParent = it }
        zIndex?.let { this.zIndex = it }
        cornerRadius?.let { this.cornerRadius = it }
        backgroundMaterial?.let { this.backgroundMaterial = it }
        bitmapData?.let { this.bitmapData = it }

        Log.d(TAG, "Updated $id: pos=($clientX, $clientY), size=(${this.width}x${this.height}), " +
                "backOffset=${this.backOffset}, material=${this.backgroundMaterial}, " +
                "scrollWithParent=${this.scrollWithParent}, parent=${this.parentElement?.id}, " +
                "scene=${this.parentScene?.id}, hasBitmap=${this.bitmapData != null}")
    }

    /**
     * Update transform matrix.
     */
    fun updateTransform(matrix: DoubleArray) {
        if (matrix.size >= 16) {
            transformMatrix = matrix
            Log.d(TAG, "Updated transform for $id: translation=(${matrix[12]}, ${matrix[13]}, ${matrix[14]})")
        }
    }

    /**
     * Update gesture flags.
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
    }

    /**
     * Add this element to a scene.
     */
    fun addToScene(scene: SpatialScene) {
        parentScene = scene
        scene.addSpatialized2DElement(this)
        Log.d(TAG, "Added $id to scene ${scene.id}")
    }

    /**
     * Add this element as child of another element.
     */
    fun addToElement(parent: Spatialized2DElement) {
        parentElement = parent
        parent.childElements[id] = this
        Log.d(TAG, "Added $id as child of ${parent.id}")
    }

    /**
     * Get child elements.
     */
    fun getChildren(): Collection<Spatialized2DElement> = childElements.values

    /**
     * Extract translation from transform matrix.
     */
    fun getTranslation(): Triple<Double, Double, Double> {
        val matrix = transformMatrix ?: return Triple(0.0, 0.0, 0.0)
        return Triple(matrix[12], matrix[13], matrix[14])
    }

    /**
     * Calculate Z position in dp for Android XR rendering.
     */
    fun getDepthDp(): Float {
        // Convert backOffset (CSS pixels) to dp
        // Positive backOffset = behind the panel
        return (backOffset * 0.5f).toFloat()
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying Spatialized2DElement: $id")
        parentElement?.childElements?.remove(id)
        parentScene?.removeSpatialized2DElement(id)
        childElements.values.forEach { it.destroy() }
        childElements.clear()
    }
}

/**
 * Represents a static 3D model element (loaded from URL).
 */
class SpatializedStatic3DElement : SpatialObject {

    companion object {
        private const val TAG = "Static3DElement"
    }

    val modelURL: String
    var isLoaded: Boolean = false
    var loadError: String? = null

    /**
     * Create with auto-generated ID.
     */
    constructor(modelURL: String) : super() {
        this.modelURL = modelURL
        Log.d(TAG, "Created SpatializedStatic3DElement: $id, url=$modelURL")
    }

    /**
     * Create with custom ID.
     */
    constructor(modelURL: String, customId: String) : super(customId) {
        this.modelURL = modelURL
        Log.d(TAG, "Created SpatializedStatic3DElement: $id, url=$modelURL")
    }

    // Transform properties
    var transformMatrix: DoubleArray? = null
    var opacity: Double = 1.0
    var visible: Boolean = true

    // Parent
    var parentElement: Spatialized2DElement? = null
    var parentScene: SpatialScene? = null

    fun onLoadSuccess() {
        isLoaded = true
        loadError = null
        Log.d(TAG, "Model loaded: $modelURL")
    }

    fun onLoadFailure(error: String) {
        isLoaded = false
        loadError = error
        Log.e(TAG, "Model load failed: $modelURL - $error")
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying SpatializedStatic3DElement: $id")
        parentScene?.removeStatic3DElement(id)
    }
}

/**
 * Represents a dynamic 3D element container (for entity/component content).
 */
class SpatializedDynamic3DElement : SpatialObject {

    companion object {
        private const val TAG = "Dynamic3DElement"
    }

    // Root entity for this dynamic 3D content
    val rootEntity: SpatialEntity = SpatialEntity()

    // Transform properties
    var transformMatrix: DoubleArray? = null
    var opacity: Double = 1.0
    var visible: Boolean = true

    // Parent
    var parentElement: Spatialized2DElement? = null
    var parentScene: SpatialScene? = null

    /**
     * Create with auto-generated ID.
     */
    constructor() : super() {
        Log.d(TAG, "Created SpatializedDynamic3DElement: $id")
    }

    /**
     * Create with custom ID.
     */
    constructor(customId: String) : super(customId) {
        Log.d(TAG, "Created SpatializedDynamic3DElement: $id")
    }

    /**
     * Add an entity to this dynamic 3D element.
     */
    fun addEntity(entity: SpatialEntity) {
        rootEntity.addChild(entity)
        Log.d(TAG, "Added entity ${entity.id} to dynamic 3D $id")
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying SpatializedDynamic3DElement: $id")
        rootEntity.destroy()
        parentScene?.removeDynamic3DElement(id)
    }
}
