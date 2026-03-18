package com.example.webspatialandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Observable state for spatial elements.
 * This allows Compose UI to react to changes from WebSpatial commands.
 *
 * Property updates are batched/coalesced to reduce UI thrashing when
 * many updates come in rapidly (e.g., during animations or resize).
 */
object SpatialElementState {

    /**
     * All tracked spatial elements (id -> properties)
     */
    val elements = mutableStateMapOf<String, SpatialElementProps>()

    /**
     * Pending updates to be batched
     */
    private val pendingUpdates = mutableMapOf<String, SpatialElementProps>()

    /**
     * Handler for debounced updates
     */
    private val updateHandler = Handler(Looper.getMainLooper())

    /**
     * Runnable for processing batched updates
     */
    private var batchUpdateRunnable: Runnable? = null

    /**
     * Batch window in milliseconds - updates within this window are coalesced
     */
    var batchWindowMs: Long = 16L // ~60fps frame time

    /**
     * The main panel's depth offset (derived from the maximum backOffset of all elements)
     * In Android XR, we adjust the panel's Z position based on this.
     */
    val panelDepthOffset = mutableStateOf(0f)

    /**
     * The main panel's background material type
     */
    val panelMaterial = mutableStateOf("none")

    /**
     * Whether any spatial elements are active
     */
    val hasSpatialElements: Boolean
        get() = elements.isNotEmpty()

    /**
     * Update or create a spatial element's properties.
     * Updates are batched within a window to coalesce rapid changes.
     *
     * @param id Element ID
     * @param props New properties
     * @param immediate If true, bypass batching and apply immediately
     */
    fun updateElement(id: String, props: SpatialElementProps, immediate: Boolean = false) {
        if (immediate || batchWindowMs <= 0) {
            // Apply immediately without batching
            applyUpdate(id, props)
            return
        }

        // Add to pending updates (overwrites previous pending update for same ID)
        synchronized(pendingUpdates) {
            pendingUpdates[id] = props
        }

        // Schedule batch processing if not already scheduled
        if (batchUpdateRunnable == null) {
            batchUpdateRunnable = Runnable {
                processBatchedUpdates()
            }
            updateHandler.postDelayed(batchUpdateRunnable!!, batchWindowMs)
        }
    }

    /**
     * Process all pending batched updates
     */
    private fun processBatchedUpdates() {
        val updates: Map<String, SpatialElementProps>
        synchronized(pendingUpdates) {
            updates = pendingUpdates.toMap()
            pendingUpdates.clear()
        }
        batchUpdateRunnable = null

        // Apply all updates in one batch
        updates.forEach { (id, props) ->
            elements[id] = props
        }

        // Only recalculate once after all updates
        if (updates.isNotEmpty()) {
            recalculatePanelState()
        }
    }

    /**
     * Apply a single update immediately
     */
    private fun applyUpdate(id: String, props: SpatialElementProps) {
        elements[id] = props
        recalculatePanelState()
    }

    /**
     * Remove a spatial element
     */
    fun removeElement(id: String) {
        elements.remove(id)
        recalculatePanelState()
    }

    /**
     * Recalculate the panel state based on all elements
     */
    private fun recalculatePanelState() {
        if (elements.isEmpty()) {
            panelDepthOffset.value = 0f
            panelMaterial.value = "none"
            return
        }

        // Find the maximum backOffset (deepest element)
        val maxBackOffset = elements.values.maxOfOrNull { it.backOffset } ?: 0.0
        panelDepthOffset.value = maxBackOffset.toFloat()

        // Use the most prominent material (non-none)
        val materials = elements.values
            .map { it.backgroundMaterial }
            .filter { it != "none" }

        panelMaterial.value = materials.firstOrNull() ?: "none"
    }

    /**
     * Get sorted elements by depth (for rendering order)
     */
    fun getElementsSortedByDepth(): List<SpatialElementProps> {
        return elements.values.sortedBy { it.zIndex + it.backOffset }
    }
}

/**
 * Properties for a spatial element (mirrors JS Spatialized2DElementProperties)
 */
data class SpatialElementProps(
    val id: String,
    var clientX: Double = 0.0,
    var clientY: Double = 0.0,
    var width: Double = 0.0,
    var height: Double = 0.0,
    var depth: Double = 0.0,
    var backOffset: Double = 0.0,
    var opacity: Double = 1.0,
    var visible: Boolean = true,
    var scrollWithParent: Boolean = true,
    var zIndex: Double = 0.0,
    var cornerRadius: Double = 0.0,
    var backgroundMaterial: String = "none",
    var parentId: String? = null,
    var attachedToScene: Boolean = false,
    var transform: DoubleArray? = null,
    var enableTapGesture: Boolean = false,
    var enableDragGesture: Boolean = false,
    var enableRotateGesture: Boolean = false,
    var enableMagnifyGesture: Boolean = false,
    // Bitmap data for element content (base64 encoded PNG)
    var bitmapData: String? = null,
    // Decoded bitmap (cached after first decode)
    @Transient
    var decodedBitmap: android.graphics.Bitmap? = null
) {
    /**
     * Convert backOffset (pixels) to Android XR depth (dp)
     * The scale factor converts web pixels to meaningful XR depth
     */
    fun getDepthDp(): Float {
        // 1 pixel of backOffset = 1dp of Z offset in XR space (increased from 0.5)
        // Apply minimum of 20dp for any positive offset to ensure visual distinction
        val depthDp = (backOffset * 1.0).toFloat()
        return if (depthDp > 0) maxOf(depthDp, 20f) else depthDp
    }

    /**
     * Get position as offset from center (for SubspaceModifier.offset)
     */
    fun getOffsetDp(): Pair<Float, Float> {
        // Convert clientX/Y to offset from panel center
        // This is a simplified calculation - real implementation would need
        // to know the panel size and adjust accordingly
        return Pair(clientX.toFloat(), clientY.toFloat())
    }

    /**
     * Extract translation from transform matrix (column 3 = x, y, z translation)
     */
    fun getTranslation(): Triple<Float, Float, Float> {
        val matrix = transform ?: return Triple(0f, 0f, 0f)
        if (matrix.size < 16) return Triple(0f, 0f, 0f)

        // Column-major 4x4 matrix: translation is at indices 12, 13, 14
        val tx = matrix[12].toFloat()
        val ty = matrix[13].toFloat()
        val tz = matrix[14].toFloat()

        return Triple(tx, ty, tz)
    }

    /**
     * Get or decode the bitmap from base64 data
     */
    fun getBitmap(): Bitmap? {
        // Return cached bitmap if available
        decodedBitmap?.let { return it }

        // Decode from base64 if data exists
        val data = bitmapData ?: return null
        return try {
            // Remove data URL prefix if present (e.g., "data:image/png;base64," or "data:image/webp;base64,")
            val base64Data = if (data.contains(",")) {
                data.substringAfter(",")
            } else {
                data
            }

            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            Log.d("SpatialElementProps", "Decoding bitmap for $id: ${bytes.size} bytes, data prefix: ${data.take(50)}")

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                Log.e("SpatialElementProps", "BitmapFactory returned null for $id (${bytes.size} bytes)")
            } else {
                Log.d("SpatialElementProps", "Decoded bitmap for $id: ${bitmap.width}x${bitmap.height}")
            }
            decodedBitmap = bitmap
            bitmap
        } catch (e: Exception) {
            Log.e("SpatialElementProps", "Failed to decode bitmap for $id: ${e.message}")
            null
        }
    }

    /**
     * Check if this element should be rendered as a separate spatial panel
     */
    fun shouldRenderAsSpatialPanel(): Boolean {
        // Render as separate panel if:
        // 1. Has any backOffset (depth) OR has bitmap data (spatial element with content)
        // 2. Is visible
        // 3. Has valid dimensions
        val hasDepth = backOffset != 0.0
        val hasBitmap = bitmapData != null
        return visible && (hasDepth || hasBitmap) && width > 0 && height > 0
    }

    /**
     * Check if this element has content to render (bitmap)
     */
    fun hasBitmapContent(): Boolean = bitmapData != null
}

/**
 * Extension to convert material string to Android-friendly format
 */
fun String.toAndroidMaterial(): MaterialType {
    return when (this.lowercase()) {
        "none" -> MaterialType.NONE
        "transparent" -> MaterialType.TRANSPARENT
        "translucent" -> MaterialType.TRANSLUCENT
        "thin" -> MaterialType.THIN
        "regular" -> MaterialType.REGULAR
        "thick" -> MaterialType.THICK
        else -> MaterialType.NONE
    }
}

enum class MaterialType {
    NONE,
    TRANSPARENT,
    TRANSLUCENT,
    THIN,
    REGULAR,
    THICK
}
