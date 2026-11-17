package com.cleanlr.schema

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Verification test for authentic mined vector drawables and SVGs from Adobe Lightroom APK.
 */
class MinedIconsTest {

    @Test
    fun testAuthenticDrawablesPresentAndValid() {
        // Resolve res/drawable relative to project root
        val resDrawableDir = File("src/main/res/drawable").let {
            if (it.exists()) it else File("app/src/main/res/drawable")
        }
        assertTrue("res/drawable directory must exist", resDrawableDir.exists())

        val xmlFiles = resDrawableDir.listFiles { f -> f.extension == "xml" } ?: emptyArray()
        assertEquals("All 183 application drawables must be present", 183, xmlFiles.size)

        // Verify critical core editing icons contain authentic vector path data
        val coreIcons = listOf(
            "ic_lr_auto.xml",
            "ic_lr_light.xml",
            "ic_lr_color.xml",
            "ic_lr_effects.xml",
            "ic_lr_detail.xml",
            "ic_lr_optics.xml",
            "ic_lr_geometry_upright.xml",
            "ic_lr_crop.xml",
            "ic_lr_heal.xml",
            "ic_lr_masking.xml",
            "ic_lr_presets.xml",
            "ic_lr_profiles.xml",
            "ic_lr_versions.xml"
        )

        for (iconName in coreIcons) {
            val iconFile = File(resDrawableDir, iconName)
            assertTrue("Core icon '$iconName' must exist", iconFile.exists())
            val content = iconFile.readText()
            assertTrue("Icon '$iconName' must be a <vector>", content.contains("<vector"))
            assertTrue("Icon '$iconName' must contain pathData", content.contains("android:pathData="))
            assertTrue("Icon '$iconName' must contain viewport", content.contains("android:viewportWidth="))
        }
    }

    @Test
    fun testMinedSvgAssetsPackaged() {
        val assetsSvgDir = File("src/main/assets/mined_svg").let {
            if (it.exists()) it else File("app/src/main/assets/mined_svg")
        }
        assertTrue("assets/mined_svg directory must exist", assetsSvgDir.exists())

        val svgFiles = assetsSvgDir.listFiles { f -> f.extension == "svg" } ?: emptyArray()
        assertTrue("Must contain over 1200 authentic mined SVGs", svgFiles.size >= 1200)

        // Spot check authentic SVGs
        val sampleSvgs = listOf("svg_auto.svg", "svg_crop.svg", "svg_color.svg", "svg_light.svg")
        for (sample in sampleSvgs) {
            val svgFile = File(assetsSvgDir, sample)
            assertTrue("Sample SVG '$sample' must exist in assets", svgFile.exists())
            assertTrue("Sample SVG '$sample' must be non-empty", svgFile.length() > 50)
        }
    }
}
