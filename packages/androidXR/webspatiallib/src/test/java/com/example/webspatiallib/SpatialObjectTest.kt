package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SpatialObject and its registry.
 */
class SpatialObjectTest {

    @Before
    fun setUp() {
        // Clear any existing objects
        SpatialObject.objects.clear()
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `SpatialObject generates unique IDs`() {
        val obj1 = TestSpatialObject()
        val obj2 = TestSpatialObject()
        val obj3 = TestSpatialObject()

        assertNotEquals(obj1.id, obj2.id)
        assertNotEquals(obj2.id, obj3.id)
        assertNotEquals(obj1.id, obj3.id)
    }

    @Test
    fun `SpatialObject registers itself in global registry`() {
        val obj = TestSpatialObject()

        assertTrue(SpatialObject.objects.containsKey(obj.id))
        assertEquals(obj, SpatialObject.get(obj.id))
    }

    @Test
    fun `SpatialObject destroy removes from registry`() {
        val obj = TestSpatialObject()
        val id = obj.id

        assertTrue(SpatialObject.objects.containsKey(id))

        obj.destroy()

        assertFalse(SpatialObject.objects.containsKey(id))
        assertNull(SpatialObject.get(id))
    }

    @Test
    fun `SpatialObject destroy is idempotent`() {
        val obj = TestSpatialObject()
        val id = obj.id

        obj.destroy()
        obj.destroy() // Should not throw

        assertNull(SpatialObject.get(id))
    }

    // Test implementation of SpatialObject
    private class TestSpatialObject : SpatialObject() {
        override fun onDestroy() {
            // No-op for testing
        }
    }
}
