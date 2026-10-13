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

class HistoryManager(initialRecipe: EditRecipe) {

    private val undoStack = mutableListOf<HistoryItem>()
    private val redoStack = mutableListOf<HistoryItem>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var currentRecipe: EditRecipe = initialRecipe
    private var touchDownSnapshot: EditRecipe? = null

    init {
        // Root base state
        undoStack.add(HistoryItem("Original Image", initialRecipe))
        updateState()
    }

    /**
     * Called on touch down before a user begins dragging a slider.
     */
    fun onTouchDown(recipe: EditRecipe) {
        touchDownSnapshot = recipe
    }

    /**
     * Called on touch release when user finishes dragging a slider.
     * Only commits if actual parameters changed.
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
     */
    fun commitState(newRecipe: EditRecipe, actionName: String) {
        undoStack.add(HistoryItem(actionName, newRecipe))
        redoStack.clear()
        currentRecipe = newRecipe
        updateState()
    }

    fun undo(): EditRecipe? {
        if (undoStack.size <= 1) return null

        val current = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(current)

        val previous = undoStack.last()
        currentRecipe = previous.recipe
        updateState()
        return currentRecipe
    }

    fun redo(): EditRecipe? {
        if (redoStack.isEmpty()) return null

        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(next)
        currentRecipe = next.recipe
        updateState()
        return currentRecipe
    }

    fun resetToOriginal(): EditRecipe {
        val root = undoStack.first()
        commitState(root.recipe, "Reset to Original")
        return root.recipe
    }

    private fun updateState() {
        _canUndo.value = undoStack.size > 1
        _canRedo.value = redoStack.isNotEmpty()
    }
}
