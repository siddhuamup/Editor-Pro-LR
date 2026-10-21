package com.cleanlr.history

import com.cleanlr.model.EditRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryItem(
    val description: String,
    val recipe: EditRecipe,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Document 5 compliant HistoryManager (Task T4):
 *
 * Architecture:
 * - 50-entry ArrayDeque limit (oldest entries evicted when exceeded)
 * - Touch-down snapshot token memento pattern (WFCatUndo.lua compliant)
 * - Redo stack cleared on any new commit (standard Lightroom behavior)
 * - Live StateFlow for canUndo / canRedo / historyCount reactive UI
 * - resetToOriginal jumps to root state
 */
class HistoryManager(initialRecipe: EditRecipe) {

    companion object {
        const val MAX_HISTORY_SIZE = 50
    }

    private val undoStack = ArrayDeque<HistoryItem>(MAX_HISTORY_SIZE + 1)
    private val redoStack = ArrayDeque<HistoryItem>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _historyCount = MutableStateFlow(1)
    val historyCount: StateFlow<Int> = _historyCount.asStateFlow()

    private val _liveRecipe = MutableStateFlow(initialRecipe)
    val liveRecipe: StateFlow<EditRecipe> = _liveRecipe.asStateFlow()

    private var currentRecipe: EditRecipe = initialRecipe
    private var touchDownSnapshot: EditRecipe? = null

    init {
        // Root base state (position 0 — never evicted)
        undoStack.addLast(HistoryItem("Original Image", initialRecipe))
        updateState()
    }

    /**
     * Called on touch down before a user begins dragging a slider.
     * Captures the exact recipe state at gesture start for comparison.
     */
    fun onTouchDown(recipe: EditRecipe) {
        touchDownSnapshot = recipe
    }

    /**
     * Live preview update during slider drag.
     * Updates liveRecipe StateFlow for real-time canvas rendering (60–120 FPS)
     * without touching undo/redo history or disk I/O.
     */
    fun updateLive(recipe: EditRecipe) {
        _liveRecipe.value = recipe
    }

    /**
     * Called on touch release when user finishes dragging a slider.
     * Only commits if actual parameters changed from the touch-down snapshot.
     */
    fun onTouchUp(latestRecipe: EditRecipe, actionName: String) {
        val snapshot = touchDownSnapshot ?: return
        touchDownSnapshot = null

        if (snapshot != latestRecipe) {
            commitState(latestRecipe, actionName)
        }
    }

    /**
     * Directly commits a discreet state change (e.g., preset applied, reset button tapped).
     * Clears redo stack and enforces 50-entry eviction policy.
     */
    fun commitState(newRecipe: EditRecipe, actionName: String) {
        undoStack.addLast(HistoryItem(actionName, newRecipe))

        // Enforce 50-entry limit — evict oldest (but never root at index 0)
        while (undoStack.size > MAX_HISTORY_SIZE + 1) {
            // Remove the second element (index 1), preserving root at index 0
            val items = undoStack.toMutableList()
            items.removeAt(1)
            undoStack.clear()
            items.forEach { undoStack.addLast(it) }
        }

        redoStack.clear()
        currentRecipe = newRecipe
        _liveRecipe.value = newRecipe
        updateState()
    }

    fun undo(): EditRecipe? {
        if (undoStack.size <= 1) return null

        val current = undoStack.removeLast()
        redoStack.addLast(current)

        val previous = undoStack.last()
        currentRecipe = previous.recipe
        _liveRecipe.value = currentRecipe
        updateState()
        return currentRecipe
    }

    fun redo(): EditRecipe? {
        if (redoStack.isEmpty()) return null

        val next = redoStack.removeLast()
        undoStack.addLast(next)
        currentRecipe = next.recipe
        _liveRecipe.value = currentRecipe
        updateState()
        return currentRecipe
    }

    fun resetToOriginal(): EditRecipe {
        val root = undoStack.first()
        commitState(root.recipe, "Reset to Original")
        return root.recipe
    }

    /** Peek at the current committed recipe without modifying history. */
    fun currentRecipe(): EditRecipe = currentRecipe

    /** Get the full undo history descriptions for UI display. */
    fun getHistoryDescriptions(): List<String> = undoStack.map { it.description }

    /**
     * Tip 122: Restore history state directly to a specific historical step index.
     */
    fun restoreToStep(index: Int): EditRecipe? {
        if (index !in 0 until undoStack.size) return null
        while (undoStack.size - 1 > index) {
            val popped = undoStack.removeLast()
            redoStack.addLast(popped)
        }
        val target = undoStack.last()
        currentRecipe = target.recipe
        _liveRecipe.value = currentRecipe
        updateState()
        return currentRecipe
    }

    private fun updateState() {
        _canUndo.value = undoStack.size > 1
        _canRedo.value = redoStack.isNotEmpty()
        _historyCount.value = undoStack.size
    }
}
