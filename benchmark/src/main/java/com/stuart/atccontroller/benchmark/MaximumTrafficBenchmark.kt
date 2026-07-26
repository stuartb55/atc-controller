package com.stuart.atccontroller.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class MaximumTrafficBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun maximumTrafficAtOneTimesSpeed() = benchmarkTraffic(speed = 1)

    @Test
    fun maximumTrafficAtTwoTimesSpeed() = benchmarkTraffic(speed = 2)

    private fun benchmarkTraffic(speed: Int) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            compilationMode = CompilationMode.Partial(warmupIterations = 1),
            iterations = 5,
            startupMode = null,
            setupBlock = {
                killProcess()
                pressHome()
                startActivityAndWait()
                openMaximumTrafficShift(speed)
            },
            measureBlock = {
                // Ten seconds captures one hundred deterministic engine frames at 1x and 2x.
                Thread.sleep(MEASUREMENT_MILLIS)
            },
        )
    }

    private fun MacrobenchmarkScope.openMaximumTrafficShift(speed: Int) {
        device.wait(Until.hasObject(By.text("CHOOSE A SHIFT")), UI_TIMEOUT_MILLIS)
        device.findObject(By.text("CHOOSE A SHIFT")).click()

        device.wait(Until.hasObject(By.text("BUILD A CUSTOM SHIFT")), UI_TIMEOUT_MILLIS)
        device.findObject(By.text("BUILD A CUSTOM SHIFT")).click()

        device.wait(
            Until.hasObject(By.desc("Next Traffic density option")),
            UI_TIMEOUT_MILLIS,
        )
        device.findObject(By.desc("Next Traffic density option")).click()
        device.wait(Until.hasObject(By.text("Busy")), UI_TIMEOUT_MILLIS)
        device.findObject(By.text("START PRACTICE")).click()

        check(
            device.wait(
                Until.hasObject(By.descContains("Terminal radar")),
                UI_TIMEOUT_MILLIS,
            ),
        ) { "Busy deterministic shift did not reach the radar" }
        if (speed == 2) {
            check(
                device.wait(
                    Until.hasObject(By.descContains("Simulation speed 1")),
                    UI_TIMEOUT_MILLIS,
                ),
            ) { "Simulation speed control was not available" }
            device.findObject(By.descContains("Simulation speed 1")).click()
            device.wait(
                Until.hasObject(By.descContains("Simulation speed 2")),
                UI_TIMEOUT_MILLIS,
            )
        }
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.stuart.atccontroller"
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val MEASUREMENT_MILLIS = 10_000L
    }
}
