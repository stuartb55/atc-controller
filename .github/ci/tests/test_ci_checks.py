from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
ANDROID_RESULTS_CHECK = ROOT / ".github" / "ci" / "verify_android_test_results.py"
BENCHMARK_BUDGET_CHECK = ROOT / ".github" / "ci" / "enforce_benchmark_budget.py"


class AndroidResultsCheckTest(unittest.TestCase):
    def run_check(self, test_count: int) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_dir = root / "sources"
            results_dir = root / "results"
            source_dir.mkdir()
            results_dir.mkdir()
            (source_dir / "ExampleTest.kt").write_text(
                "class ExampleTest {\n    @Test\n    fun first() = Unit\n}\n",
                encoding="utf-8",
            )
            test_cases = "\n".join(
                f'<testcase name="test{index}" classname="ExampleTest" />'
                for index in range(test_count)
            )
            (results_dir / "TEST-device.xml").write_text(
                f'<testsuite tests="{test_count}">{test_cases}</testsuite>\n',
                encoding="utf-8",
            )
            return subprocess.run(
                [
                    sys.executable,
                    str(ANDROID_RESULTS_CHECK),
                    "--source-dir",
                    str(source_dir),
                    "--results-dir",
                    str(results_dir),
                    "--summary",
                    str(root / "summary.json"),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

    def test_accepts_complete_discovery(self) -> None:
        self.assertEqual(0, self.run_check(test_count=1).returncode)

    def test_rejects_zero_discovery(self) -> None:
        result = self.run_check(test_count=0)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("zero discovered tests", result.stderr)


class BenchmarkBudgetCheckTest(unittest.TestCase):
    def run_check(
        self,
        *,
        second_speed: bool = True,
        frame_p95: float = 12.0,
    ) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            results_dir = root / "outputs"
            results_dir.mkdir()
            budget = {
                "scenario": "test",
                "speeds": [1, 2],
                "frame_duration_cpu_ms_p95_max": 32.0,
                "frame_overrun_ms_p95_max": 16.0,
                "memory_heap_size_kb_max": 262144,
            }
            benchmarks = [
                self.benchmark("maximumTrafficAtOneTimesSpeed", frame_p95),
            ]
            if second_speed:
                benchmarks.append(
                    self.benchmark("maximumTrafficAtTwoTimesSpeed", frame_p95)
                )
            (root / "budget.json").write_text(
                json.dumps(budget),
                encoding="utf-8",
            )
            (results_dir / "atc-benchmarkData.json").write_text(
                json.dumps({"benchmarks": benchmarks}),
                encoding="utf-8",
            )
            return subprocess.run(
                [
                    sys.executable,
                    str(BENCHMARK_BUDGET_CHECK),
                    "--budget",
                    str(root / "budget.json"),
                    "--results-dir",
                    str(results_dir),
                    "--summary",
                    str(root / "summary.json"),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

    @staticmethod
    def benchmark(name: str, frame_p95: float) -> dict[str, object]:
        return {
            "name": name,
            "className": "MaximumTrafficBenchmark",
            "metrics": {
                "memoryHeapSizeMaxKb": {
                    "minimum": 100000,
                    "maximum": 120000,
                    "median": 110000,
                    "runs": [100000, 120000],
                }
            },
            "sampledMetrics": {
                "frameDurationCpuMs": {
                    "runs": [[8.0, 10.0, frame_p95]],
                },
                "frameOverrunMs": {
                    "runs": [[-2.0, 1.0, 8.0]],
                },
            },
        }

    def test_accepts_results_within_budget(self) -> None:
        self.assertEqual(0, self.run_check().returncode)

    def test_rejects_budget_regression(self) -> None:
        result = self.run_check(frame_p95=40.0)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("exceeds", result.stderr)

    def test_rejects_missing_speed(self) -> None:
        result = self.run_check(second_speed=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing declared simulation speed", result.stderr)


if __name__ == "__main__":
    unittest.main()
