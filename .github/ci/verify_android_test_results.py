#!/usr/bin/env python3
"""Fail CI when Android instrumentation discovery is missing or incomplete."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


TEST_ANNOTATION = re.compile(r"^\s*@Test\b", re.MULTILINE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    sources = sorted(args.source_dir.rglob("*.kt"))
    expected = sum(
        len(TEST_ANNOTATION.findall(source.read_text(encoding="utf-8")))
        for source in sources
    )

    result_files = sorted(args.results_dir.rglob("TEST-*.xml"))
    test_cases = []
    parse_errors: list[str] = []
    suite_errors: list[str] = []
    for result_file in result_files:
        try:
            root = ET.parse(result_file).getroot()
        except (ET.ParseError, OSError) as error:
            parse_errors.append(f"{result_file}: {error}")
            continue
        test_cases.extend(root.iter("testcase"))
        suite_errors.extend(
            element.text.strip()
            for element in root.iter("system-err")
            if element.text and element.text.strip()
        )

    failures = sum(case.find("failure") is not None for case in test_cases)
    errors = sum(case.find("error") is not None for case in test_cases)
    skipped = sum(case.find("skipped") is not None for case in test_cases)
    executed = len(test_cases)
    summary = {
        "source_test_annotations": expected,
        "result_files": [str(path) for path in result_files],
        "reported_test_cases": executed,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "parse_errors": parse_errors,
        "suite_errors": suite_errors,
    }
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))

    problems: list[str] = []
    if expected == 0:
        problems.append("No @Test annotations were found in instrumentation sources.")
    if not result_files:
        problems.append("No instrumentation XML result files were produced.")
    if parse_errors:
        problems.append("One or more instrumentation result files could not be parsed.")
    if executed == 0:
        problems.append("Instrumentation reported zero discovered tests.")
    if executed < expected:
        problems.append(
            f"Instrumentation reported {executed} test cases, fewer than the "
            f"{expected} source @Test annotations."
        )
    if failures or errors:
        problems.append(
            f"Instrumentation reported {failures} failures and {errors} errors."
        )
    if suite_errors and executed == 0:
        problems.append("The empty test suite also reported runner diagnostics.")

    if problems:
        for problem in problems:
            print(f"ERROR: {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
