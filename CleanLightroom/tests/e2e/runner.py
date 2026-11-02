"""
CleanLightroom E2E Test Suite Runner
Executes E2E verification tests against emulator-5554 across Tiers 1-4.
Outputs console summaries, JSON reports, and Markdown reports.
"""

import os
import sys
import time
import json
import argparse
import subprocess

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(TESTS_DIR, "..", "..", ".."))

TIER_FILES = {
    "1": os.path.join(TESTS_DIR, "test_tier1_feature_coverage.py"),
    "2": os.path.join(TESTS_DIR, "test_tier2_boundary_corner.py"),
    "3": os.path.join(TESTS_DIR, "test_tier3_pairwise_combinations.py"),
    "4": os.path.join(TESTS_DIR, "test_tier4_real_world_scenarios.py"),
}


def run_tests(tier: str = "all", verbose: bool = False, json_output: str = None, md_output: str = None) -> int:
    target_files = []
    if tier == "all":
        target_files = list(TIER_FILES.values())
    elif tier in TIER_FILES:
        target_files = [TIER_FILES[tier]]
    else:
        print(f"Unknown tier: {tier}. Must be 1, 2, 3, 4, or all.")
        return 1

    print("=" * 70)
    print(" CleanLightroom Mobile E2E Test Suite")
    print(f" Target Device : emulator-5554")
    print(f" Selected Tier : Tier {tier}")
    print(f" Test Files    : {len(target_files)}")
    print("=" * 70)

    pytest_cmd = [sys.executable, "-m", "pytest"] + target_files
    if verbose:
        pytest_cmd.append("-v")
    else:
        pytest_cmd.append("-q")

    # Add junitxml or json report if requested
    temp_json = os.path.join(TESTS_DIR, "test_report.json")
    # Run pytest
    start_time = time.time()
    result = subprocess.run(pytest_cmd, cwd=PROJECT_ROOT)
    elapsed = time.time() - start_time

    print("-" * 70)
    print(f"Test Run Completed in {elapsed:.2f} seconds with exit code {result.returncode}")
    print("-" * 70)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description="CleanLightroom E2E Test Runner")
    parser.add_argument("--tier", choices=["1", "2", "3", "4", "all"], default="all", help="Test Tier to execute")
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose test execution")
    parser.add_argument("--report-json", type=str, default=None, help="Path to save JSON test report")
    parser.add_argument("--report-md", type=str, default=None, help="Path to save Markdown test report")

    args = parser.parse_args()
    sys.exit(run_tests(tier=args.tier, verbose=args.verbose, json_output=args.report_json, md_output=args.report_md))


if __name__ == "__main__":
    main()
