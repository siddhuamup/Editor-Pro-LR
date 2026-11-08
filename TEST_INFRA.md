# E2E Test Infra: Lightroom APK SVG Icon Mining & Verification

## Test Philosophy
- Requirement-driven, opaque-box and structural verification.
- Validates that every extracted vector asset in `mined Svg/` meets W3C SVG standards, has non-zero size, is well-formed XML, contains visual elements, and accurately represents the original vector drawables from `Lightroom.apk`.

## Feature Inventory & Test Coverage
| # | Feature | Requirement | Tier 1 | Tier 2 | Tier 3 | Tier 4 |
|---|---------|-------------|:------:|:------:|:------:|:------:|
| 1 | APK Ingestion & Extraction | R1: Extract all icon assets | ✓ | ✓ | ✓ | ✓ |
| 2 | Name Deobfuscation | Canonical naming from ARSC | ✓ | ✓ | ✓ | ✓ |
| 3 | Well-formed XML/SVG | AC: Clean XML parsing | ✓ | ✓ | ✓ | ✓ |
| 4 | File Size > 0 Bytes | AC: Non-empty files | ✓ | ✓ | ✓ | ✓ |
| 5 | Visual Content Presence | Path data and elements | ✓ | ✓ | ✓ | ✓ |
| 6 | Sizing & ViewBox | Valid viewBox and dimensions | ✓ | ✓ | ✓ | ✓ |
| 7 | Transparency & Colors | Correct alpha/fill rendering | ✓ | ✓ | ✓ | ✓ |

## Test Architecture
- **Test Runner**: Python script `verify_mined_svgs.py --dir "mined Svg"`
- **Pass/Fail Semantics**:
  - Exit code `0`: All files meet all 5 validation gates (100% pass rate).
  - Exit code `1`: Any file is missing, empty, malformed, or missing visual elements.
- **Directory Layout**:
  - Input: `C:\Users\Siddhu\Downloads\Lightroom.apk`
  - Output: `c:\Users\Siddhu\Desktop\apps testing\mined Svg\*.svg`
  - Test Script: `c:\Users\Siddhu\Desktop\apps testing\verify_mined_svgs.py`

## Verification Gates
- **Gate 1**: File Existence & Size (`os.path.getsize(f) > 0`).
- **Gate 2**: XML Well-Formedness (`xml.etree.ElementTree.parse(f)` succeeds with 0 syntax errors).
- **Gate 3**: Root Tag & Coordinates (`root.tag == "svg"`, valid `viewBox` and/or `width`/`height`).
- **Gate 4**: Graphical Elements (`<path>`, `<g>`, etc. present with non-empty `d` path data).
- **Gate 5**: Global Batch Assertion (`total_count > 1000`, `failed_count == 0`).
