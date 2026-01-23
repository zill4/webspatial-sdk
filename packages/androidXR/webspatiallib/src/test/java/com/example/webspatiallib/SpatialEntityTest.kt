package com.example.webspatiallib

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SpatialEntity and component system.
 */
class SpatialEntityTest {

    @Before
    fun setUp() {
        SpatialObject.objects.clear()
    }

    @After
    fun tearDown() {
        SpatialObject.objects.clear()
    }

    @Test
    fun `SpatialEntity creates with unique ID`() {
        val entity1 = SpatialEntity()
        val entity2 = SpatialEntity()

        assertNotEquals(entity1.id, entity2.id)
    }

    @Test
    fun `SpatialEntity can have name`() {
        val entity = SpatialEntity()
        entity.name = "TestEntity"

        assertEquals("TestEntity", entity.name)
    }

    @Test
    fun `SpatialEntity can add and remove children`() {
        val parent = SpatialEntity()
        val child1 = SpatialEntity()
        val child2 = SpatialEntity()

        parent.addChild(child1)
        parent.addChild(child2)

        assertTrue(parent.getChildren().contains(child1))
        assertTrue(parent.getChildren().contains(child2))
        assertEquals(parent, child1.parent)
        assertEquals(parent, child2.parent)
        assertEquals(2, parent.getChildren().size)

        parent.removeChild(child1)

        assertFalse(parent.getChildren().contains(child1))
        assertTrue(parent.getChildren().contains(child2))
        assertNull(child1.parent)
    }

    @Test
    fun `SpatialEntity setParent updates relationships`() {
        val parent1 = SpatialEntity()
        val parent2 = SpatialEntity()
        val child = SpatialEntity()

        child.setParent(parent1)

        assertEquals(parent1, child.parent)
        assertTrue(parent1.getChildren().contains(child))

        child.setParent(parent2)

        assertEquals(parent2, child.parent)
        assertFalse(parent1.getChildren().contains(child))
        assertTrue(parent2.getChildren().contains(child))
    }

    @Test
    fun `SpatialEntity destroy removes from parent and destroys children`() {
        val parent = SpatialEntity()
        val child1 = SpatialEntity()
        val child2 = SpatialEntity()
        val grandchild = SpatialEntity()

        parent.addChild(child1)
        parent.addChild(child2)
        child1.addChild(grandchild)

        val child1Id = child1.id
        val grandchildId = grandchild.id

        child1.destroy()

        // Child1 should be removed from parent
        assertFalse(parent.getChildren().contains(child1))
        // Grandchild should also be destroyed
        assertNull(SpatialObject.get(child1Id))
        assertNull(SpatialObject.get(grandchildId))
        // Parent and child2 should still exist
        assertNotNull(SpatialObject.get(parent.id))
        assertTrue(parent.getChildren().contains(child2))
    }

    @Test
    fun `SpatialEntity can add components`() {
        val entity = SpatialEntity()
        val component = TestComponent()

        entity.addComponent(component)

        assertTrue(entity.components.contains(component))
        assertEquals(entity, component.entity)
    }

    @Test
    fun `SpatialEntity can get component by type`() {
        val entity = SpatialEntity()
        val component = TestComponent()

        entity.addComponent(component)

        val found = entity.getComponent<TestComponent>()
        assertEquals(component, found)
    }

    @Test
    fun `SpatialEntity removeComponent removes component`() {
        val entity = SpatialEntity()
        val component = TestComponent()

        entity.addComponent(component)
        entity.removeComponent(component)

        assertFalse(entity.components.contains(component))
        assertNull(component.entity)
    }

    @Test
    fun `SpatialEntity destroy cleans up components`() {
        val entity = SpatialEntity()
        val component = TestComponent()

        entity.addComponent(component)
        entity.destroy()

        assertNull(component.entity)
        assertNull(SpatialObject.get(component.id))
    }

    // Test component
    private class TestComponent : SpatialComponent() {
        override fun onAddToEntity() {
            // No-op for testing
        }
    }
}
