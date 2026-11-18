#!/usr/bin/env bash
# Emulator only: use synthetic profiles, stop the process, then replace the package with identical identity/signature.
set -euo pipefail
probe_dir=app/build/outputs/androidTest-results/profile-compatibility
mkdir -p "$probe_dir"
probe_test=com.josxtm.autocontrol.community.sniffer.ProfileCompatibilityTest
probe_package=com.josxtm.autocontrol.community
# connectedDebugAndroidTest removes its packages at teardown. Install both explicitly
# so this separate process/upgrade probe does not depend on Gradle's device cleanup.
adb install -r -t app/build/outputs/apk/debug/app-debug.apk | tee "$probe_dir/initial-install.txt"
grep -q '^Success' "$probe_dir/initial-install.txt"
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | tee "$probe_dir/test-install.txt"
grep -q '^Success' "$probe_dir/test-install.txt"
for probe_phase in seed verify; do
  if [[ "$probe_phase" == verify ]]; then
    adb shell am force-stop "$probe_package"
    adb install -r -t app/build/outputs/apk/debug/app-debug.apk | tee "$probe_dir/install.txt"
    grep -q '^Success' "$probe_dir/install.txt"
  fi
  adb shell am instrument -w -e class "$probe_test" -e privateProfilePhase "$probe_phase" \
    "$probe_package.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$probe_dir/$probe_phase.txt"
  grep -q '^OK (1 test)' "$probe_dir/$probe_phase.txt"
done
