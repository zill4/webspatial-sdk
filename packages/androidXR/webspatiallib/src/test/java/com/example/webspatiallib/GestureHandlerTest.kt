package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Unit tests for GestureHandler.
 */
class GestureHandlerTest {

    @Before
    fun setUp() {
        SpatialObject.objects.clear()
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `GestureUtils screenTo3D converts coordinates correctly`() {
        // Center of screen should be (0, 0, 0)
        val center = GestureUtils.screenTo3D(
            screenX = 960f,
            screenY = 540f,
            depth = 0f,
            panelWidth = 1920f,
            panelHeight = 1080f
        )

        assertEquals(0f, center.first, 0.01f)
        assertEquals(0f, center.second, 0.01f)
        assertEquals(0f, center.third, 0.01f)
    }

    @Test
    fun `GestureUtils screenTo3D handles top-left corner`() {
        val topLeft = GestureUtils.screenTo3D(
            screenX = 0f,
            screenY = 0f,
            depth = 0f,
            panelWidth = 1920f,
            panelHeight = 1080f
        )

        // Top-left should be negative X, positive Y
        assertTrue(topLeft.first < 0)
        assertTrue(topLeft.second > 0)
    }

    @Test
    fun `GestureUtils screenTo3D handles bottom-right corner`() {
        val bottomRight = GestureUtils.screenTo3D(
            screenX = 1920f,
            screenY = 1080f,
            depth = 0f,
            panelWidth = 1920f,
            panelHeight = 1080f
        )

        // Bottom-right should be positive X, negative Y
        assertTrue(bottomRight.first > 0)
        assertTrue(bottomRight.second < 0)
    }

    @Test
    fun `GestureUtils screenTo3D includes depth`() {
        val withDepth = GestureUtils.screenTo3D(
            screenX = 960f,
            screenY = 540f,
            depth = 0.5f,
            panelWidth = 1920f,
            panelHeight = 1080f
        )

        assertEquals(0.5f, withDepth.third, 0.01f)
    }

    @Test
    fun `RotationGestureDetector calculates rotation in radians`() {
        val detector = RotationGestureDetector(object : RotationGestureDetector.SimpleOnRotationGestureListener() {})

        // Initial rotation should be 0
        assertEquals(0f, detector.getRotationRadians(), 0.01f)
        assertEquals(0f, detector.getRotationDegrees(), 0.01f)
    }
}
