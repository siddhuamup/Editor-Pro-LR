# E2E Test Infra: CleanLightroom Mobile

## Test Philosophy
- Opaque-box, requirement-driven. Direct verification of user-facing UI, layout, and rendering on `emulator-5554`.
- Methodology: Category-Partition + Boundary Value Analysis + Pairwise Feature Combinations + Real-World Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Build & Launch | ORIGINAL_REQUEST §1 | 5 | 5 | ✓ |
| 2 | Design Tokens & Surfaces | ORIGINAL_REQUEST §1 | 5 | 5 | ✓ |
| 3 | SVG Icon Parity | ORIGINAL_REQUEST §1 | 5 | 5 | ✓ |
| 4 | Masking Panel Sub-modes | ORIGINAL_REQUEST §2 | 5 | 5 | ✓ |
| 5 | Screen Orientation Adaptation | ORIGINAL_REQUEST §3 | 5 | 5 | ✓ |

## Test Architecture
- Test Runner: Python test runner invoking ADB commands against `emulator-5554`.
- Test Harness:
  - Orientation controller: `settings put system user_rotation [0|1]`.
  - Screenshot capture: `screencap -p /sdcard/capture.png` pulled to local inspection directory.
  - UI Dumper: `uiautomator dump` to verify presence of specific component text, bounds, and resource IDs.
- Pass/Fail Semantics:
  - Zero crashes/ANRs during launch and mode transitions.
  - Pixel color sampling matches authentic spectrum tokens (`#1D1D1D` dock surface, `#2680EB` active tint).
  - Exact orientation dimensions: Portrait canvas centered above dock, Landscape canvas on left (`X = 0..1780`) with adjustment rails on right (`X = 1780..2712`).

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Portrait Primary Workflow | Library navigation -> Editor open -> Adjust Light sliders -> Compare before/after | High |
| 2 | Masking Creation & Editing | Open Masking -> Tap (+) -> Select Subject -> Verify ruby red overlay -> Adjust mask exposure | High |
| 3 | Landscape Orientation Pivot | Rotate emulator to landscape -> Verify 3-rail right layout -> Select Edit sub-tools | High |
| 4 | Masking in Landscape | Open Masking in landscape -> Check 5 adjustment tabs in right column -> Invert mask | High |
| 5 | Crop & Aspect Ratio in Both Orientations | Open Crop in portrait -> Rotate to landscape -> Change aspect ratio to 1:1 -> Verify canvas letterbox | High |

## Coverage Thresholds
- Tier 1: ≥5 per feature (25 tests)
- Tier 2: ≥5 per feature (25 tests)
- Tier 3: pairwise interaction coverage across tools and orientations (10 tests)
- Tier 4: ≥5 realistic application scenarios (5 tests)
- **Total: 65 E2E Verification Tests**
