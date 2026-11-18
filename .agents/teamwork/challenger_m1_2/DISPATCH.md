## Task Assignment: Milestone 1 Empirical Challenger 2 (challenger_m1_2)

Read:
1. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md`
2. `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
3. `c:\Users\Siddhu\Desktop\apps testing\TEST_INFRA.md`
4. `c:\Users\Siddhu\Desktop\apps testing\TEST_READY.md`
5. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m1_1\handoff.md`

Working directory: `.agents/teamwork/challenger_m1_2/`

### Mission
Conduct independent empirical verification using an independent oracle:
- Write and execute an independent verification oracle from scratch in Python (do NOT import or reuse worker's code).
- Independently scan all subfolders in `CleanLightroom\design-reference\`.
- Compute SHA-256 digests and file sizes directly from disk bytes.
- Decode every PNG using PIL or struct header parsing, verifying dimensions, channels, and bit depth.
- Cross-check against `PANEL_MANIFEST.json` and `explorer_survey_1` baseline hashes.
- Verify 100% bijective equality: zero missing, zero extra, zero corrupted.
- Render explicit confirmation of correctness or defects.
- Write full report to `.agents/teamwork/challenger_m1_2/handoff.md`.
