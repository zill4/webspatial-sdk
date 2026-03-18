package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Unit tests for Spatialized2DElement and related classes.
 */
class Spatialized2DElementTest {

    @Before
    fun setUp() {
        SpatialObject.objects.clear()
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `Spatialized2DElement creates with default values`() {
        val element = Spatialized2DElement(WeakReference(null))

        assertEquals(0.0, element.clientX, 0.01)
        assertEquals(0.0, element.clientY, 0.01)
        assertEquals(0.0, element.width, 0.01)
        assertEquals(0.0, element.height, 0.01)
        assertEquals(0.0, element.backOffset, 0.01)
        assertEquals(1.0, element.opacity, 0.01)
        assertTrue(element.visible)
        assertEquals("none", element.backgroundMaterial)
    }

    @Test
    fun `Spatialized2DElement updateProperties updates values`() {
        val element = Spatialized2DElement(WeakReference(null))

        element.updateProperties(
            clientX = 100.0,
            clientY = 200.0,
            width = 300.0,
            height = 400.0,
            backOffset = 50.0,
            opacity = 0.8,
            visible = false,
            backgroundMaterial = "translucent"
        )

        assertEquals(100.0, element.clientX, 0.01)
        assertEquals(200.0, element.clientY, 0.01)
        assertEquals(300.0, element.width, 0.01)
        assertEquals(400.0, element.height, 0.01)
        assertEquals(50.0, element.backOffset, 0.01)
        assertEquals(0.8, element.opacity, 0.01)
        assertFalse(element.visible)
        assertEquals("translucent", element.backgroundMaterial)
    }

    @Test
    fun `Spatialized2DElement updateGestureFlags updates flags`() {
        val element = Spatialized2DElement(WeakReference(null))

        assertFalse(element.enableTapGesture)
        assertFalse(element.enableDragGesture)
        assertFalse(element.enableRotateGesture)
        assertFalse(element.enableMagnifyGesture)

        element.updateGestureFlags(
            tap = true,
            drag = true,
            rotate = true,
            magnify = true
        )

        assertTrue(element.enableTapGesture)
        assertTrue(element.enableDragGesture)
        assertTrue(element.enableRotateGesture)
        assertTrue(element.enableMagnifyGesture)
    }

    @Test
    fun `Spatialized2DElement updateTransform stores matrix`() {
        val element = Spatialized2DElement(WeakReference(null))

        // Identity matrix with translation
        val matrix = doubleArrayOf(
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            10.0, 20.0, 30.0, 1.0 // Translation at indices 12, 13, 14
        )

        element.updateTransform(matrix)

        assertNotNull(element.transformMatrix)
        val translation = element.getTranslation()
        assertEquals(10.0, translation.first, 0.01)
        assertEquals(20.0, translation.second, 0.01)
        assertEquals(30.0, translation.third, 0.01)
    }

    @Test
    fun `Spatialized2DElement getDepthDp converts backOffset`() {
        val element = Spatialized2DElement(WeakReference(null))
        element.updateProperties(backOffset = 100.0)

        // backOffset * 0.5 = depth in dp
        assertEquals(50f, element.getDepthDp(), 0.01f)
    }

    @Test
    fun `Spatialized2DElement can have children`() {
        val parent = Spatialized2DElement(WeakReference(null))
        val child1 = Spatialized2DElement(WeakReference(null))
        val child2 = Spatialized2DElement(WeakReference(null))

        child1.addToElement(parent)
        child2.addToElement(parent)

        assertEquals(2, parent.getChildren().size)
        assertTrue(parent.getChildren().contains(child1))
        assertTrue(parent.getChildren().contains(child2))
        assertEquals(parent, child1.parentElement)
        assertEquals(parent, child2.parentElement)
    }

    @Test
    fun `Spatialized2DElement destroy cleans up children`() {
        val parent = Spatialized2DElement(WeakReference(null))
        val child = Spatialized2DElement(WeakReference(null))

        child.addToElement(parent)
        val childId = child.id

        parent.destroy()

        assertNull(SpatialObject.get(childId))
    }

    @Test
    fun `SpatializedStatic3DElement creates with modelURL`() {
        val element = SpatializedStatic3DElement("https://example.com/model.glb")

        assertEquals("https://example.com/model.glb", element.modelURL)
        assertFalse(element.isLoaded)
        assertNull(element.loadError)
    }

    @Test
    fun `SpatializedStatic3DElement tracks load status`() {
        val element = SpatializedStatic3DElement("https://example.com/model.glb")

        element.onLoadSuccess()
        assertTrue(element.isLoaded)
        assertNull(element.loadError)

        element.onLoadFailure("Network error")
        assertFalse(element.isLoaded)
        assertEquals("Network error", element.loadError)
    }

    @Test
    fun `SpatializedDynamic3DElement has rootEntity`() {
        val dynamic3d = SpatializedDynamic3DElement()

        assertNotNull(dynamic3d.rootEntity)
    }

    @Test
    fun `SpatializedDynamic3DElement can add entities`() {
        val dynamic3d = SpatializedDynamic3DElement()
        val entity = SpatialEntity()

        dynamic3d.addEntity(entity)

        assertTrue(dynamic3d.rootEntity.getChildren().contains(entity))
    }
}
