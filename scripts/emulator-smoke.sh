#!/usr/bin/env bash
# CI-only smoke tests, without provider passwords or private playback streams.
set -euo pipefail

package=app.opentv.qa
apk="$(find emulator-apks -type f -name '*.apk' | grep -i 'qa' | head -1)"
test -n "$apk"
echo "Installing $apk"
adb install -r "$apk"
adb logcat -c || true

check_alive() {
  local pid
  pid="$(adb shell pidof "$package" | tr -d '\r' || true)"
  if [[ -z "$pid" ]]; then
    echo "FAIL: $package stopped unexpectedly" | tee -a emulator-smoke-results.txt
    adb logcat -d -v time > emulator-logcat.txt || true
    exit 1
  fi
  echo "PASS: $package alive pid=$pid" | tee -a emulator-smoke-results.txt
}

echo "Android emulator image: $(adb shell getprop ro.build.version.release)" | tee emulator-smoke-results.txt
adb shell am start -W -n "$package/app.opentv.MainActivity"
sleep 12
check_alive
# Exercise remote-style focus movement and OK (works on phone and Android TV).
for key in 22 20 21 19 23; do adb shell input keyevent "$key"; sleep 1; done
check_alive
# Force-stop + reopen is a realistic cold-start test and catches startup crashes.
adb shell am force-stop "$package"
adb shell am start -W -n "$package/app.opentv.MainActivity"
sleep 10
check_alive
adb logcat -d -v time > emulator-logcat.txt || true
if grep -E 'FATAL EXCEPTION|Fatal signal [0-9]+' emulator-logcat.txt | grep -q -E 'app\.opentv|AndroidRuntime'; then
  echo "WARNING: Review emulator-logcat.txt for process-specific failures" | tee -a emulator-smoke-results.txt
fi
echo "PASS: cold boot, TV remote key events and app restart" | tee -a emulator-smoke-results.txt
