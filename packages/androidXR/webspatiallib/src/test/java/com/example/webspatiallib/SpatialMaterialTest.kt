package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SpatialMaterial types.
 * Note: Tests that require Android Color class are marked for instrumented tests.
 */
class SpatialMaterialTest {

    @Before
    fun setUp() {
        SpatialObject.objects.clear()
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `UnlitMaterial has correct material type`() {
        // Create directly without color parsing (which requires Android)
        val material = UnlitMaterial(
            color = 0xFFFFFFFF.toInt(),
            textureId = null,
            transparent = false,
            opacity = 1f
        )

        assertEquals("unlit", material.materialType)
    }

    @Test
    fun `UnlitMaterial stores texture ID`() {
        val material = UnlitMaterial(
            color = 0xFFFFFFFF.toInt(),
            textureId = "texture_123",
            transparent = false,
            opacity = 1f
        )

        assertEquals("texture_123", material.textureId)
    }

    @Test
    fun `UnlitMaterial stores transparency settings`() {
        val material = UnlitMaterial(
            color = 0xFFFFFFFF.toInt(),
            textureId = null,
            transparent = true,
            opacity = 0.5f
        )

        assertTrue(material.transparent)
        assertEquals(0.5f, material.opacity)
    }

    @Test
    fun `UnlitMaterial stores color value`() {
        val color = 0xFF00FF00.toInt() // Green
        val material = UnlitMaterial(
            color = color,
            textureId = null,
            transparent = false,
            opacity = 1f
        )

        assertEquals(color, material.color)
    }

    @Test
    fun `Material registers in SpatialObject registry`() {
        val material = UnlitMaterial(
            color = 0xFFFFFFFF.toInt(),
            textureId = null,
            transparent = false,
            opacity = 1f
        )

        assertNotNull(SpatialObject.get(material.id))
    }

    @Test
    fun `Material can be destroyed`() {
        val material = UnlitMaterial(
            color = 0xFFFFFFFF.toInt(),
            textureId = null,
            transparent = false,
            opacity = 1f
        )
        val id = material.id

        material.destroy()

        assertNull(SpatialObject.get(id))
    }
}
