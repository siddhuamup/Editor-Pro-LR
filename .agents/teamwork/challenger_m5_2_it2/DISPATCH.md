# Dispatch Instructions — challenger_m5_2_it2

## 2026-11-08T18:58:10Z
[Message] timestamp=2026-11-08T18:58:10Z sender=940af036-e815-4281-aa60-73e24df6e7c3 priority=MESSAGE_PRIORITY_HIGH
You are challenger_m5_2_it2 (teamwork_challenger).
Your working directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_2_it2\

Read your dispatch instructions in:
c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_2_it2\DISPATCH.md

MANDATORY INPUTS (read before starting):
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md
- c:\Users\Siddhu\Desktop\apps testing\PROJECT.md
- c:\Users\Siddhu\Desktop\apps testing\TEST_READY.md
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_replacement\handoff.md

TASK:
1. Adversarially verify application resilience and state continuity:
   - Verify that rotating between portrait and landscape preserves editing state, slider parameters, and mask layers without crash or visual reset.
   - Verify mask creation, inversion, opacity boundary extremes (0% and 100%), and combine modes (Union, Intersect, Subtract).
   - Verify that memory pressure trims (am send-trim-memory) and process background/foreground cycles (test_t2_02, test_t2_03) execute cleanly without memory leaks or ANRs.
2. Verify hardware state on emulator-5554:
   - Inspect logcat crash buffer (logcat -d -b crash) for zero crashes.
   - Run Tier 2 and Tier 3 boundary/pairwise tests (python run_e2e_tests.py --tier 2 -v, python run_e2e_tests.py --tier 3 -v) or verify their passing execution.
3. Check for any edge-case visual overlap, touch target clipping, or unhandled exceptions.
4. Document your findings and deliver your gate verdict (APPROVE or REQUEST_CHANGES) to:
   c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_2_it2\handoff.md
   and notify parent via send_message(Recipient="940af036-e815-4281-aa60-73e24df6e7c3", Message=...).

## 2026-11-09T16:48:58Z
[Message] timestamp=2026-11-09T16:48:58Z sender=940af036-e815-4281-aa60-73e24df6e7c3 priority=MESSAGE_PRIORITY_HIGH
**Context**: Milestone 5 Challenger 2 verification following server restart.
**Content**: You previously noted uiautomator daemon contention during concurrent runs. The system is now stabilized, and updated APK is deployed.
**Action**: Please complete your verification, write your findings and verdict to `handoff.md`, and report your verdict.
