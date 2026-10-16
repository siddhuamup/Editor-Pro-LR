package com.cleanlr.schema

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.version.VersionManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Task T5: Named Versions Architecture Test (WFCatVersions.lua compliant)
 */
class SchemaT5NamedVersionsTest {

    private val baseRecipe = EditRecipe(
        basic = BasicAdjustments(exposure = 0.0f, contrast = 0.0f)
    )

    @Test
    fun testInitialBaseVersionCreated() {
        val manager = VersionManager(baseRecipe)
        val versions = manager.versions.value

        assertEquals("Should start with 1 version", 1, versions.size)
        val initial = versions.first()
        assertEquals("Original", initial.name)
        assertTrue("Initial version must be marked as base", initial.isBase)
        assertEquals("Initial version ID", "version_original", initial.id)
    }

    @Test
    fun testCreateNamedVersion() {
        val manager = VersionManager(baseRecipe)
        val editedRecipe = baseRecipe.copy(
            basic = baseRecipe.basic.copy(exposure = 1.25f, contrast = 25f)
        )

        val v1 = manager.createVersion("V1 - Bright Daylight", editedRecipe)

        assertEquals("V1 - Bright Daylight", v1.name)
        assertFalse("User-created version is not base", v1.isBase)
        assertEquals(2, manager.getVersionCount())
        assertEquals(editedRecipe, manager.getVersion(v1.id)?.recipe)
    }

    @Test
    fun testBaseVersionCannotBeDeleted() {
        val manager = VersionManager(baseRecipe)
        val baseId = manager.versions.value.first().id

        val deleted = manager.deleteVersion(baseId)

        assertFalse("Base version deletion must be prohibited", deleted)
        assertEquals(1, manager.getVersionCount())
    }

    @Test
    fun testDeleteUserVersion() {
        val manager = VersionManager(baseRecipe)
        val edited = baseRecipe.copy(basic = baseRecipe.basic.copy(temperature = 6500f))
        val v = manager.createVersion("Warm Sunset", edited)

        assertEquals(2, manager.getVersionCount())
        val deleted = manager.deleteVersion(v.id)

        assertTrue("User version should be deleted successfully", deleted)
        assertEquals(1, manager.getVersionCount())
        assertNull("Deleted version should not be found", manager.getVersion(v.id))
    }

    @Test
    fun testRenameVersion() {
        val manager = VersionManager(baseRecipe)
        val v = manager.createVersion("Temp Name", baseRecipe)

        val renamed = manager.renameVersion(v.id, "Moody B&W")
        assertTrue(renamed)
        assertEquals("Moody B&W", manager.getVersion(v.id)?.name)

        // Empty name should fail
        val emptyFail = manager.renameVersion(v.id, "   ")
        assertFalse(emptyFail)
    }

    @Test
    fun testApplyVersionRestoresRecipe() {
        val manager = VersionManager(baseRecipe)
        val warmRecipe = baseRecipe.copy(basic = baseRecipe.basic.copy(temperature = 7200f))
        val v = manager.createVersion("Warm Vintage", warmRecipe)

        val applied = manager.applyVersion(v.id)
        assertNotNull(applied)
        assertEquals(7200f, applied!!.basic.temperature, 0.001f)
        assertEquals(v.id, manager.getActiveVersionId())
    }

    @Test
    fun testUpdateVersionRecipe() {
        val manager = VersionManager(baseRecipe)
        val v = manager.createVersion("Draft", baseRecipe)

        val updatedRecipe = baseRecipe.copy(basic = baseRecipe.basic.copy(exposure = -0.75f))
        val success = manager.updateVersionRecipe(v.id, updatedRecipe)

        assertTrue(success)
        assertEquals(-0.75f, manager.getVersion(v.id)?.recipe?.basic?.exposure ?: 0f, 0.001f)
    }
}
