import os
import re
import json
import zipfile
import xml.etree.ElementTree as ET
from typing import Dict, Any, List

APK_PATH = r"C:\Users\Siddhu\Downloads\Lightroom.apk"
OUTPUT_DIR = r"mined_profiles"
ASSETS_OUT = r"CleanLightroom\app\src\main\assets\mined_profiles.json"
RESOURCES_OUT = r"CleanLightroom\app\src\main\resources\mined_profiles.json"
KOTLIN_OUT = r"CleanLightroom\app\src\main\kotlin\com\cleanlr\profile\ProfileManager.kt"

CRS_NS = "http://ns.adobe.com/camera-raw-settings/1.0/"
RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

def main():
    print(f"Mining profiles from {APK_PATH}...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    mined_profiles: List[Dict[str, Any]] = []

    with zipfile.ZipFile(APK_PATH, "r") as z:
        for name in sorted(z.namelist()):
            if ('Settings/Adobe/Profiles/' in name or 'Style - Film/Profiles/' in name) and name.endswith('.xmp'):
                raw = z.read(name)
                # Save raw asset to mined_profiles/
                rel_path = name.replace("assets/stagingcontent/Settings/", "").replace("assets/", "")
                out_file_path = os.path.join(OUTPUT_DIR, rel_path)
                os.makedirs(os.path.dirname(out_file_path), exist_ok=True)
                with open(out_file_path, "wb") as f_out:
                    f_out.write(raw)

                try:
                    root = ET.fromstring(raw)
                except Exception:
                    continue

                desc = root.find(f".//{{{RDF_NS}}}Description")
                name_el = root.find(f".//{{{CRS_NS}}}Name//{{{RDF_NS}}}li")
                prof_name = name_el.text.strip() if (name_el is not None and name_el.text) else name.split('/')[-1].replace('.xmp', '')

                # Determine group
                parts = name.split('/')
                group = parts[-2]
                if group == 'Adobe Raw':
                    group = 'Basic'
                elif group == 'Profiles':
                    group = 'Film'

                is_monochrome = False
                if desc is not None:
                    # Check ConvertToGrayscale attribute
                    is_gray_attr = desc.attrib.get(f"{{{CRS_NS}}}ConvertToGrayscale") or desc.attrib.get("ConvertToGrayscale", "False")
                    is_monochrome = str(is_gray_attr).lower() in ("true", "1")
                if "monochrome" in prof_name.lower() or group == "B&W":
                    is_monochrome = True

                safe_id = re.sub(r'[^a-zA-Z0-9]+', '_', f"profile_{group}_{prof_name}").strip('_').lower()

                mined_profiles.append({
                    "id": safe_id,
                    "name": prof_name,
                    "group": group,
                    "isMonochrome": is_monochrome,
                    "path": name
                })

    print(f"Mined {len(mined_profiles)} profiles.")

    # Save JSON to output dir and project assets/resources
    for dest in [os.path.join(OUTPUT_DIR, "mined_profiles.json"), ASSETS_OUT, RESOURCES_OUT]:
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "w", encoding="utf-8") as f:
            json.dump(mined_profiles, f, indent=2)
        print(f"Exported JSON to {dest}")

    # Generate Kotlin ProfileManager
    groups_ordered = ["Basic", "Artistic", "B&W", "Modern", "Vintage", "Film", "Adaptive"]
    
    kotlin_items = []
    for p in mined_profiles:
        mono_str = "true" if p["isMonochrome"] else "false"
        kotlin_items.append(f"""        ProfileItem(
            id = "{p['id']}",
            name = "{p['name']}",
            group = "{p['group']}",
            isMonochrome = {mono_str}
        )""")

    kotlin_code = f"""package com.cleanlr.profile

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
 * Manages all {len(mined_profiles)} authentic Lightroom profiles mined from APK.
 */
object ProfileManager {{

    val GROUPS: List<String> = listOf({', '.join(f'"{g}"' for g in groups_ordered)})

    val ALL_PROFILES: List<ProfileItem> = listOf(
{",\n".join(kotlin_items)}
    )

    fun getProfilesByGroup(group: String): List<ProfileItem> {{
        val target = if (group.equals("Adobe Raw", ignoreCase = true)) "Basic" else group
        return ALL_PROFILES.filter {{ it.group.equals(target, ignoreCase = true) }}
    }}

    fun findProfileByName(name: String): ProfileItem? =
        ALL_PROFILES.find {{ it.name.equals(name, ignoreCase = true) }}

    fun isProfileMonochrome(name: String): Boolean =
        findProfileByName(name)?.isMonochrome ?: name.contains("Monochrome", ignoreCase = true) || name.startsWith("B&W", ignoreCase = true)
}}
"""

    os.makedirs(os.path.dirname(KOTLIN_OUT), exist_ok=True)
    with open(KOTLIN_OUT, "w", encoding="utf-8") as f:
        f.write(kotlin_code)

    print(f"Generated {KOTLIN_OUT} with {len(mined_profiles)} profiles.")

if __name__ == "__main__":
    main()
