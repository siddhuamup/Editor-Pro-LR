# E2E Test Infra: Lightroom Mobile Screenshot Panel Sorting

## Test Philosophy
- Opaque-box, requirement-driven verification of file retention, classification accuracy, and data integrity.
- Methodology: Category-Partition + Boundary Value Analysis + Invariant Checking (7 Cryptographic Invariants).
- Zero tolerance for file loss, corruption, duplicates, or omitted files.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Baseline Count & Enumeration | ORIGINAL_REQUEST R1, AC | 5 | 5 | ✓ |
| 2 | Canonical Panel Folder Creation (20 Folders) | ORIGINAL_REQUEST R2 | 5 | 5 | ✓ |
| 3 | Vertical & Horizontal Screenshot Movement | ORIGINAL_REQUEST R2 | 5 | 5 | ✓ |
| 4 | Collision-Free Preservation | ORIGINAL_REQUEST R2, AC | 5 | 5 | ✓ |
| 5 | Clean Workspace (No Stray Images in Root) | ORIGINAL_REQUEST R2, AC | 5 | 5 | ✓ |
| 6 | Automated Audit Harness (`verify_inventory.py`) | ORIGINAL_REQUEST R3, AC | 5 | 5 | ✓ |
| 7 | Cryptographic SHA-256 Digest Match (102/102) | ORIGINAL_REQUEST R3, AC | 5 | 5 | ✓ |

## Test Architecture
- **Test Runner**: Python test script `CleanLightroom/design-reference/verify_inventory.py`
- **Invocation**: `python CleanLightroom/design-reference/verify_inventory.py`
- **Pass/Fail Semantics**:
  - Exit code `0` on 100% pass of all 7 Invariants.
  - Exit code `1` on any failure with specific file and error breakdown.

### The 7 Retention & Integrity Invariants
1. **Baseline Count Invariant**: Exactly 102 screenshot files in baseline catalog ($N = 102$).
2. **Current Disk Count Invariant**: Exactly 102 screenshot files exist across the 20 panel folders.
3. **Bijective Hash Invariant**: $\text{Set}(\text{Hashes}_{baseline}) \equiv \text{Set}(\text{Hashes}_{disk})$ (0 missing, 0 duplicates).
4. **Byte-Level Integrity Invariant**: Every file size and SHA-256 matches baseline bit-for-bit (0 corruption).
5. **Decodable Image Structure Invariant**: PIL / libpng decodes every `.png` without errors (`Image.verify()`).
6. **Valid Dimension Invariant**: Every file is either `1220x2712` (portrait) or `2712x1220` (landscape).
7. **Canonical Taxonomy Invariant**: All files reside inside one of the 20 canonical panel folders, with no unclassified screenshot files in root.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Global File Invariant Audit | F1, F2, F3, F4, F6, F7 | High |
| 2 | Cross-Orientation Coexistence Verification | F2, F3, F4, F6 | Medium |
| 3 | Image Header & Bitstream Corruption Verification | F6, F7 | Medium |
| 4 | Sub-panel Classification Verification (Tone Curve, Color Mix, Color Grading) | F2, F3 | Medium |
| 5 | Cleanup & Stray File Detection | F5, F6 | Low |

## Coverage Thresholds
- Tier 1 (Feature Coverage): All 20 panels populated with expected counts.
- Tier 2 (Boundary & Corner): Verification of 11 portrait-in-horizontal files, 1-file panels (`Community`, `Optics`), and 13-file panels (`Crop`).
- Tier 3 (Cross-Feature): Simultaneous verification of both vertical and horizontal files inside shared panel directories.
- Tier 4 (Real-World Application): Complete end-to-end execution of `verify_inventory.py` yielding 100% pass rate.
