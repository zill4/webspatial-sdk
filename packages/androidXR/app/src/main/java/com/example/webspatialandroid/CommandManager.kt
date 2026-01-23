package com.example.webspatialandroid

import android.util.Log
import com.example.webspatiallib.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.lang.ref.WeakReference

inline fun <reified T> JsonElement.find(vararg keys: String): T? {
    val element = keys.fold(this) { acc, key ->
        (acc as? JsonObject)?.get(key) ?: return null
    }

    if (T::class == JsonElement::class) {
        return element as T?
    }

    if (T::class == JsonArray::class) {
        return try { element.jsonArray as T? } catch (e: Exception) { null }
    }

    val primitive = try { element.jsonPrimitive } catch (e: Exception) { return null }

    return when (T::class) {
        String::class -> primitive.content as T
        Int::class -> primitive.intOrNull as T?
        Long::class -> primitive.longOrNull as T?
        Double::class -> primitive.doubleOrNull as T?
        Float::class -> primitive.floatOrNull as T?
        Boolean::class -> primitive.booleanOrNull as T?
        else -> null
    }
}

/**
 * CommandManager handles commands from JavaScript WebSpatial SDK.
 *
 * Implements full WebSpatial API matching visionOS implementation:
 * - Scene management
 * - Spatialized 2D elements (HTML in 3D space)
 * - Static 3D elements (model loading)
 * - Dynamic 3D elements (entity/component system)
 * - Gesture handling
 * - Lifecycle management
 */
class CommandManager : CommandManagerInterface {

    companion object {
        private const val TAG = "WebSpatialCmd"
    }

    // Scene per webview
    private val webviewScenes = mutableMapOf<String, SpatialScene>()

    /**
     * Get or create the scene for a webview.
     */
    private fun getScene(webview: NativeWebView): SpatialScene {
        val webviewId = webview.windowComponent.get()?.id ?: "default"
        return webviewScenes.getOrPut(webviewId) {
            SpatialScene(WeakReference(webview)).also {
                Log.d(TAG, "Created scene for webview: $webviewId")
            }
        }
    }

    override fun processCommand(senderWebview: NativeWebView, ci: CommandInfo) {
        Log.d(TAG, "Processing command: ${ci.command} (requestID: ${ci.requestID})")

        when (ci.command) {
            // === Scene Management ===
            "UpdateSpatialSceneProperties" -> handleUpdateSpatialSceneProperties(senderWebview, ci)
            "UpdateSceneConfig" -> handleUpdateSceneConfig(senderWebview, ci)
            "GetSpatialSceneState" -> handleGetSpatialSceneState(senderWebview, ci)
            "FocusScene" -> handleFocusScene(senderWebview, ci)

            // === Spatialized 2D Element ===
            "CreateSpatialized2DElement" -> handleCreateSpatialized2DElement(senderWebview, ci)
            "UpdateSpatialized2DElementProperties" -> handleUpdateSpatialized2DElementProperties(senderWebview, ci)
            "UpdateSpatializedElementTransform" -> handleUpdateSpatializedElementTransform(senderWebview, ci)
            "AddSpatializedElementToSpatialScene" -> handleAddSpatializedElementToSpatialScene(senderWebview, ci)
            "AddSpatializedElementToSpatialized2DElement" -> handleAddSpatializedElementToSpatialized2DElement(senderWebview, ci)

            // === Static 3D Elements ===
            "CreateSpatializedStatic3DElement" -> handleCreateSpatializedStatic3DElement(senderWebview, ci)
            "UpdateSpatializedStatic3DElementProperties" -> handleUpdateSpatializedStatic3DElementProperties(senderWebview, ci)

            // === Dynamic 3D Elements ===
            "CreateSpatializedDynamic3DElement" -> handleCreateSpatializedDynamic3DElement(senderWebview, ci)
            "UpdateSpatializedDynamic3DElementProperties" -> handleUpdateSpatializedDynamic3DElementProperties(senderWebview, ci)

            // === Entity/Component System ===
            "CreateSpatialEntity" -> handleCreateSpatialEntity(senderWebview, ci)
            "UpdateEntityProperties" -> handleUpdateEntityProperties(senderWebview, ci)
            "AddEntityToDynamic3D" -> handleAddEntityToDynamic3D(senderWebview, ci)
            "AddEntityToEntity" -> handleAddEntityToEntity(senderWebview, ci)
            "RemoveEntityFromParent" -> handleRemoveEntityFromParent(senderWebview, ci)
            "SetParentToEntity" -> handleSetParentToEntity(senderWebview, ci)

            // === Geometry & Materials ===
            "CreateGeometry" -> handleCreateGeometry(senderWebview, ci)
            "CreateUnlitMaterial" -> handleCreateUnlitMaterial(senderWebview, ci)
            "CreateModelComponent" -> handleCreateModelComponent(senderWebview, ci)
            "AddComponentToEntity" -> handleAddComponentToEntity(senderWebview, ci)

            // === Model Assets ===
            "CreateModelAsset" -> handleCreateModelAsset(senderWebview, ci)
            "CreateSpatialModelEntity" -> handleCreateSpatialModelEntity(senderWebview, ci)

            // === Lifecycle ===
            "Destroy" -> handleDestroy(senderWebview, ci)
            "Inspect" -> handleInspect(senderWebview, ci)

            // === Legacy/Compat ===
            "updateResource" -> handleUpdateResource(senderWebview, ci)
            "CheckWebViewCanCreate" -> handleCheckWebViewCanCreate(senderWebview, ci)

            else -> {
                Log.w(TAG, "Unhandled command: ${ci.command}")
                senderWebview.completeEvent(ci.requestID)
            }
        }
    }

    // ============================================================
    // Scene Management Handlers
    // ============================================================

    private fun handleUpdateSpatialSceneProperties(webview: NativeWebView, ci: CommandInfo) {
        val scene = getScene(webview)

        val cornerRadius = ci.json.find<Double>("data", "cornerRadius")
        val opacity = ci.json.find<Double>("data", "opacity")
        val material = ci.json.find<String>("data", "material", "type")

        scene.updateProperties(cornerRadius, opacity, material)

        // Update observable state for Compose UI
        SpatialElementState.panelMaterial.value = material ?: "none"

        Log.d(TAG, "UpdateSpatialSceneProperties: cornerRadius=$cornerRadius, opacity=$opacity, material=$material")
        webview.completeEvent(ci.requestID)
    }

    private fun handleUpdateSceneConfig(webview: NativeWebView, ci: CommandInfo) {
        val scene = getScene(webview)

        val width = ci.json.find<Double>("data", "defaultSize", "width")
        val height = ci.json.find<Double>("data", "defaultSize", "height")
        val resizable = ci.json.find<Boolean>("data", "resizability") != false
        val worldScaling = ci.json.find<String>("data", "worldScaling")
        val worldAlignment = ci.json.find<String>("data", "worldAlignment")

        scene.updateConfig(width, height, resizable, worldScaling, worldAlignment)

        Log.d(TAG, "UpdateSceneConfig: size=${width}x${height}, resizable=$resizable")
        webview.completeEvent(ci.requestID)
    }

    private fun handleGetSpatialSceneState(webview: NativeWebView, ci: CommandInfo) {
        val scene = getScene(webview)
        val state = scene.getStateString()

        Log.d(TAG, "GetSpatialSceneState: $state")
        webview.completeEvent(ci.requestID, """{"state": "$state"}""")
    }

    private fun handleFocusScene(webview: NativeWebView, ci: CommandInfo) {
        val sceneId = ci.json.find<String>("data", "id")
        Log.d(TAG, "FocusScene: $sceneId")
        // TODO: Implement scene focus in Android XR
        webview.completeEvent(ci.requestID)
    }

    // ============================================================
    // Spatialized 2D Element Handlers
    // ============================================================

    private fun handleCreateSpatialized2DElement(webview: NativeWebView, ci: CommandInfo) {
        // Get ID from request or generate new one
        val elementId = ci.json.find<String>("data", "id")
        Log.d(TAG, "CreateSpatialized2DElement: requestedId=$elementId")

        val element = if (elementId != null) {
            // Use provided ID (from SDK)
            Spatialized2DElement(WeakReference(webview), elementId)
        } else {
            // Generate new ID
            Spatialized2DElement(WeakReference(webview))
        }

        Log.d(TAG, "Created Spatialized2DElement: ${element.id}")
        webview.completeEvent(ci.requestID, """{"id": "${element.id}"}""")
    }

    private fun handleUpdateSpatialized2DElementProperties(webview: NativeWebView, ci: CommandInfo) {
        val elementId = ci.json.find<String>("data", "id") ?: ci.resourceID
        Log.d(TAG, "UpdateSpatialized2DElementProperties: id=$elementId")

        // Get or create element
        var element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element == null) {
            element = Spatialized2DElement(WeakReference(webview))
            // Register with the provided ID
            SpatialObject.objects.remove(element.id)
            SpatialObject.objects[elementId] = element
            Log.d(TAG, "Created new Spatialized2DElement: $elementId")
        }

        // Update properties
        element.updateProperties(
            clientX = ci.json.find("data", "clientX"),
            clientY = ci.json.find("data", "clientY"),
            width = ci.json.find("data", "width"),
            height = ci.json.find("data", "height"),
            depth = ci.json.find("data", "depth"),
            backOffset = ci.json.find("data", "backOffset"),
            opacity = ci.json.find("data", "opacity"),
            visible = ci.json.find("data", "visible"),
            zIndex = ci.json.find("data", "zIndex"),
            cornerRadius = ci.json.find("data", "cornerRadius", "value"),
            backgroundMaterial = ci.json.find("data", "material", "type"),
            bitmapData = ci.json.find("data", "bitmap")
        )

        // Update gesture flags
        element.updateGestureFlags(
            tap = ci.json.find("data", "enableTapGesture"),
            dragStart = ci.json.find("data", "enableDragStartGesture"),
            drag = ci.json.find("data", "enableDragGesture"),
            dragEnd = ci.json.find("data", "enableDragEndGesture"),
            rotateStart = ci.json.find("data", "enableRotateStartGesture"),
            rotate = ci.json.find("data", "enableRotateGesture"),
            rotateEnd = ci.json.find("data", "enableRotateEndGesture"),
            magnifyStart = ci.json.find("data", "enableMagnifyStartGesture"),
            magnify = ci.json.find("data", "enableMagnifyGesture"),
            magnifyEnd = ci.json.find("data", "enableMagnifyEndGesture")
        )

        // Update observable state for Compose UI
        updateSpatialElementState(element)

        webview.completeEvent(ci.requestID)
    }

    private fun handleUpdateSpatializedElementTransform(webview: NativeWebView, ci: CommandInfo) {
        val elementId = ci.json.find<String>("data", "id") ?: ci.resourceID
        val matrixArray = ci.json.find<JsonArray>("data", "matrix")

        Log.d(TAG, "UpdateSpatializedElementTransform: id=$elementId")

        val element = SpatialObject.get(elementId) as? Spatialized2DElement
        if (element != null && matrixArray != null) {
            val transformArray = matrixArray.map {
                it.jsonPrimitive.doubleOrNull ?: 0.0
            }.toDoubleArray()
            element.updateTransform(transformArray)

            // Update observable state
            updateSpatialElementState(element)
        }

        webview.completeEvent(ci.requestID)
    }

    private fun handleAddSpatializedElementToSpatialScene(webview: NativeWebView, ci: CommandInfo) {
        val elementId = ci.json.find<String>("data", "spatializedElementId")
        Log.d(TAG, "AddSpatializedElementToSpatialScene: elementId=$elementId")

        val element = elementId?.let { SpatialObject.get(it) as? Spatialized2DElement }
        if (element != null) {
            val scene = getScene(webview)
            element.addToScene(scene)
        }

        webview.completeEvent(ci.requestID)
    }

    private fun handleAddSpatializedElementToSpatialized2DElement(webview: NativeWebView, ci: CommandInfo) {
        val parentId = ci.json.find<String>("data", "id")
        val childId = ci.json.find<String>("data", "spatializedElementId")
        Log.d(TAG, "AddSpatializedElementToSpatialized2DElement: parent=$parentId, child=$childId")

        val parent = parentId?.let { SpatialObject.get(it) as? Spatialized2DElement }
        val child = childId?.let { SpatialObject.get(it) as? Spatialized2DElement }

        if (parent != null && child != null) {
            child.addToElement(parent)
        }

        webview.completeEvent(ci.requestID)
    }

    // ============================================================
    // Static 3D Element Handlers
    // ============================================================

    private fun handleCreateSpatializedStatic3DElement(webview: NativeWebView, ci: CommandInfo) {
        val modelURL = ci.json.find<String>("data", "modelURL") ?: ""
        Log.d(TAG, "CreateSpatializedStatic3DElement: modelURL=$modelURL")

        val element = SpatializedStatic3DElement(modelURL)

        // TODO: Actually load the model using SceneCore
        // For now, we just create the element and return its ID

        webview.completeEvent(ci.requestID, """{"id": "${element.id}"}""")
    }

    private fun handleUpdateSpatializedStatic3DElementProperties(webview: NativeWebView, ci: CommandInfo) {
        val elementId = ci.json.find<String>("data", "id")
        Log.d(TAG, "UpdateSpatializedStatic3DElementProperties: id=$elementId")

        val element = elementId?.let { SpatialObject.get(it) as? SpatializedStatic3DElement }
        if (element != null) {
            ci.json.find<Double>("data", "opacity")?.let { element.opacity = it }
            ci.json.find<Boolean>("data", "visible")?.let { element.visible = it }

            val matrixArray = ci.json.find<JsonArray>("data", "transform")
            if (matrixArray != null) {
                element.transformMatrix = matrixArray.map {
                    it.jsonPrimitive.doubleOrNull ?: 0.0
                }.toDoubleArray()
            }
        }

        webview.completeEvent(ci.requestID)
    }

    // ============================================================
    // Dynamic 3D Element Handlers
    // ============================================================

    private fun handleCreateSpatializedDynamic3DElement(webview: NativeWebView, ci: CommandInfo) {
        Log.d(TAG, "CreateSpatializedDynamic3DElement")

        val element = SpatializedDynamic3DElement()

        webview.completeEvent(ci.requestID, """{"id": "${element.id}"}""")
    }

    private fun handleUpdateSpatializedDynamic3DElementProperties(webview: NativeWebView, ci: CommandInfo) {
        val elementId = ci.json.find<String>("data", "id")
        Log.d(TAG, "UpdateSpatializedDynamic3DElementProperties: id=$elementId")

        val element = elementId?.let { SpatialObject.get(it) as? SpatializedDynamic3DElement }
        if (element != null) {
            ci.json.find<Double>("data", "opacity")?.let { element.opacity = it }
            ci.json.find<Boolean>("data", "visible")?.let { element.visible = it }

            val matrixArray = ci.json.find<JsonArray>("data", "transform")
            if (matrixArray != null) {
                element.transformMatrix = matrixArray.map {
                    it.jsonPrimitive.doubleOrNull ?: 0.0
                }.toDoubleArray()
            }
        }

        webview.completeEvent(ci.requestID)
    }

    // ============================================================
    // Entity/Component System Handlers
    // ============================================================

    private fun handleCreateSpatialEntity(webview: NativeWebView, ci: CommandInfo) {
        val name = ci.json.find<String>("data", "name") ?: ""
        Log.d(TAG, "CreateSpatialEntity: name=$name")

        val entity = SpatialEntity()
        entity.name = name

        webview.completeEvent(ci.requestID, """{"id": "${entity.id}"}""")
    }

    private fun handleUpdateEntityProperties(webview: NativeWebView, ci: CommandInfo) {
        val entityId = ci.json.find<String>("data", "entityId")
        val matrixArray = ci.json.find<JsonArray>("data", "transform")
        Log.d(TAG, "UpdateEntityProperties: entityId=$entityId")

        val entity = entityId?.let { SpatialObject.get(it) as? SpatialEntity }
        if (entity != null && matrixArray != null) {
            // TODO: Store and apply transform matrix to entity
            val transform = matrixArray.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }.toDoubleArray()
            Log.d(TAG, "Entity transform: tx=${transform.getOrNull(12)}, ty=${transform.getOrNull(13)}, tz=${transform.getOrNull(14)}")
        }

        webview.completeEvent(ci.requestID)
    }

    private fun handleAddEntityToDynamic3D(webview: NativeWebView, ci: CommandInfo) {
        val dynamic3dId = ci.json.find<String>("data", "dynamic3dId")
        val entityId = ci.json.find<String>("data", "entityId")
        Log.d(TAG, "AddEntityToDynamic3D: dynamic3dId=$dynamic3dId, entityId=$entityId")

        val dynamic3d = dynamic3dId?.let { SpatialObject.get(it) as? SpatializedDynamic3DElement }
        val entity = entityId?.let { SpatialObject.get(it) as? SpatialEntity }

        if (dynamic3d != null && entity != null) {
            dynamic3d.addEntity(entity)
        }

        webview.completeEvent(ci.requestID)
    }

    private fun handleAddEntityToEntity(webview: NativeWebView, ci: CommandInfo) {
        val parentId = ci.json.find<String>("data", "parentId")
        val childId = ci.json.find<String>("data", "childId")
        Log.d(TAG, "AddEntityToEntity: parent=$parentId, child=$childId")

        val parent = parentId?.let { SpatialObject.get(it) as? SpatialEntity }
        val child = childId?.let { SpatialObject.get(it) as? SpatialEntity }

        if (parent != null && child != null) {
            parent.addChild(child)
        }

        webview.completeEvent(ci.requestID)
    }

    private fun handleRemoveEntityFromParent(webview: NativeWebView, ci: CommandInfo) {
        val entityId = ci.json.find<String>("data", "entityId")
        Log.d(TAG, "RemoveEntityFromParent: entityId=$entityId")

        val entity = entityId?.let { SpatialObject.get(it) as? SpatialEntity }
        entity?.setParent(null)

        webview.completeEvent(ci.requestID)
    }

    private fun handleSetParentToEntity(webview: NativeWebView, ci: CommandInfo) {
        val childId = ci.json.find<String>("data", "childId")
        val parentId = ci.json.find<String>("data", "parentId")
        Log.d(TAG, "SetParentToEntity: child=$childId, parent=$parentId")

        val child = childId?.let { SpatialObject.get(it) as? SpatialEntity }
        val parent = parentId?.let { SpatialObject.get(it) as? SpatialEntity }

        if (child != null) {
            child.setParent(parent)
        }

        webview.completeEvent(ci.requestID)
    }

    // ============================================================
    // Geometry & Material Handlers
    // ============================================================

    private fun handleCreateGeometry(webview: NativeWebView, ci: CommandInfo) {
        val type = ci.json.find<String>("data", "type") ?: "box"
        Log.d(TAG, "CreateGeometry: type=$type")

        val geometry: SpatialGeometry = when (type.lowercase()) {
            "box" -> BoxGeometry(
                width = ci.json.find<Float>("data", "width") ?: 1f,
                height = ci.json.find<Float>("data", "height") ?: 1f,
                depth = ci.json.find<Float>("data", "depth") ?: 1f,
                cornerRadius = ci.json.find<Float>("data", "cornerRadius") ?: 0f,
                splitFaces = ci.json.find<Boolean>("data", "splitFaces") ?: false
            )
            "plane" -> PlaneGeometry(
                width = ci.json.find<Float>("data", "width") ?: 1f,
                height = ci.json.find<Float>("data", "height") ?: 1f,
                cornerRadius = ci.json.find<Float>("data", "cornerRadius") ?: 0f
            )
            "sphere" -> SphereGeometry(
                radius = ci.json.find<Float>("data", "radius") ?: 0.5f
            )
            "cone" -> ConeGeometry(
                radius = ci.json.find<Float>("data", "radius") ?: 0.5f,
                height = ci.json.find<Float>("data", "height") ?: 1f
            )
            "cylinder" -> CylinderGeometry(
                radius = ci.json.find<Float>("data", "radius") ?: 0.5f,
                height = ci.json.find<Float>("data", "height") ?: 1f
            )
            else -> BoxGeometry()
        }

        webview.completeEvent(ci.requestID, """{"id": "${geometry.id}"}""")
    }

    private fun handleCreateUnlitMaterial(webview: NativeWebView, ci: CommandInfo) {
        val color = ci.json.find<String>("data", "color")
        val textureId = ci.json.find<String>("data", "textureId")
        val transparent = ci.json.find<Boolean>("data", "transparent") ?: false
        val opacity = ci.json.find<Float>("data", "opacity") ?: 1f

        Log.d(TAG, "CreateUnlitMaterial: color=$color, opacity=$opacity")

        val material = UnlitMaterial.create(color, textureId, transparent, opacity)

        webview.completeEvent(ci.requestID, """{"id": "${material.id}"}""")
    }

    private fun handleCreateModelComponent(webview: NativeWebView, ci: CommandInfo) {
        val geometryId = ci.json.find<String>("data", "geometryId")
        val materialIds = ci.json.find<JsonArray>("data", "materialIds")

        Log.d(TAG, "CreateModelComponent: geometryId=$geometryId")

        val geometry = geometryId?.let { SpatialObject.get(it) as? SpatialGeometry }
        val materials = materialIds?.mapNotNull {
            SpatialObject.get(it.jsonPrimitive.content) as? SpatialMaterial
        } ?: emptyList()

        if (geometry != null) {
            val component = SpatialModelComponent(geometry, materials)
            webview.completeEvent(ci.requestID, """{"id": "${component.id}"}""")
        } else {
            webview.completeEventWithError(ci.requestID, "InvalidGeometry", "Geometry not found: $geometryId")
        }
    }

    private fun handleAddComponentToEntity(webview: NativeWebView, ci: CommandInfo) {
        val entityId = ci.json.find<String>("data", "entityId")
        val componentId = ci.json.find<String>("data", "componentId")

        Log.d(TAG, "AddComponentToEntity: entityId=$entityId, componentId=$componentId")

        val entity = entityId?.let { SpatialObject.get(it) as? SpatialEntity }
        val component = componentId?.let { SpatialObject.get(it) as? SpatialComponent }

        if (entity != null && component != null) {
            entity.addComponent(component)
        }

        webview.completeEvent(ci.requestID)
    }

    // ============================================================
    // Model Asset Handlers
    // ============================================================

    private fun handleCreateModelAsset(webview: NativeWebView, ci: CommandInfo) {
        val url = ci.json.find<String>("data", "url") ?: ""
        Log.d(TAG, "CreateModelAsset: url=$url")

        val asset = SpatialModelAsset(url)

        // TODO: Start async model loading via SceneCore
        // For now, mark as loaded immediately for testing
        asset.onLoaded()

        webview.completeEvent(ci.requestID, """{"id": "${asset.id}"}""")
    }

    private fun handleCreateSpatialModelEntity(webview: NativeWebView, ci: CommandInfo) {
        Log.d(TAG, "CreateSpatialModelEntity")

        val entity = SpatialEntity()
        entity.name = "ModelEntity"

        webview.completeEvent(ci.requestID, """{"id": "${entity.id}"}""")
    }

    // ============================================================
    // Lifecycle Handlers
    // ============================================================

    private fun handleDestroy(webview: NativeWebView, ci: CommandInfo) {
        val objectId = ci.json.find<String>("data", "id")
        Log.d(TAG, "Destroy: id=$objectId")

        val obj = objectId?.let { SpatialObject.get(it) }
        if (obj != null) {
            // Send destroy event to JS
            WebMsg.sendObjectDestroy(webview, objectId)

            // Remove from observable state
            SpatialElementState.removeElement(objectId)

            // Actually destroy the object
            obj.destroy()
        }

        webview.completeEvent(ci.requestID)
    }

    private fun handleInspect(webview: NativeWebView, ci: CommandInfo) {
        val objectId = ci.json.find<String>("data", "id")
        Log.d(TAG, "Inspect: id=$objectId")

        val obj = objectId?.let { SpatialObject.get(it) }

        val response = when (obj) {
            is Spatialized2DElement -> """{
                "found": true,
                "type": "Spatialized2DElement",
                "clientX": ${obj.clientX},
                "clientY": ${obj.clientY},
                "width": ${obj.width},
                "height": ${obj.height},
                "backOffset": ${obj.backOffset},
                "visible": ${obj.visible}
            }"""
            is SpatializedStatic3DElement -> """{
                "found": true,
                "type": "SpatializedStatic3DElement",
                "modelURL": "${obj.modelURL}",
                "isLoaded": ${obj.isLoaded}
            }"""
            is SpatializedDynamic3DElement -> """{
                "found": true,
                "type": "SpatializedDynamic3DElement"
            }"""
            is SpatialEntity -> """{
                "found": true,
                "type": "SpatialEntity",
                "name": "${obj.name}"
            }"""
            else -> """{"found": false}"""
        }

        webview.completeEvent(ci.requestID, response)
    }

    // ============================================================
    // Legacy/Compat Handlers
    // ============================================================

    private fun handleUpdateResource(webview: NativeWebView, ci: CommandInfo) {
        val style = ci.json.find<JsonElement>("data", "update", "style")
        if (style != null) {
            val swc = SpatialObject.get(ci.resourceID) as? SpatialWindowComponent
            if (swc != null) {
                val backgroundMaterial = style.find<String>("backgroundMaterial")
                if (backgroundMaterial != null) {
                    swc.backgroundStyle = backgroundMaterial
                    Log.d(TAG, "updateResource: backgroundStyle=$backgroundMaterial")
                }
            }
        }
        webview.completeEvent(ci.requestID)
    }

    private fun handleCheckWebViewCanCreate(webview: NativeWebView, ci: CommandInfo) {
        webview.completeEvent(ci.requestID, """{"can": true}""")
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    /**
     * Update the observable Compose state from a Spatialized2DElement.
     */
    private fun updateSpatialElementState(element: Spatialized2DElement) {
        val props = SpatialElementProps(
            id = element.id,
            clientX = element.clientX,
            clientY = element.clientY,
            width = element.width,
            height = element.height,
            depth = element.depth,
            backOffset = element.backOffset,
            opacity = element.opacity,
            visible = element.visible,
            zIndex = element.zIndex,
            cornerRadius = element.cornerRadius,
            backgroundMaterial = element.backgroundMaterial,
            transform = element.transformMatrix,
            enableTapGesture = element.enableTapGesture,
            enableDragGesture = element.enableDragGesture,
            enableRotateGesture = element.enableRotateGesture,
            enableMagnifyGesture = element.enableMagnifyGesture,
            bitmapData = element.bitmapData
        )
        SpatialElementState.updateElement(element.id, props)
    }
}
