package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Unit tests for SpatialScene.
 */
class SpatialSceneTest {

    @Before
    fun setUp() {
        SpatialObject.objects.clear()
        // Clear scenes too (private access, use reflection or trust it's cleared via destroy)
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `SpatialScene creates with default state`() {
        val scene = SpatialScene(WeakReference(null))

        assertEquals(SpatialSceneState.VISIBLE, scene.state.value)
        assertEquals(0.0, scene.cornerRadius, 0.01)
        assertEquals(1.0, scene.opacity, 0.01)
        assertEquals("none", scene.backgroundMaterial)
    }

    @Test
    fun `SpatialScene getStateString returns correct string`() {
        val scene = SpatialScene(WeakReference(null))

        scene.state.value = SpatialSceneState.IDLE
        assertEquals("idle", scene.getStateString())

        scene.state.value = SpatialSceneState.PENDING
        assertEquals("pending", scene.getStateString())

        scene.state.value = SpatialSceneState.WILL_VISIBLE
        assertEquals("willVisible", scene.getStateString())

        scene.state.value = SpatialSceneState.VISIBLE
        assertEquals("visible", scene.getStateString())

        scene.state.value = SpatialSceneState.FAIL
        assertEquals("fail", scene.getStateString())
    }

    @Test
    fun `SpatialScene updateProperties updates values`() {
        val scene = SpatialScene(WeakReference(null))

        scene.updateProperties(
            cornerRadius = 10.0,
            opacity = 0.5,
            backgroundMaterial = "translucent"
        )

        assertEquals(10.0, scene.cornerRadius, 0.01)
        assertEquals(0.5, scene.opacity, 0.01)
        assertEquals("translucent", scene.backgroundMaterial)
    }

    @Test
    fun `SpatialScene updateConfig updates configuration`() {
        val scene = SpatialScene(WeakReference(null))

        scene.updateConfig(
            defaultWidth = 1920.0,
            defaultHeight = 1080.0,
            resizable = false,
            worldScaling = "fixed",
            worldAlignment = "gravity"
        )

        assertEquals(1920.0, scene.defaultWidth, 0.01)
        assertEquals(1080.0, scene.defaultHeight, 0.01)
        assertFalse(scene.resizable)
        assertEquals("fixed", scene.worldScaling)
        assertEquals("gravity", scene.worldAlignment)
    }

    @Test
    fun `SpatialScene can add 2D elements`() {
        val scene = SpatialScene(WeakReference(null))
        val element1 = Spatialized2DElement(WeakReference(null))
        val element2 = Spatialized2DElement(WeakReference(null))

        scene.addSpatialized2DElement(element1)
        scene.addSpatialized2DElement(element2)

        assertEquals(2, scene.spatialized2DElements.size)
        assertTrue(scene.spatialized2DElements.contains(element1))
        assertTrue(scene.spatialized2DElements.contains(element2))
        assertEquals(scene, element1.parentScene)
    }

    @Test
    fun `SpatialScene can get 2D element by ID`() {
        val scene = SpatialScene(WeakReference(null))
        val element = Spatialized2DElement(WeakReference(null))

        scene.addSpatialized2DElement(element)

        val found = scene.getSpatialized2DElement(element.id)
        assertEquals(element, found)
    }

    @Test
    fun `SpatialScene can remove 2D element`() {
        val scene = SpatialScene(WeakReference(null))
        val element = Spatialized2DElement(WeakReference(null))

        scene.addSpatialized2DElement(element)
        scene.removeSpatialized2DElement(element.id)

        assertEquals(0, scene.spatialized2DElements.size)
        assertNull(scene.getSpatialized2DElement(element.id))
    }

    @Test
    fun `SpatialScene can add static 3D elements`() {
        val scene = SpatialScene(WeakReference(null))
        val element = SpatializedStatic3DElement("https://example.com/model.glb")

        scene.addStatic3DElement(element)

        assertEquals(1, scene.static3DElements.size)
        assertTrue(scene.static3DElements.contains(element))
        assertEquals(scene, element.parentScene)
    }

    @Test
    fun `SpatialScene can add dynamic 3D elements`() {
        val scene = SpatialScene(WeakReference(null))
        val element = SpatializedDynamic3DElement()

        scene.addDynamic3DElement(element)

        assertEquals(1, scene.dynamic3DElements.size)
        assertTrue(scene.dynamic3DElements.contains(element))
        assertEquals(scene, element.parentScene)
    }

    @Test
    fun `SpatialScene getMaxBackOffset returns max of all elements`() {
        val scene = SpatialScene(WeakReference(null))
        val element1 = Spatialized2DElement(WeakReference(null))
        val element2 = Spatialized2DElement(WeakReference(null))
        val element3 = Spatialized2DElement(WeakReference(null))

        element1.updateProperties(backOffset = 10.0)
        element2.updateProperties(backOffset = 50.0)
        element3.updateProperties(backOffset = 30.0)

        scene.addSpatialized2DElement(element1)
        scene.addSpatialized2DElement(element2)
        scene.addSpatialized2DElement(element3)

        assertEquals(50.0, scene.getMaxBackOffset(), 0.01)
    }

    @Test
    fun `SpatialScene getElementsSortedByDepth returns sorted list`() {
        val scene = SpatialScene(WeakReference(null))
        val element1 = Spatialized2DElement(WeakReference(null))
        val element2 = Spatialized2DElement(WeakReference(null))
        val element3 = Spatialized2DElement(WeakReference(null))

        element1.updateProperties(zIndex = 3.0)
        element2.updateProperties(zIndex = 1.0)
        element3.updateProperties(zIndex = 2.0)

        scene.addSpatialized2DElement(element1)
        scene.addSpatialized2DElement(element2)
        scene.addSpatialized2DElement(element3)

        val sorted = scene.getElementsSortedByDepth()

        assertEquals(element2, sorted[0]) // zIndex 1
        assertEquals(element3, sorted[1]) // zIndex 2
        assertEquals(element1, sorted[2]) // zIndex 3
    }

    @Test
    fun `SpatialScene destroy cleans up all children`() {
        val scene = SpatialScene(WeakReference(null))
        val element2d = Spatialized2DElement(WeakReference(null))
        val element3dStatic = SpatializedStatic3DElement("https://example.com/model.glb")
        val element3dDynamic = SpatializedDynamic3DElement()

        scene.addSpatialized2DElement(element2d)
        scene.addStatic3DElement(element3dStatic)
        scene.addDynamic3DElement(element3dDynamic)

        val id2d = element2d.id
        val idStatic = element3dStatic.id
        val idDynamic = element3dDynamic.id

        scene.destroy()

        assertEquals(0, scene.spatialized2DElements.size)
        assertEquals(0, scene.static3DElements.size)
        assertEquals(0, scene.dynamic3DElements.size)

        assertNull(SpatialObject.get(id2d))
        assertNull(SpatialObject.get(idStatic))
        assertNull(SpatialObject.get(idDynamic))
    }
}
