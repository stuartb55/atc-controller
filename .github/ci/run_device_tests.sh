#!/usr/bin/env bash
set -Eeuo pipefail

api_level="${1:?Android API level is required}"
artifact_dir=".github/ci-artifacts/device-api-${api_level}"
package_name="com.stuart.atccontroller"
mkdir -p "${artifact_dir}"

logcat_pid=""

capture_adb() {
  local output_name="$1"
  shift
  adb "$@" >"${artifact_dir}/${output_name}" 2>&1 || true
}

capture_diagnostics() {
  local exit_status="$1"
  set +e
  printf '%s\n' "${exit_status}" >"${artifact_dir}/script-exit-code.txt"
  if [[ -n "${logcat_pid}" ]]; then
    kill "${logcat_pid}" 2>/dev/null
    wait "${logcat_pid}" 2>/dev/null
  fi

  capture_adb adb-devices.txt devices -l
  capture_adb emulator-name.txt emu avd name
  capture_adb boot-properties.txt shell getprop
  capture_adb display-size.txt shell wm size
  capture_adb display-density.txt shell wm density
  capture_adb system-settings.txt shell settings list system
  capture_adb global-settings.txt shell settings list global
  capture_adb secure-settings.txt shell settings list secure
  capture_adb instrumentation.txt shell pm list instrumentation
  capture_adb packages.txt shell pm list packages -f
  capture_adb app-package.txt shell dumpsys package "${package_name}"
  capture_adb test-package.txt shell dumpsys package "${package_name}.test"
  capture_adb exit-info.txt shell dumpsys activity exit-info "${package_name}"
  capture_adb activities.txt shell dumpsys activity activities
  capture_adb processes.txt shell ps -A
  capture_adb memory.txt shell dumpsys meminfo "${package_name}"
  capture_adb logcat-final.txt logcat -d -b all -v threadtime
  adb exec-out screencap -p >"${artifact_dir}/screen.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/atc-ci-window.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/atc-ci-window.xml \
    >"${artifact_dir}/window-hierarchy.xml" 2>&1 || true
}

trap 'status=$?; trap - EXIT; capture_diagnostics "${status}"; exit "${status}"' EXIT

adb wait-for-device
adb logcat -c || true
adb logcat -b all -v threadtime >"${artifact_dir}/logcat-live.txt" 2>&1 &
logcat_pid="$!"

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

set +e
./gradlew \
  --no-daemon \
  --stacktrace \
  --dependency-verification=strict \
  connectedDebugAndroidTest \
  2>&1 | tee "${artifact_dir}/gradle-device-tests.log"
gradle_status="${PIPESTATUS[0]}"
set -e

set +e
python3 .github/ci/verify_android_test_results.py \
  --source-dir app/src/androidTest \
  --results-dir app/build/outputs/androidTest-results/connected \
  --summary "${artifact_dir}/test-discovery-summary.json"
verification_status="$?"
set -e

if [[ "${gradle_status}" -ne 0 ]]; then
  echo "Gradle instrumentation execution failed with status ${gradle_status}." >&2
  exit "${gradle_status}"
fi
if [[ "${verification_status}" -ne 0 ]]; then
  echo "Instrumentation discovery/result verification failed." >&2
  exit "${verification_status}"
fi
