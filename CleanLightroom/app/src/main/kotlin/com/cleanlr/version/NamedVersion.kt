package com.cleanlr.version

import com.cleanlr.model.EditRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Task T5: Named Versions Data Model (WFCatVersions.lua compliant)
 *
 * Represents a discrete, user-named checkpoint in the edit lifecycle
 * (e.g., "Original", "V1 - Dramatic B&W", "Golden Hour Warmth").
 *
 * Distinct from Undo/Redo stack:
 * - Persisted independently of session history
 * - Does not get wiped by redo-clearing operations
 * - Can be switched between freely without losing current edits
 */
@Serializable
data class NamedVersion(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val recipe: EditRecipe,
    val createdAt: Long = System.currentTimeMillis(),
    val isBase: Boolean = false
)

/**
 * Task T5: VersionManager
 *
 * Coordinates named snapshot creation, retrieval, switching, and pruning
 * per Adobe Lightroom Mobile's "Versions" panel architecture.
 */
class VersionManager(initialRecipe: EditRecipe) {

    private val _versions = MutableStateFlow<List<NamedVersion>>(emptyList())
    val versions: StateFlow<List<NamedVersion>> = _versions.asStateFlow()

    private var activeVersionId: String

    init {
        val original = NamedVersion(
            id = "version_original",
            name = "Original",
            recipe = initialRecipe,
            isBase = true
        )
        _versions.value = listOf(original)
        activeVersionId = original.id
    }

    /**
     * Create a new named version from the current recipe state.
     */
    fun createVersion(name: String, recipe: EditRecipe): NamedVersion {
        val version = NamedVersion(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Version ${_versions.value.size}" },
            recipe = recipe
        )
        _versions.value = _versions.value + version
        activeVersionId = version.id
        return version
    }

    /**
     * Retrieve a specific version by its unique identifier.
     */
    fun getVersion(id: String): NamedVersion? {
        return _versions.value.find { it.id == id }
    }

    /**
     * Switch to a version and return its recipe to apply to the canvas.
     */
    fun applyVersion(id: String): EditRecipe? {
        val target = getVersion(id) ?: return null
        activeVersionId = target.id
        return target.recipe
    }

    /**
     * Delete a named version. Base versions (e.g. "Original") cannot be deleted.
     */
    fun deleteVersion(id: String): Boolean {
        val target = getVersion(id) ?: return false
        if (target.isBase) return false

        _versions.value = _versions.value.filter { it.id != id }
        if (activeVersionId == id) {
            activeVersionId = _versions.value.first().id
        }
        return true
    }

    /**
     * Rename an existing version.
     */
    fun renameVersion(id: String, newName: String): Boolean {
        val target = getVersion(id) ?: return false
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false

        _versions.value = _versions.value.map {
            if (it.id == id) it.copy(name = cleanName) else it
        }
        return true
    }

    /**
     * Overwrite an existing version with a new recipe state.
     */
    fun updateVersionRecipe(id: String, recipe: EditRecipe): Boolean {
        val target = getVersion(id) ?: return false
        if (target.isBase) return false

        _versions.value = _versions.value.map {
            if (it.id == id) it.copy(recipe = recipe) else it
        }
        return true
    }

    fun getActiveVersionId(): String = activeVersionId

    fun getVersionCount(): Int = _versions.value.size
}
