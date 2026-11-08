# Project: Lightroom APK SVG Icon Mining & Conversion

## Architecture
The system extracts all vector icon and graphic assets from the authentic Adobe Lightroom Mobile APK (`C:\Users\Siddhu\Downloads\Lightroom.apk`), decodes compiled Android Binary XML (AXML) into Vector Drawable structures, resolves obfuscated resource filenames to canonical semantic names using the APK's `resources.arsc` symbol table, and converts all vector drawables into standard W3C compliant `.svg` files stored in `mined Svg/`. A programmatic test suite verifies well-formedness, dimensions, and graphical element integrity.

### Data Flow & Conversion Pipeline
1. **APK Archive Ingestion**: Open `C:\Users\Siddhu\Downloads\Lightroom.apk` via Python `zipfile`.
2. **Resource Name Deobfuscation**: Parse `resources.arsc` table (package `0x7f` `com.adobe.lrmobile`) to map obfuscated `res/*.xml` paths (e.g. `res/-18.xml`, `res/RUT.xml`) to canonical human-readable identifiers (e.g. `svg_camera`, `ic_overflow_copy_settings`).
3. **In-Memory Streaming AXML Decoder**: Stream binary AXML chunks (`0x0001` string pool, `0x0102` start element, `0x0103` end element), resolving string pool tokens and attribute resource IDs.
4. **Vector-to-SVG Semantic Mapping**:
   - `<vector>` root: User coordinate system `viewBox="0 0 {viewportWidth} {viewportHeight}"`, intrinsic `width` and `height` (stripped of `dp`/`px` units), standard namespace `xmlns="http://www.w3.org/2000/svg"`.
   - `<path>` geometry: `android:pathData` mapped directly to `d="..."`. Indirect `@0x...` string references resolved via ARSC string pool.
   - Color & Opacity: `#AARRGGBB` hex split into `#RRGGBB` + `fill-opacity` / `stroke-opacity`. `#00000000` mapped to `fill="none"` / `stroke="none"`. Missing `android:fillColor` explicitly mapped to `fill="none"` to prevent SVG default black fill.
   - Stroke styling: `stroke`, `stroke-width`, `stroke-linecap`, `stroke-linejoin`, `stroke-miterlimit`.
   - Fill rule: `android:fillType="1"` / `evenOdd` mapped to `fill-rule="evenodd"`.
   - Groups & Transforms: Pivot-aware transformation matrix `translate(px + tx, py + ty) rotate(rot) scale(sx, sy) translate(-px, -py)`.
   - Clip paths: `<clip-path>` emitted as `<clipPath id="..."><path d="..."/></clipPath>` and attached via `clip-path="url(#...)"`.
5. **Output Organization**: Cleanly write all generated `.svg` files to `mined Svg/`.
6. **Programmatic Verification**: Execute `verify_mined_svgs.py` verifying file existence, non-zero size, XML well-formedness, root `<svg>` tag and attributes, and presence of valid graphical elements.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | APK Verification & Discovery | Ingest `C:\Users\Siddhu\Downloads\Lightroom.apk` (170 MB), scan all 3,552 AXML files, identify all 1,267 `<vector>` drawables | M1 | Survey 1, Survey 2 |
| 2 | ARSC Resource Deobfuscation | Parse `resources.arsc` to map all 1,267 obfuscated `res/*.xml` paths to canonical human-readable names (`svg_*`, `ic_*`, `spectrum_*`) | M1 | Survey 1, Survey 2 |
| 3 | Fast In-Memory AXML Parser | Decode binary AXML files directly in memory without external binary dependencies | M1 | Survey 1, Survey 2 |
| 4 | Indirect String Ref Resolution | Resolve `@0x...` string reference IDs in `pathData` to path strings from ARSC string pool | M1 | Survey 2 |
| 5 | Viewport & Sizing Normalization | Map `viewportWidth/Height` to `viewBox` and clean `width/height` attributes | M1 | Survey 2, Survey 3 |
| 6 | Color & Transparency Mapping | Split ARGB `#AARRGGBB`, map `#00000000` to `fill="none"`, avoid black fill on stroke paths | M1 | Survey 2, Survey 3 |
| 7 | Stroke & FillRule Translation | Map stroke caps/joins/width and `fillType="evenOdd"` to SVG `fill-rule="evenodd"` | M1 | Survey 2, Survey 3 |
| 8 | Group Pivot Transformation | Map `pivotX/Y`, `rotation`, `scaleX/Y`, `translateX/Y` to SVG group `transform` | M1 | Survey 2, Survey 3 |
| 9 | ClipPath Generation | Map `<clip-path>` to SVG `<clipPath>` definitions and references | M1 | Survey 2, Survey 3 |
| 10 | Output Organization to `mined Svg` | Write all extracted `.svg` files into `mined Svg/` directory | M1 | ORIGINAL_REQUEST R2 |
| 11 | Programmatic Test Harness | Implement 5-gate validator `verify_mined_svgs.py` verifying size > 0, XML validity, SVG schema | M1 | ORIGINAL_REQUEST AC |
| 12 | 100% Quality Assurance Gate | Execute test script and confirm 100% of mined SVGs pass all gates with 0 errors | M1 | ORIGINAL_REQUEST AC |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Complete APK SVG Mining & Verification | Implement `mine_apk_svgs.py` and `verify_mined_svgs.py`. Extract and convert all 1,267+ vector assets from `Lightroom.apk` to standard `.svg` in `mined Svg/`. Run programmatic test suite and confirm 100% pass rate. | none | IN_PROGRESS |

---

## Code Layout & File Ownership Matrix

| Stream / Role | Exclusive Write Scope | Read-Only Access |
|:--------------|:----------------------|:-----------------|
| Worker (`teamwork_worker`) | `mine_apk_svgs.py`, `verify_mined_svgs.py`, `mined Svg/**` | `C:\Users\Siddhu\Downloads\Lightroom.apk`, `.agents/teamwork/**` |
| Reviewers / Challengers / Auditor | Verification reports in their respective `.agents/teamwork/` directories | All files, `mined Svg/**` |

**Protected Resources**:
`C:\Users\Siddhu\Downloads\Lightroom.apk` must remain unmodified (read-only source).
