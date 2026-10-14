#!/usr/bin/env bash
# CI-only, mock-provider, isolated app.opentv.qa testing. No personal credentials.
set -Eeuo pipefail
pkg=app.opentv.qa
fixture_pid=""
mkdir -p emulator-screenshots
on_exit() {
  code=$?
  adb logcat -d -v time > emulator-logcat.txt 2>/dev/null || true
  adb logcat -b crash -d -v time > emulator-crashes.txt 2>/dev/null || true
  adb exec-out screencap -p > emulator-screenshots/final.png 2>/dev/null || true
  adb shell uiautomator dump /sdcard/koky-ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/koky-ui.xml emulator-ui.xml >/dev/null 2>&1 || true
  if [[ -n "$fixture_pid" ]]; then kill "$fixture_pid" 2>/dev/null || true; fi
  printf 'RESULT_EXIT_CODE=%s\n' "$code" >> emulator-smoke-results.txt
}
trap on_exit EXIT
apk="$(find emulator-apks -type f -name '*qa*.apk' ! -iname '*androidtest*' | head -1)"
testapk="$(find emulator-apks -type f -iname '*androidtest*.apk' | head -1)"
test -s "$apk" && test -s "$testapk"
media="$(find emulator-apks -type f -name qa_seek_fixture.mp4 | head -1)"
test -s "$media" || { echo "FAIL synthetic_media_missing" | tee -a emulator-smoke-results.txt; exit 1; }
cp "$media" "$RUNNER_TEMP/koky-fixture.mp4"
python3 scripts/mock-xtream-server.py --media-file "$RUNNER_TEMP/koky-fixture.mp4" > emulator-fixture.log 2>&1 &
fixture_pid=$!
for retry in {1..30}; do
  if python3 -c 'import urllib.request; urllib.request.urlopen("http://127.0.0.1:18080/health", timeout=1)' 2>/dev/null; then break; fi
  sleep 1
done
python3 - <<'PY'
import json, urllib.request
with urllib.request.urlopen("http://127.0.0.1:18080/health", timeout=5) as response:
    health=json.load(response)
if health.get("ok") is not True or health.get("media") is not True:
    raise SystemExit("FAIL: provider did not start with valid seekable media")
PY
echo "PASS fixture_http" | tee emulator-smoke-results.txt
adb install -r "$apk"
adb install -r "$testapk"
adb logcat -c || true
adb shell pm clear "$pkg"
adb shell am start -W -n "$pkg/app.opentv.MainActivity"
sleep 10
check_alive() {
  local pid
  pid="$(adb shell pidof "$pkg" | tr -d '\r' || true)"
  if [[ -z "$pid" ]]; then
    echo "FAIL process_missing: $1" | tee -a emulator-smoke-results.txt
    exit 1
  fi
  echo "PASS process_alive: $1" | tee -a emulator-smoke-results.txt
}
check_alive cold_start
adb exec-out screencap -p > emulator-screenshots/cold-start.png || true
for key in 22 20 21 19 23; do adb shell input keyevent "$key"; sleep 0.6; done
check_alive tv_remote_keys
adb exec-out screencap -p > emulator-screenshots/dpad.png || true
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/app.opentv.MainActivity"
sleep 8
check_alive relaunch
echo "RUNNING Android framework and synthetic Xtream integration tests" | tee -a emulator-smoke-results.txt
adb shell am instrument -w -r "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee emulator-instrumentation.txt
if grep -Eq '^FAILURES|INSTRUMENTATION_FAILED|Process crashed' emulator-instrumentation.txt ||
    ! grep -Eq '^OK \([0-9]+ tests?\)' emulator-instrumentation.txt; then
  echo "FAIL android_integration" | tee -a emulator-smoke-results.txt
  exit 1
fi
echo "PASS android_integration" | tee -a emulator-smoke-results.txt
