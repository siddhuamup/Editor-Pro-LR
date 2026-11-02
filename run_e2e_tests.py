"""
CleanLightroom E2E Test Suite Master CLI Launcher
Usage:
    python run_e2e_tests.py
    python run_e2e_tests.py --tier 1
    python run_e2e_tests.py --tier 2 -v
"""

import os
import sys

TARGET_RUNNER = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "CleanLightroom", "tests", "e2e", "runner.py")
)

if __name__ == "__main__":
    if os.path.exists(TARGET_RUNNER):
        import importlib.util
        spec = importlib.util.spec_from_file_location("runner", TARGET_RUNNER)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        mod.main()
    else:
        print(f"Error: Target runner not found at {TARGET_RUNNER}")
        sys.exit(1)
