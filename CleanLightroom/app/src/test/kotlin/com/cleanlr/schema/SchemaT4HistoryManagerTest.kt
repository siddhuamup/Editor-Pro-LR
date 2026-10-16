package com.cleanlr.schema

import com.cleanlr.history.HistoryManager
import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import org.junit.Assert.*
import org.junit.Test

/**
 * Task T4: HistoryManager Audit Test (Document 5)
 *
 * Verifies:
 * 1. 50-entry ArrayDeque limit with oldest eviction (root preserved)
 * 2. Touch-down snapshot token memento pattern
 * 3. Redo stack cleared on new commit
 * 4. Live recipe StateFlow updates without touching history
 * 5. resetToOriginal jumps to root and commits as new entry
 */
class SchemaT4HistoryManagerTest {

    @Test
    fun `test 50-entry deque limit evicts oldest while preserving root`() {
        val root = EditRecipe(assetId = "root_image")
        val manager = HistoryManager(root)

        // Commit 60 entries (exceeds 50 limit)
        for (i in 1..60) {
            manager.commitState(
                EditRecipe(basic = BasicAdjustments(exposure = i * 0.1f)),
                "Edit #$i"
            )
        }

        // historyCount should be capped at 51 (root + 50 entries max)
        assertTrue(
            "History must not exceed 51 entries (root + 50), was ${manager.historyCount.value}",
            manager.historyCount.value <= 51
        )

        // Root "Original Image" must always be preserved at index 0
        val descriptions = manager.getHistoryDescriptions()
        assertEquals("Original Image", descriptions.first())

        // Latest entry must be Edit #60
        assertEquals("Edit #60", descriptions.last())
    }

    @Test
    fun `test touch-down snapshot only commits if recipe actually changed`() {
        val root = EditRecipe()
        val manager = HistoryManager(root)

        // Touch down captures snapshot
        manager.onTouchDown(root)

        // Touch up with same recipe — should NOT commit
        manager.onTouchUp(root, "No Change")
        assertEquals(1, manager.historyCount.value) // Still just root

        // Touch down again
        val modified = root.copy(basic = root.basic.copy(exposure = 1.5f))
        manager.onTouchDown(root)
        manager.onTouchUp(modified, "Exposure +1.5")
        assertEquals(2, manager.historyCount.value) // Root + 1 commit
    }

    @Test
    fun `test redo stack is cleared on new commit after undo`() {
        val root = EditRecipe()
        val manager = HistoryManager(root)

        val edit1 = root.copy(basic = root.basic.copy(exposure = 1.0f))
        val edit2 = root.copy(basic = root.basic.copy(exposure = 2.0f))
        val edit3 = root.copy(basic = root.basic.copy(exposure = 3.0f))

        manager.commitState(edit1, "Exposure 1")
        manager.commitState(edit2, "Exposure 2")
        manager.commitState(edit3, "Exposure 3")

        assertTrue(manager.canUndo.value)
        assertFalse(manager.canRedo.value)

        // Undo twice
        manager.undo()
        manager.undo()
        assertTrue(manager.canRedo.value)
        assertEquals(1.0f, manager.currentRecipe().basic.exposure, 1e-4f)

        // New commit clears redo
        val edit4 = root.copy(basic = root.basic.copy(contrast = 50f))
        manager.commitState(edit4, "Contrast 50")
        assertFalse(manager.canRedo.value) // Redo cleared!
    }

    @Test
    fun `test live recipe StateFlow updates without modifying history`() {
        val root = EditRecipe()
        val manager = HistoryManager(root)

        // Simulate slider drag: update live recipe many times
        for (i in 1..100) {
            val interim = root.copy(basic = root.basic.copy(exposure = i * 0.01f))
            manager.updateLive(interim)
        }

        // History should still be just root — live updates don't commit
        assertEquals(1, manager.historyCount.value)

        // But liveRecipe should reflect the latest value
        assertEquals(1.0f, manager.liveRecipe.value.basic.exposure, 1e-4f)
    }

    @Test
    fun `test resetToOriginal jumps to root state`() {
        val root = EditRecipe(assetId = "original_photo")
        val manager = HistoryManager(root)

        manager.commitState(
            root.copy(basic = root.basic.copy(exposure = 2.5f)),
            "Heavy Exposure"
        )
        manager.commitState(
            root.copy(basic = root.basic.copy(contrast = 80f)),
            "Heavy Contrast"
        )

        val reset = manager.resetToOriginal()
        assertEquals("original_photo", reset.assetId)
        assertEquals(0.0f, reset.basic.exposure, 1e-4f)
        assertEquals(0.0f, reset.basic.contrast, 1e-4f)
    }

    @Test
    fun `test undo and redo produce correct sequential recipes`() {
        val root = EditRecipe()
        val manager = HistoryManager(root)

        val e1 = root.copy(basic = root.basic.copy(exposure = 1.0f))
        val e2 = root.copy(basic = root.basic.copy(exposure = 2.0f))
        val e3 = root.copy(basic = root.basic.copy(exposure = 3.0f))

        manager.commitState(e1, "E1")
        manager.commitState(e2, "E2")
        manager.commitState(e3, "E3")

        // Undo 3 times: E3 -> E2 -> E1 -> root
        assertEquals(2.0f, manager.undo()!!.basic.exposure, 1e-4f)
        assertEquals(1.0f, manager.undo()!!.basic.exposure, 1e-4f)
        assertEquals(0.0f, manager.undo()!!.basic.exposure, 1e-4f)
        assertNull(manager.undo()) // Can't undo past root

        // Redo 3 times: root -> E1 -> E2 -> E3
        assertEquals(1.0f, manager.redo()!!.basic.exposure, 1e-4f)
        assertEquals(2.0f, manager.redo()!!.basic.exposure, 1e-4f)
        assertEquals(3.0f, manager.redo()!!.basic.exposure, 1e-4f)
        assertNull(manager.redo()) // No more redo
    }
}
