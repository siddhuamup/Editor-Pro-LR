## 2026-11-08T08:41:44Z
You are worker_mining_1, a versatile worker agent (teamwork_worker).
Your working directory is: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_mining_1\
Your parent is: ee54e32c-82f7-47db-a4c1-749165b59911

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

MANDATORY FIRST STEP:
Read the authoritative user request and specifications at:
1. c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md
2. c:\Users\Siddhu\Desktop\apps testing\PROJECT.md
3. c:\Users\Siddhu\Desktop\apps testing\TEST_INFRA.md

Also read the survey reports with exact algorithms and findings:
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\explorer_survey_1\handoff.md
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\spec_miner_survey_2\handoff.md
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\spec_miner_survey_3\handoff.md

EXCLUSIVE WRITE OWNERSHIP:
You own and may write to:
- c:\Users\Siddhu\Desktop\apps testing\mine_apk_svgs.py
- c:\Users\Siddhu\Desktop\apps testing\verify_mined_svgs.py
- c:\Users\Siddhu\Desktop\apps testing\mined Svg\ (directory and all extracted .svg files)
- Your own directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_mining_1\

TASKS TO EXECUTE:
1. Implement the extraction script `mine_apk_svgs.py`:
   - Inspect and read `C:\Users\Siddhu\Downloads\Lightroom.apk`.
   - Parse `resources.arsc` to map all 1,267 obfuscated `res/*.xml` files to their canonical resource identifiers (e.g., `svg_camera`, `ic_overflow_copy_settings`, etc.). Use the tested mapping logic or `aapt2 dump resources` from `C:\Users\Siddhu\AppData\Local\Android\Sdk\build-tools\36.1.0\aapt2.exe`.
   - Decode the binary AXML for each of the 1,267 `<vector>` drawables in memory.
   - Resolve indirect `@0x...` string references in pathData using the ARSC string table if present.
   - Convert every `<vector>` drawable into standard, valid W3C `.svg` XML:
     * Root `<svg>` tag with `xmlns="http://www.w3.org/2000/svg"`, `viewBox="0 0 {viewportWidth} {viewportHeight}"`, `width="{width}"`, `height="{height}"`.
     * Clean dimensions (strip `dp`, `dip`, `px`).
     * Path data `d="{pathData}"`.
     * Handle colors: split 8-digit `#AARRGGBB` into `#RRGGBB` and `fill-opacity` / `stroke-opacity`.
     * Handle alpha=0 (`#00000000` -> `fill="none"` / `stroke="none"`).
     * If `android:fillColor` is missing, explicitly emit `fill="none"` to avoid default SVG black fill artifacts.
     * Handle strokes: `stroke`, `stroke-width`, `stroke-linecap`, `stroke-linejoin`.
     * Handle fill-rules: `android:fillType="1"` or `evenOdd` -> `fill-rule="evenodd"`.
     * Handle `<group>` transformations with pivot translation.
     * Handle `<clip-path>`.
   - Save every generated SVG into `mined Svg/<canonical_name>.svg`. Handle any naming duplicates cleanly without overwriting.

2. Implement the programmatic verification test script `verify_mined_svgs.py`:
   - Follow the 5-gate test harness designed in `TEST_INFRA.md` and `spec_miner_survey_3/handoff.md`.
   - Gate 1: Non-empty file (> 0 bytes).
   - Gate 2: Well-formed XML DOM parsing via `xml.etree.ElementTree`.
   - Gate 3: Root `<svg>` tag with valid `xmlns` and `viewBox` or dimensions.
   - Gate 4: Visual graphical elements (`<path>`, `<g>`, etc.) with non-empty path data.
   - Gate 5: Summary statistics (Total, Passed, Failed) and exit code 0 if all pass, 1 if any fail.

3. Execute `mine_apk_svgs.py` to extract all vector icons from `C:\Users\Siddhu\Downloads\Lightroom.apk` into `mined Svg/`.

4. Execute `python verify_mined_svgs.py --dir "mined Svg"` and ensure 100% of files pass all validation gates cleanly with exit code 0.
