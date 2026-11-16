package com.cleanlr.schema

import com.cleanlr.profile.ProfileManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Verification test for authentic mined creative profiles from Adobe Lightroom APK.
 */
class ProfileManagerTest {

    @Test
    fun testProfileCatalogContainsAllProfiles() {
        assertEquals("Profile catalog must contain exactly 70 authentic profiles", 70, ProfileManager.ALL_PROFILES.size)
    }

    @Test
    fun testAllAuthenticGroupsPresent() {
        val groups = ProfileManager.GROUPS
        val expectedGroups = listOf("Basic", "Artistic", "B&W", "Modern", "Vintage", "Film", "Adaptive")
        expectedGroups.forEach { expected ->
            assertTrue("Expected group '$expected' not found in GROUPS", groups.contains(expected))
        }
    }

    @Test
    fun testGetProfilesByGroup() {
        val basic = ProfileManager.getProfilesByGroup("Basic")
        assertEquals(6, basic.size)

        val artistic = ProfileManager.getProfilesByGroup("Artistic")
        assertEquals(8, artistic.size)

        val bw = ProfileManager.getProfilesByGroup("B&W")
        assertEquals(22, bw.size)

        val modern = ProfileManager.getProfilesByGroup("Modern")
        assertEquals(10, modern.size)

        val vintage = ProfileManager.getProfilesByGroup("Vintage")
        assertEquals(10, vintage.size)

        val film = ProfileManager.getProfilesByGroup("Film")
        assertEquals(12, film.size)

        val adaptive = ProfileManager.getProfilesByGroup("Adaptive")
        assertEquals(2, adaptive.size)
    }

    @Test
    fun testMonochromeProfilesCorrectlyFlagged() {
        assertTrue(ProfileManager.isProfileMonochrome("Adobe Monochrome"))
        assertTrue(ProfileManager.isProfileMonochrome("B&W 01"))
        assertTrue(ProfileManager.isProfileMonochrome("B&W Blue Filter"))
        assertTrue(ProfileManager.isProfileMonochrome("Adaptive B&W"))

        assertFalse(ProfileManager.isProfileMonochrome("Adobe Color"))
        assertFalse(ProfileManager.isProfileMonochrome("Adobe Portrait"))
        assertFalse(ProfileManager.isProfileMonochrome("Artistic 01"))
        assertFalse(ProfileManager.isProfileMonochrome("Modern 01"))
        assertFalse(ProfileManager.isProfileMonochrome("Vintage 01"))
    }

    @Test
    fun testFindProfileByName() {
        val profile = ProfileManager.findProfileByName("Adobe Color")
        assertNotNull(profile)
        assertEquals("Basic", profile?.group)
        assertEquals("Adobe Color", profile?.name)
        assertFalse(profile?.isMonochrome ?: true)

        val bwProfile = ProfileManager.findProfileByName("B&W 01")
        assertNotNull(bwProfile)
        assertEquals("B&W", bwProfile?.group)
        assertTrue(bwProfile?.isMonochrome ?: false)
    }
}
