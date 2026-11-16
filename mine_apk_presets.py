import os
import re
import json
import zipfile
import xml.etree.ElementTree as ET
from typing import Dict, Any, List, Optional, Tuple

APK_PATH = r"C:\Users\Siddhu\Downloads\Lightroom.apk"
OUTPUT_DIR = r"mined_presets"
ASSETS_OUT = r"CleanLightroom\app\src\main\assets\mined_presets.json"
RESOURCES_OUT = r"CleanLightroom\app\src\main\resources\mined_presets.json"
KOTLIN_OUT = r"CleanLightroom\app\src\main\kotlin\com\cleanlr\preset\MinedPresetLibrary.kt"

CRS_NS = "http://ns.adobe.com/camera-raw-settings/1.0/"
RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

def to_float(val, default=0.0):
    if val is None:
        return default
    try:
        s = str(val).strip().replace("+", "")
        return float(s)
    except (ValueError, TypeError):
        return default

def parse_curve_points(root, tag_name: str) -> Optional[List[Dict[str, float]]]:
    seq = root.findall(f".//{{{CRS_NS}}}{tag_name}/{{{RDF_NS}}}Seq/{{{RDF_NS}}}li")
    if not seq:
        return None
    pts = []
    for li in seq:
        if li.text:
            parts = [p.strip() for p in li.text.split(",")]
            if len(parts) == 2:
                try:
                    pts.append({"x": float(parts[0]), "y": float(parts[1])})
                except ValueError:
                    pass
    return pts if pts else None

def parse_xmp(raw_bytes: bytes, filename: str) -> Optional[Dict[str, Any]]:
    try:
        root = ET.fromstring(raw_bytes)
    except Exception:
        return None

    desc = root.find(f".//{{{RDF_NS}}}Description")
    if desc is None:
        return None

    attrs = {}
    for k, v in desc.attrib.items():
        attr_name = k.split("}")[-1]
        attrs[attr_name] = v

    # Extract Name
    name = None
    name_el = root.find(f".//{{{CRS_NS}}}Name//{{{RDF_NS}}}li")
    if name_el is not None and name_el.text:
        name = name_el.text.strip()
    if not name:
        base = os.path.basename(filename)
        name = os.path.splitext(base)[0]
        name = re.sub(r'^(?:Color\s*-\s*|Creative\s*-\s*|B&W\s*-\s*|Style\s*-\s*)', '', name)

    # Extract Group
    group = None
    group_el = root.find(f".//{{{CRS_NS}}}Group//{{{RDF_NS}}}li")
    if group_el is not None and group_el.text:
        group = group_el.text.strip()
    if not group:
        parts = filename.replace("\\", "/").split("/")
        group = parts[-2]
    
    group_clean = group.replace(" - ", ": ").strip()
    if group_clean == "Style - Film":
        group_clean = "Style: Film-Inspired"
    elif group_clean == "Auto+ - Retro":
        group_clean = "Auto+: Retro"

    desc_text = f"Authentic Lightroom Mobile preset mined from APK ({group_clean})"

    # Grayscale
    is_gray = attrs.get("ConvertToGrayscale", "False").lower() in ("true", "1")

    # Basic adjustments
    exposure = to_float(attrs.get("Exposure2012", 0.0))
    contrast = to_float(attrs.get("Contrast2012", 0.0))
    highlights = to_float(attrs.get("Highlights2012", 0.0))
    shadows = to_float(attrs.get("Shadows2012", 0.0))
    whites = to_float(attrs.get("Whites2012", 0.0))
    blacks = to_float(attrs.get("Blacks2012", 0.0))
    temperature = to_float(attrs.get("Temperature", 5500.0), 5500.0)
    tint = to_float(attrs.get("Tint", 0.0))
    vibrance = -100.0 if is_gray else to_float(attrs.get("Vibrance", 0.0))
    saturation = -100.0 if is_gray else to_float(attrs.get("Saturation", 0.0))

    # Effects adjustments
    texture = to_float(attrs.get("Texture", 0.0))
    clarity = to_float(attrs.get("Clarity2012", 0.0))
    dehaze = to_float(attrs.get("Dehaze", 0.0))
    vignette = to_float(attrs.get("PostCropVignetteAmount", 0.0))
    grain = to_float(attrs.get("GrainAmount", 0.0))
    grain_size = to_float(attrs.get("GrainSize", 25.0), 25.0)
    grain_rough = to_float(attrs.get("GrainFrequency", 50.0), 50.0)

    # Detail adjustments
    sharpening = to_float(attrs.get("Sharpness", 0.0))
    sharpen_radius = to_float(attrs.get("SharpenRadius", 1.0), 1.0)
    sharpen_detail = to_float(attrs.get("SharpenDetail", 25.0), 25.0)
    sharpen_mask = to_float(attrs.get("SharpenEdgeMasking", 0.0))
    lum_noise = to_float(attrs.get("LuminanceSmoothing", 0.0))
    col_noise = to_float(attrs.get("ColorNoiseReduction", 25.0), 25.0)

    # Curves
    curve_comp = parse_curve_points(root, "ToneCurvePV2012") or [{"x": 0.0, "y": 0.0}, {"x": 255.0, "y": 255.0}]
    curve_r = parse_curve_points(root, "ToneCurvePV2012Red") or [{"x": 0.0, "y": 0.0}, {"x": 255.0, "y": 255.0}]
    curve_g = parse_curve_points(root, "ToneCurvePV2012Green") or [{"x": 0.0, "y": 0.0}, {"x": 255.0, "y": 255.0}]
    curve_b = parse_curve_points(root, "ToneCurvePV2012Blue") or [{"x": 0.0, "y": 0.0}, {"x": 255.0, "y": 255.0}]

    # HSL
    hsl_channels = {}
    for ch in ["Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta"]:
        h = to_float(attrs.get(f"HueAdjustment{ch}", 0.0))
        s = to_float(attrs.get(f"SaturationAdjustment{ch}", 0.0))
        l = to_float(attrs.get(f"LuminanceAdjustment{ch}", 0.0))
        hsl_channels[ch.lower()] = {"hue": h, "saturation": s, "luminance": l}

    # Color grading
    sh_hue = to_float(attrs.get("ColorGradeShadowHue", attrs.get("SplitToningShadowHue", 0.0)))
    sh_sat = to_float(attrs.get("ColorGradeShadowSat", attrs.get("SplitToningShadowSaturation", 0.0)))
    sh_lum = to_float(attrs.get("ColorGradeShadowLum", 0.0))

    hi_hue = to_float(attrs.get("ColorGradeHighlightHue", attrs.get("SplitToningHighlightHue", 0.0)))
    hi_sat = to_float(attrs.get("ColorGradeHighlightSat", attrs.get("SplitToningHighlightSaturation", 0.0)))
    hi_lum = to_float(attrs.get("ColorGradeHighlightLum", 0.0))

    mid_hue = to_float(attrs.get("ColorGradeMidtoneHue", 0.0))
    mid_sat = to_float(attrs.get("ColorGradeMidtoneSat", 0.0))
    mid_lum = to_float(attrs.get("ColorGradeMidtoneLum", 0.0))

    gl_hue = to_float(attrs.get("ColorGradeGlobalHue", 0.0))
    gl_sat = to_float(attrs.get("ColorGradeGlobalSat", 0.0))
    gl_lum = to_float(attrs.get("ColorGradeGlobalLum", 0.0))

    blending = to_float(attrs.get("ColorGradeBlending", 50.0), 50.0)
    balance = to_float(attrs.get("ColorGradeBalance", attrs.get("SplitToningBalance", 0.0)))

    safe_group = re.sub(r'[^a-zA-Z0-9]+', '_', group_clean).strip('_').lower()
    safe_name = re.sub(r'[^a-zA-Z0-9]+', '_', name).strip('_').lower()
    preset_id = f"mined_{safe_group}_{safe_name}"

    return {
        "id": preset_id,
        "name": name,
        "group": group_clean,
        "description": desc_text,
        "isCustom": False,
        "recipe": {
            "version": 1,
            "assetId": "preset_asset",
            "updatedAt": 0,
            "isHdrEnabled": False,
            "basic": {
                "exposure": exposure, "contrast": contrast, "highlights": highlights,
                "shadows": shadows, "whites": whites, "blacks": blacks,
                "temperature": temperature, "tint": tint, "vibrance": vibrance,
                "saturation": saturation
            },
            "effects": {
                "texture": texture, "clarity": clarity, "dehaze": dehaze,
                "vignetteAmount": vignette, "vignetteMidpoint": 50.0, "vignetteFeather": 50.0,
                "vignetteRoundness": 0.0, "vignetteHighlights": 0.0,
                "grainAmount": grain, "grainSize": grain_size, "grainRoughness": grain_rough
            },
            "detail": {
                "sharpening": sharpening, "sharpenRadius": sharpen_radius,
                "sharpenDetail": sharpen_detail, "sharpenMasking": sharpen_mask,
                "luminanceNoise": lum_noise, "luminanceDetail": 50.0, "luminanceContrast": 0.0,
                "colorNoise": col_noise, "colorDetail": 50.0, "colorSmoothness": 50.0
            },
            "curves": {
                "composite": curve_comp,
                "red": curve_r,
                "green": curve_g,
                "blue": curve_b,
                "parametricHighlights": 0.0, "parametricLights": 0.0,
                "parametricDarks": 0.0, "parametricShadows": 0.0,
                "splitShadow": 64.0, "splitMid": 128.0, "splitHighlight": 192.0
            },
            "hsl": hsl_channels,
            "colorGrading": {
                "shadows": {"hue": sh_hue, "saturation": sh_sat, "luminance": sh_lum},
                "midtones": {"hue": mid_hue, "saturation": mid_sat, "luminance": mid_lum},
                "highlights": {"hue": hi_hue, "saturation": hi_sat, "luminance": hi_lum},
                "global": {"hue": gl_hue, "saturation": gl_sat, "luminance": gl_lum},
                "blending": blending,
                "balance": balance
            },
            "masks": [],
            "crop": {
                "left": 0.0, "top": 0.0, "right": 1.0, "bottom": 1.0,
                "rotationDegrees": 0.0, "straightenAngle": 0.0,
                "flipHorizontal": False, "flipVertical": False,
                "aspectRatio": "Free", "verticalPerspective": 0.0,
                "horizontalPerspective": 0.0, "isExpandEnabled": False,
                "constrainCrop": False, "uprightMode": "Auto",
                "distortion": 0.0, "geometryRotate": 0.0, "geometryAspect": 0.0,
                "geometryScale": 100.0, "geometryXOffset": 0.0, "geometryYOffset": 0.0
            },
            "blur": {
                "amount": 0.0, "bokehShape": "Circle", "boost": 0.0,
                "focalRangeStart": 0.0, "focalRangeEnd": 1.0
            },
            "optics": {
                "removeChromaticAberration": False, "enableLensCorrections": False,
                "distortion": 0.0, "vignetteAmount": 0.0
            },
            "profile": "Adobe Color"
        }
    }

def format_kotlin_float(val: float) -> str:
    s = f"{val:.2f}f".rstrip("0").rstrip(".")
    if not s.endswith("f"):
        s += "f"
    if s == "-0f" or s == "-0.0f":
        s = "0f"
    return s

def generate_curated_preset_code(p: Dict[str, Any]) -> str:
    b = p["recipe"]["basic"]
    e = p["recipe"]["effects"]
    d = p["recipe"]["detail"]
    cg = p["recipe"]["colorGrading"]

    basic_args = []
    if b["exposure"] != 0: basic_args.append(f"exposure = {format_kotlin_float(b['exposure'])}")
    if b["contrast"] != 0: basic_args.append(f"contrast = {format_kotlin_float(b['contrast'])}")
    if b["highlights"] != 0: basic_args.append(f"highlights = {format_kotlin_float(b['highlights'])}")
    if b["shadows"] != 0: basic_args.append(f"shadows = {format_kotlin_float(b['shadows'])}")
    if b["whites"] != 0: basic_args.append(f"whites = {format_kotlin_float(b['whites'])}")
    if b["blacks"] != 0: basic_args.append(f"blacks = {format_kotlin_float(b['blacks'])}")
    if b["vibrance"] != 0: basic_args.append(f"vibrance = {format_kotlin_float(b['vibrance'])}")
    if b["saturation"] != 0: basic_args.append(f"saturation = {format_kotlin_float(b['saturation'])}")
    basic_code = f"BasicAdjustments({', '.join(basic_args)})" if basic_args else "BasicAdjustments()"

    eff_args = []
    if e["texture"] != 0: eff_args.append(f"texture = {format_kotlin_float(e['texture'])}")
    if e["clarity"] != 0: eff_args.append(f"clarity = {format_kotlin_float(e['clarity'])}")
    if e["dehaze"] != 0: eff_args.append(f"dehaze = {format_kotlin_float(e['dehaze'])}")
    if e["grainAmount"] != 0: eff_args.append(f"grainAmount = {format_kotlin_float(e['grainAmount'])}")
    eff_code = f"EffectsAdjustments({', '.join(eff_args)})" if eff_args else "EffectsAdjustments()"

    has_cg = any(cg[k]["saturation"] > 0 for k in ["shadows", "highlights"])
    if has_cg:
        cg_code = f"ColorGrading(shadows = ColorGradingWheel(hue = {format_kotlin_float(cg['shadows']['hue'])}, saturation = {format_kotlin_float(cg['shadows']['saturation'])}), highlights = ColorGradingWheel(hue = {format_kotlin_float(cg['highlights']['hue'])}, saturation = {format_kotlin_float(cg['highlights']['saturation'])}))"
    else:
        cg_code = "ColorGrading.default()"

    return f"""        Preset(
            id = "{p['id']}",
            name = "{p['name']}",
            group = "{p['group']}",
            description = "{p['description']}",
            recipe = EditRecipe(
                basic = {basic_code},
                effects = {eff_code},
                colorGrading = {cg_code}
            )
        )"""

def main():
    print(f"Mining presets from {APK_PATH}...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    mined_presets: List[Dict[str, Any]] = []
    seen_ids = set()

    with zipfile.ZipFile(APK_PATH, "r") as z:
        for name in sorted(z.namelist()):
            if name.lower().endswith(".xmp"):
                raw = z.read(name)
                parsed = parse_xmp(raw, name)
                if parsed:
                    pid = parsed["id"]
                    if pid in seen_ids:
                        idx = 2
                        while f"{pid}_{idx}" in seen_ids:
                            idx += 1
                        pid = f"{pid}_{idx}"
                        parsed["id"] = pid
                    seen_ids.add(pid)
                    mined_presets.append(parsed)

    print(f"Mined {len(mined_presets)} presets from APK.")

    # Save complete JSON
    json_path = os.path.join(OUTPUT_DIR, "mined_presets.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(mined_presets, f, indent=2)

    # Copy to assets and resources
    for dest in [ASSETS_OUT, RESOURCES_OUT]:
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "w", encoding="utf-8") as f:
            json.dump(mined_presets, f, indent=2)
        print(f"Exported JSON to {dest}")

    # Select 1 representative preset from each unique group for in-code curated set
    curated: List[Dict[str, Any]] = []
    seen_groups = set()
    for p in mined_presets:
        g = p["group"]
        if g not in seen_groups:
            seen_groups.add(g)
            curated.append(p)
    # Also add top Cinematic, Vintage, and Adaptive presets
    for p in mined_presets:
        if p not in curated and any(k in p["group"] for k in ["Cinematic", "Vintage", "Adaptive", "Color", "B&W"]):
            if len(curated) < 60:
                curated.append(p)

    curated_code = ",\n".join(generate_curated_preset_code(p) for p in curated)

    # Generate Kotlin library
    print(f"Generating Kotlin library at {KOTLIN_OUT}...")
    os.makedirs(os.path.dirname(KOTLIN_OUT), exist_ok=True)

    kotlin_code = f"""package com.cleanlr.preset

import android.content.Context
import com.cleanlr.model.*
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * CleanLightroom Mined Preset Library
 * Extracted directly from authentic Adobe Lightroom Mobile APK ({len(mined_presets)} presets).
 * Deserializes complete authentic preset catalog from assets / classpath resources,
 * with resilient in-code curated fallback covering every authentic category.
 */
object MinedPresetLibrary {{

    private val json = Json {{
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }}

    /**
     * Curated in-code presets ({len(curated)} presets) covering all authentic groups.
     */
    val CURATED_MINED_PRESETS: List<Preset> = listOf(
{curated_code}
    )

    /**
     * Complete mined preset collection ({len(mined_presets)} presets).
     */
    val PRESETS: List<Preset> by lazy {{
        loadAll()
    }}

    /**
     * Loads all 500+ presets from bundled JSON asset or classpath resource stream.
     */
    fun loadAll(context: Context? = null): List<Preset> {{
        return try {{
            val stream: InputStream? = context?.assets?.open("mined_presets.json")
                ?: MinedPresetLibrary::class.java.classLoader?.getResourceAsStream("mined_presets.json")
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("mined_presets.json")

            if (stream != null) {{
                val content = stream.bufferedReader(Charsets.UTF_8).use {{ it.readText() }}
                val loaded = json.decodeFromString<List<Preset>>(content)
                if (loaded.isNotEmpty()) loaded else CURATED_MINED_PRESETS
            }} else {{
                CURATED_MINED_PRESETS
            }}
        }} catch (e: Exception) {{
            CURATED_MINED_PRESETS
        }}
    }}

    fun getGroups(): List<String> = PRESETS.map {{ it.group }}.distinct()

    fun getPresetsByGroup(group: String): List<Preset> =
        PRESETS.filter {{ it.group.equals(group, ignoreCase = true) }}

    fun findPresetById(id: String): Preset? =
        PRESETS.find {{ it.id == id }}
}}
"""

    with open(KOTLIN_OUT, "w", encoding="utf-8") as f:
        f.write(kotlin_code)

    print(f"Kotlin code written successfully ({len(curated)} curated in-code, {len(mined_presets)} total via JSON).")

if __name__ == "__main__":
    main()
