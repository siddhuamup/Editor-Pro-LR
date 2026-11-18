# Project: Lightroom Mobile Design Reference Screenshot Analysis & Sorting

## Architecture
The system organizes 102 authentic Adobe Lightroom Mobile reference screenshots (36 vertical captures in `design-reference` root and 66 horizontal captures in `horizontal screen UI(same as verticle)`) into 20 canonical panel folders according to Lightroom's navigation, editing, and tool hierarchy. An automated, cryptographically grounded inventory audit verifies 100% file retention, zero data loss, zero duplication, zero image corruption, and zero unclassified omissions.

### Canonical Directory Taxonomy (20 Panels)
```
CleanLightroom/design-reference/
├── Actions/                  (4 screenshots: Quick Actions bar, AI Auto, Enhance, Subject)
├── Camera/                   (5 screenshots: Pro camera viewfinder, manual ISO/Shutter/WB, settings)
├── Color/                    (4 screenshots: White Balance, Temp, Tint, Vibrance, Saturation, B&W)
├── Color_Grading/            (5 screenshots: 3-way color wheels Shadows, Midtones, Highlights, Global)
├── Color_Mix/                (3 screenshots: 8-color HSL mixer chips, sliders, Targeted Adjustment Tool)
├── Community/                (1 screenshot: Community Discover feed, remixes, tutorials)
├── Crop/                     (13 screenshots: Aspect ratios, Free/Lock, Straighten dial, Generative Expand, Guided Upright)
├── Detail/                   (5 screenshots: Sharpening, Radius, Detail, Masking, Noise, Color Noise)
├── Editor_and_Export/        (9 screenshots: Loupe resting states, 3-dots overflow, Share & Export modals)
├── Effects/                  (7 screenshots: Texture, Clarity, Dehaze, Vignette, Grain)
├── Gallery/                  (3 screenshots: Device photos grid, Cloud imports, View options sheet)
├── Lens_Blur/                (3 screenshots: AI depth map detection, focus histogram, bokeh shapes)
├── Light/                    (4 screenshots: Exposure, Contrast, Highlights, Shadows, Whites, Blacks, HDR mode)
├── Masking/                  (11 screenshots: AI Subject/Sky/Bg, Brush, Gradients, Layer dock, local adjustments)
├── Optics/                   (1 screenshot: Chromatic Aberration, Lens Profile Corrections)
├── Presets/                  (7 screenshots: Recommended, Premium adaptive packs, User presets)
├── Profiles/                 (3 screenshots: Camera and creative look profiles browser)
├── Remove/                   (3 screenshots: Generative AI Remove, Heal, Clone Stamp, object detection)
├── Settings_and_Account/     (6 screenshots: Navigation drawer, Preferences, Account, Storage, Cloud sync)
├── Tone_Curve/               (5 screenshots: Point Curves White/Red/Green/Blue, Parametric 3-split curve)
├── Svg Icons/                (Excluded: Vector asset repository)
└── PANEL_MANIFEST.json       (Cryptographic audit index mapping all 102 files)
```

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Baseline File Inventory & Hashes | Enumerate all 102 screenshot files (36 vertical, 66 horizontal) with file sizes, dimensions, and SHA-256 digests | M1 | explorer_survey_1 |
| 2 | Canonical Panel Taxonomy | Define 20 standardized panel folders matching Lightroom Mobile UI navigation and editing tools | M1 | spec_miner_survey_3 |
| 3 | Per-File Semantic Mapping | Map each screenshot (vertical and horizontal) to its exact canonical panel folder and sub-tool state | M1 | explorer_survey_2 |
| 4 | Edge-Case Resolution | Map the 11 portrait-oriented screenshots residing in horizontal folder to functional panels (Camera, Settings, Community) | M1 | spec_miner_survey_3 |
| 5 | Collision-Free Migration Engine | Move (not copy/duplicate) screenshots into their target panel folders without losing original filenames or data | M1 | ORIGINAL_REQUEST R2 |
| 6 | Subfolder Cleanup | Safely retire `horizontal screen UI(same as verticle)` once all 66 horizontal files are moved to their target panel folders | M1 | ORIGINAL_REQUEST R2 |
| 7 | Machine-Readable Audit Manifest | Maintain `PANEL_MANIFEST.json` detailing original path, new path, dimensions, orientation, and SHA-256 digest | M1 | explorer_survey_2 |
| 8 | Automated Retention & Integrity Audit | Implement `verify_inventory.py` executing 7 invariant checks: count=102, bijective hash set equality, byte size equality, zero corruption | M1 | ORIGINAL_REQUEST R3, AC |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Screenshot Panel Sorting & 100% Retention Audit | Create 20 panel folders; move all 36 vertical and 66 horizontal screenshots into their respective panel folders; execute migration; run automated audit script `verify_inventory.py` to confirm 100% file retention (102/102 files, 0 missing, 0 duplicates, 0 corrupted). | Survey | DONE |

---

## Interface Contracts
### Migration Script ↔ File System
- **Input**:
  - `CleanLightroom/design-reference/*.png` (36 files)
  - `CleanLightroom/design-reference/horizontal screen UI(same as verticle)/*.png` (66 files)
  - `CleanLightroom/design-reference/PANEL_MANIFEST.json`
- **Output**:
  - Exactly 20 panel folders under `CleanLightroom/design-reference/` containing all 102 files.
  - Zero files left in root (except `PANEL_MANIFEST.json` and directory `Svg Icons/`).
  - Empty source directory `horizontal screen UI(same as verticle)` removed after migration.

### Verification Script ↔ Acceptance Gate
- **Invocation**: `python CleanLightroom/design-reference/verify_inventory.py`
- **Output**:
  - Exit code `0` if all 7 invariants pass.
  - Exit code `1` if any file is missing, duplicate, misplaced, corrupt, or hash mismatched.

---

## Code Layout & Ownership Matrix

| Stream / Role | Exclusive Write Scope | Read-Only Access |
|:--------------|:----------------------|:-----------------|
| Worker (`teamwork_worker`) | `CleanLightroom/design-reference/**` (panel folders, migration script, `verify_inventory.py`) | All files, `.agents/teamwork/**` |
| Reviewers / Challengers / Auditor | Verification reports in their respective `.agents/teamwork/` directories | All files, `CleanLightroom/design-reference/**` |

**Protected Resources**:
- No screenshot file may be deleted or modified in place; all file movements must preserve exact byte size and SHA-256 digest.
