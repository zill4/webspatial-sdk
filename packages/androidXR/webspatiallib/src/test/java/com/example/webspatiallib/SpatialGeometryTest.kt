package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SpatialGeometry types.
 */
class SpatialGeometryTest {

    @Before
    fun setUp() {
        SpatialObject.objects.clear()
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `BoxGeometry creates with default values`() {
        val box = BoxGeometry()

        assertEquals(1f, box.width)
        assertEquals(1f, box.height)
        assertEquals(1f, box.depth)
        assertEquals(0f, box.cornerRadius)
        assertFalse(box.splitFaces)
        assertEquals("box", box.geometryType)
    }

    @Test
    fun `BoxGeometry creates with custom values`() {
        val box = BoxGeometry(
            width = 2f,
            height = 3f,
            depth = 4f,
            cornerRadius = 0.1f,
            splitFaces = true
        )

        assertEquals(2f, box.width)
        assertEquals(3f, box.height)
        assertEquals(4f, box.depth)
        assertEquals(0.1f, box.cornerRadius)
        assertTrue(box.splitFaces)
    }

    @Test
    fun `PlaneGeometry creates correctly`() {
        val plane = PlaneGeometry(width = 5f, height = 3f, cornerRadius = 0.2f)

        assertEquals(5f, plane.width)
        assertEquals(3f, plane.height)
        assertEquals(0.2f, plane.cornerRadius)
        assertEquals("plane", plane.geometryType)
    }

    @Test
    fun `SphereGeometry creates correctly`() {
        val sphere = SphereGeometry(radius = 2.5f)

        assertEquals(2.5f, sphere.radius)
        assertEquals("sphere", sphere.geometryType)
    }

    @Test
    fun `ConeGeometry creates correctly`() {
        val cone = ConeGeometry(radius = 1f, height = 2f)

        assertEquals(1f, cone.radius)
        assertEquals(2f, cone.height)
        assertEquals("cone", cone.geometryType)
    }

    @Test
    fun `CylinderGeometry creates correctly`() {
        val cylinder = CylinderGeometry(radius = 0.5f, height = 1.5f)

        assertEquals(0.5f, cylinder.radius)
        assertEquals(1.5f, cylinder.height)
        assertEquals("cylinder", cylinder.geometryType)
    }

    @Test
    fun `All geometries register in SpatialObject registry`() {
        val box = BoxGeometry()
        val plane = PlaneGeometry()
        val sphere = SphereGeometry()
        val cone = ConeGeometry()
        val cylinder = CylinderGeometry()

        assertNotNull(SpatialObject.get(box.id))
        assertNotNull(SpatialObject.get(plane.id))
        assertNotNull(SpatialObject.get(sphere.id))
        assertNotNull(SpatialObject.get(cone.id))
        assertNotNull(SpatialObject.get(cylinder.id))
    }
}
