package com.cleanlr.profile

import kotlinx.serialization.Serializable

/**
 * Represents an authentic photographic creative Profile mined from Adobe Lightroom Mobile APK.
 */
@Serializable
data class ProfileItem(
    val id: String,
    val name: String,
    val group: String,
    val isMonochrome: Boolean = false
)

/**
 * CleanLightroom Profile Manager
 * Manages all 70 authentic Lightroom profiles mined from APK.
 */
object ProfileManager {

    val GROUPS: List<String> = listOf("Basic", "Artistic", "B&W", "Modern", "Vintage", "Film", "Adaptive")

    val ALL_PROFILES: List<ProfileItem> = listOf(
        ProfileItem(
            id = "profile_adaptive_adaptive_b_w",
            name = "Adaptive B&W",
            group = "Adaptive",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_adaptive_adaptive_color",
            name = "Adaptive Color",
            group = "Adaptive",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_basic_adobe_color",
            name = "Adobe Color",
            group = "Basic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_basic_adobe_landscape",
            name = "Adobe Landscape",
            group = "Basic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_basic_adobe_monochrome",
            name = "Adobe Monochrome",
            group = "Basic",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_basic_adobe_neutral",
            name = "Adobe Neutral",
            group = "Basic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_basic_adobe_portrait",
            name = "Adobe Portrait",
            group = "Basic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_basic_adobe_vivid",
            name = "Adobe Vivid",
            group = "Basic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_01",
            name = "Artistic 01",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_02",
            name = "Artistic 02",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_03",
            name = "Artistic 03",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_04",
            name = "Artistic 04",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_05",
            name = "Artistic 05",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_06",
            name = "Artistic 06",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_07",
            name = "Artistic 07",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_artistic_artistic_08",
            name = "Artistic 08",
            group = "Artistic",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_b_w_b_w_01",
            name = "B&W 01",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_02",
            name = "B&W 02",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_03",
            name = "B&W 03",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_04",
            name = "B&W 04",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_05",
            name = "B&W 05",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_06",
            name = "B&W 06",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_07",
            name = "B&W 07",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_08",
            name = "B&W 08",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_09",
            name = "B&W 09",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_10",
            name = "B&W 10",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_11",
            name = "B&W 11",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_12",
            name = "B&W 12",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_blue_filter_v2",
            name = "B&W Blue Filter v2",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_blue_filter",
            name = "B&W Blue Filter",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_green_filter_v2",
            name = "B&W Green Filter v2",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_green_filter",
            name = "B&W Green Filter",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_orange_filter_v2",
            name = "B&W Orange Filter v2",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_orange_filter",
            name = "B&W Orange Filter",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_red_filter_v2",
            name = "B&W Red Filter v2",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_red_filter",
            name = "B&W Red Filter",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_yellow_filter_v2",
            name = "B&W Yellow Filter v2",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_b_w_b_w_yellow_filter",
            name = "B&W Yellow Filter",
            group = "B&W",
            isMonochrome = true
        ),
        ProfileItem(
            id = "profile_modern_modern_01",
            name = "Modern 01",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_02",
            name = "Modern 02",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_03",
            name = "Modern 03",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_04",
            name = "Modern 04",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_05",
            name = "Modern 05",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_06",
            name = "Modern 06",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_07",
            name = "Modern 07",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_08",
            name = "Modern 08",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_09",
            name = "Modern 09",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_modern_modern_10",
            name = "Modern 10",
            group = "Modern",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_01",
            name = "Vintage 01",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_02",
            name = "Vintage 02",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_03",
            name = "Vintage 03",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_04",
            name = "Vintage 04",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_05",
            name = "Vintage 05",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_06",
            name = "Vintage 06",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_07",
            name = "Vintage 07",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_08",
            name = "Vintage 08",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_09",
            name = "Vintage 09",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_vintage_vintage_10",
            name = "Vintage 10",
            group = "Vintage",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_01",
            name = "Film-Inspired 01",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_02",
            name = "Film-Inspired 02",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_03",
            name = "Film-Inspired 03",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_04",
            name = "Film-Inspired 04",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_05",
            name = "Film-Inspired 05",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_06",
            name = "Film-Inspired 06",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_07",
            name = "Film-Inspired 07",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_08",
            name = "Film-Inspired 08",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_09",
            name = "Film-Inspired 09",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_10",
            name = "Film-Inspired 10",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_11",
            name = "Film-Inspired 11",
            group = "Film",
            isMonochrome = false
        ),
        ProfileItem(
            id = "profile_film_film_inspired_12",
            name = "Film-Inspired 12",
            group = "Film",
            isMonochrome = false
        )
    )

    fun getProfilesByGroup(group: String): List<ProfileItem> {
        val target = if (group.equals("Adobe Raw", ignoreCase = true)) "Basic" else group
        return ALL_PROFILES.filter { it.group.equals(target, ignoreCase = true) }
    }

    fun findProfileByName(name: String): ProfileItem? =
        ALL_PROFILES.find { it.name.equals(name, ignoreCase = true) }

    fun isProfileMonochrome(name: String): Boolean =
        findProfileByName(name)?.isMonochrome ?: name.contains("Monochrome", ignoreCase = true) || name.startsWith("B&W", ignoreCase = true)
}
