#!/usr/bin/env python3
"""Enforce checked-in Macrobenchmark budgets from benchmarkData JSON output."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any


METRICS = {
    "frame_duration_cpu_ms_p95": {
        "aliases": ("frameDurationCpuMs",),
        "budget": "frame_duration_cpu_ms_p95_max",
        "aggregate": "p95",
    },
    "frame_overrun_ms_p95": {
        "aliases": ("frameOverrunMs",),
        "budget": "frame_overrun_ms_p95_max",
        "aggregate": "p95",
    },
    "memory_heap_size_kb_max": {
        "aliases": ("memoryHeapSizeMaxKb", "memoryHeapSizeKb", "memoryHeapSize"),
        "budget": "memory_heap_size_kb_max",
        "aggregate": "max",
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--budget", type=Path, required=True)
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    return parser.parse_args()


def numeric_values(value: Any) -> list[float]:
    if isinstance(value, bool):
        return []
    if isinstance(value, (int, float)) and math.isfinite(float(value)):
        return [float(value)]
    if isinstance(value, list):
        return [number for item in value for number in numeric_values(item)]
    return []


def metric_values(benchmark: dict[str, Any], aliases: tuple[str, ...]) -> list[float]:
    for section_name in ("metrics", "sampledMetrics"):
        section = benchmark.get(section_name)
        if not isinstance(section, dict):
            continue
        for alias in aliases:
            metric = section.get(alias)
            if not isinstance(metric, dict):
                continue
            runs = numeric_values(metric.get("runs"))
            if runs:
                return runs
            maximum = numeric_values(metric.get("maximum"))
            if maximum:
                return maximum
    return []


def percentile_nearest_rank(values: list[float], percentile: float) -> float:
    ordered = sorted(values)
    index = max(0, math.ceil(percentile * len(ordered)) - 1)
    return ordered[index]


def benchmark_label(benchmark: dict[str, Any]) -> str:
    class_name = str(benchmark.get("className", "")).strip()
    name = str(benchmark.get("name", "")).strip()
    return ".".join(part for part in (class_name, name) if part) or "<unnamed>"


def benchmark_speed(label: str) -> int | None:
    normalized = "".join(character for character in label.lower() if character.isalnum())
    speed_tokens = {
        1: ("onetimesspeed", "speed1", "1xspeed"),
        2: ("twotimesspeed", "speed2", "2xspeed"),
    }
    for speed, tokens in speed_tokens.items():
        if any(token in normalized for token in tokens):
            return speed
    return None


def main() -> int:
    args = parse_args()
    try:
        budget = json.loads(args.budget.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        print(f"ERROR: Could not read benchmark budget: {error}", file=sys.stderr)
        return 1
    result_files = sorted(args.results_dir.rglob("*-benchmarkData.json"))

    parse_errors: list[str] = []
    benchmarks: list[dict[str, Any]] = []
    for result_file in result_files:
        try:
            report = json.loads(result_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as error:
            parse_errors.append(f"{result_file}: {error}")
            continue
        report_benchmarks = report.get("benchmarks")
        if not isinstance(report_benchmarks, list):
            parse_errors.append(f"{result_file}: missing benchmarks array")
            continue
        benchmarks.extend(
            benchmark for benchmark in report_benchmarks if isinstance(benchmark, dict)
        )

    results: list[dict[str, Any]] = []
    problems: list[str] = []
    expected_speeds = budget.get("speeds")
    if not isinstance(expected_speeds, list) or not expected_speeds:
        problems.append("Budget must declare at least one expected speed.")
        expected_speeds = []
    if not result_files:
        problems.append("No *-benchmarkData.json files were produced.")
    if parse_errors:
        problems.extend(parse_errors)
    if len(benchmarks) < len(expected_speeds):
        problems.append(
            f"Expected at least {len(expected_speeds)} benchmark result(s), "
            f"found {len(benchmarks)}."
        )
    observed_speeds = {
        speed
        for benchmark in benchmarks
        if (speed := benchmark_speed(benchmark_label(benchmark))) is not None
    }
    missing_speeds = sorted(set(expected_speeds) - observed_speeds)
    if missing_speeds:
        problems.append(
            "Benchmark output is missing declared simulation speed(s): "
            + ", ".join(str(speed) for speed in missing_speeds)
            + "."
        )

    for benchmark in benchmarks:
        label = benchmark_label(benchmark)
        benchmark_result: dict[str, Any] = {"benchmark": label, "metrics": {}}
        for output_name, definition in METRICS.items():
            values = metric_values(benchmark, definition["aliases"])
            budget_key = str(definition["budget"])
            limit = budget.get(budget_key)
            if not isinstance(limit, (int, float)):
                problems.append(f"Budget is missing numeric value {budget_key}.")
                continue
            if not values:
                problems.append(
                    f"{label} did not report {definition['aliases'][0]} runs."
                )
                continue
            if definition["aggregate"] == "p95":
                measured = percentile_nearest_rank(values, 0.95)
            else:
                measured = max(values)
            passed = measured <= float(limit)
            benchmark_result["metrics"][output_name] = {
                "measured": measured,
                "limit": float(limit),
                "samples": len(values),
                "passed": passed,
            }
            if not passed:
                problems.append(
                    f"{label} {output_name}={measured:.3f} exceeds "
                    f"{float(limit):.3f}."
                )
        results.append(benchmark_result)

    summary = {
        "scenario": budget.get("scenario"),
        "expected_speeds": expected_speeds,
        "observed_speeds": sorted(observed_speeds),
        "result_files": [str(path) for path in result_files],
        "benchmarks": results,
        "passed": not problems,
        "problems": problems,
    }
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))

    if problems:
        for problem in problems:
            print(f"ERROR: {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
