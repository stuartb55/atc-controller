#!/usr/bin/env bash
set -Eeuo pipefail

artifact_dir=".github/ci-artifacts/performance"
package_name="com.stuart.atccontroller"
mkdir -p "${artifact_dir}"

capture_adb() {
  local output_name="$1"
  shift
  adb "$@" >"${artifact_dir}/${output_name}" 2>&1 || true
}

capture_diagnostics() {
  local exit_status="$1"
  set +e
  printf '%s\n' "${exit_status}" >"${artifact_dir}/script-exit-code.txt"
  capture_adb adb-devices.txt devices -l
  capture_adb boot-properties.txt shell getprop
  capture_adb display-size.txt shell wm size
  capture_adb display-density.txt shell wm density
  capture_adb app-package.txt shell dumpsys package "${package_name}"
  capture_adb exit-info.txt shell dumpsys activity exit-info "${package_name}"
  capture_adb gfxinfo.txt shell dumpsys gfxinfo "${package_name}" framestats
  capture_adb meminfo.txt shell dumpsys meminfo "${package_name}"
  capture_adb logcat.txt logcat -d -b all -v threadtime
  adb exec-out screencap -p >"${artifact_dir}/screen.png" 2>/dev/null || true
}

trap 'status=$?; trap - EXIT; capture_diagnostics "${status}"; exit "${status}"' EXIT

adb wait-for-device
adb logcat -c || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell wm size 1080x2400
adb shell wm density 420
device_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
if [[ -z "${device_locale}" ]]; then
  device_language="$(adb shell getprop persist.sys.language | tr -d '\r')"
  device_country="$(adb shell getprop persist.sys.country | tr -d '\r')"
  device_locale="${device_language}-${device_country}"
fi
printf '%s\n' "${device_locale}" >"${artifact_dir}/configured-locale.txt"
if [[ "${device_locale}" != "en-US" && "${device_locale}" != "en_US" ]]; then
  echo "Expected the deterministic en-US emulator locale, found ${device_locale}." >&2
  exit 1
fi

if [[ ! -f benchmark/build.gradle.kts && ! -f benchmark/build.gradle ]]; then
  echo "The required :benchmark module is missing." >&2
  exit 1
fi
if [[ ! -f benchmark/performance-budget.json ]]; then
  echo "The checked-in benchmark performance budget is missing." >&2
  exit 1
fi

set +e
./gradlew \
  --no-daemon \
  --stacktrace \
  --dependency-verification=strict \
  :benchmark:connectedBenchmarkAndroidTest \
  2>&1 | tee "${artifact_dir}/gradle-macrobenchmark.log"
gradle_status="${PIPESTATUS[0]}"
set -e
if [[ "${gradle_status}" -ne 0 ]]; then
  exit "${gradle_status}"
fi

python3 .github/ci/enforce_benchmark_budget.py \
  --budget benchmark/performance-budget.json \
  --results-dir benchmark/build/outputs \
  --summary "${artifact_dir}/budget-summary.json"
