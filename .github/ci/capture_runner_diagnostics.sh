#!/usr/bin/env bash
set -u

artifact_dir="${1:?Artifact directory is required}"
mkdir -p "${artifact_dir}"

capture() {
  local output_name="$1"
  shift
  "$@" >"${artifact_dir}/${output_name}" 2>&1 || true
}

capture adb-devices.txt adb devices -l
capture adb-server-status.txt adb server-status
capture disk.txt df -h
capture memory.txt free -m
capture kernel.txt uname -a
capture processes.txt ps -ef
capture kvm.txt ls -la /dev/kvm

if [[ -n "${ANDROID_HOME:-}" ]]; then
  capture emulator-acceleration.txt \
    "${ANDROID_HOME}/emulator/emulator" -accel-check
  capture sdk-packages.txt \
    "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --list_installed
fi

if [[ -d "${HOME}/.android/avd" ]]; then
  find "${HOME}/.android/avd" -maxdepth 3 -type f -printf '%p %s bytes\n' \
    >"${artifact_dir}/avd-files.txt" 2>&1 || true
fi
