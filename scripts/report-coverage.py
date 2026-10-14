#!/usr/bin/env python3
"""Evidence-based per-requirement CI report. A build is never a gameplay pass."""
import argparse, csv, json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("--device", choices=["phone", "android-tv"], required=True)
p.add_argument("--out", default="emulator-quality")
args = p.parse_args()
smoke = Path("emulator-smoke-results.txt").read_text(errors="replace") if Path("emulator-smoke-results.txt").exists() else ""
inst = Path("emulator-instrumentation.txt").read_text(errors="replace") if Path("emulator-instrumentation.txt").exists() else ""
passed = "PASS android_integration" in smoke and "OK (" in inst and "FAILURES" not in inst
cold = "PASS process_alive: cold_start" in smoke
remote = "PASS process_alive: tv_remote_keys" in smoke
restart = "PASS process_alive: relaunch" in smoke
checks = set()
if passed:
    # These specific assertions appear in MockProviderIntegrationTest.
    checks = {18, 21, 29, 34, 39, 40, 44}
    # Category-local UI searches (41/42) are NOT validated by the all-category
    # repository query. Keep them unverified until selected-category assertions exist.
rows = []
with Path("tests/requirements-coverage.csv").open(newline="") as f:
    source = list(csv.DictReader(f))
for row in source:
    number = int(row["id"])
    status = "NOT_AUTOMATED"
    evidence = "No automated end-to-end assertion for this requirement"
    if row["unit_logic_test"].strip().lower() == "true":
        status = "UNIT_LOGIC_ONLY"
        evidence = "JVM/Robolectric test exercises related logic; UI/device outcome not established"
    if number == 1:
        status, evidence = "BUILD_PASS", "QA APK and test APK compiled before dependent job"
    elif number == 2 and args.device == "phone":
        status, evidence = ("PASS" if cold and restart else
            "FAIL" if cold else "NOT_RUN_ENVIRONMENT"), "Phone cold launch and relaunch process liveness" 
    elif number == 3 and args.device == "android-tv":
        status, evidence = ("PASS" if cold and restart else
            "FAIL" if cold else "NOT_RUN_ENVIRONMENT"), "TV cold launch and relaunch process liveness"
    elif number in checks:
        status, evidence = "PASS", "On-device Android instrumentation assertions against synthetic Xtream/Room"
    elif row["android_integration_test"].strip().lower() == "true" and number not in (2,3):
        status, evidence = "NOT_RUN_OR_FAILED", "Android test suite not confirmed passing"
    if row["last_reported_failure"] == "True":
        status = "OPEN_REAL_DEVICE_REGRESSION"
        evidence = "User-reported regression still needs reproduction and retest on physical device; no emulator assertion of fix"
    rows.append({"id": number, "requirement": row["requirement"], "status": status,
                 "evidence": evidence, "device": args.device})
out = Path(args.out)
out.mkdir(parents=True, exist_ok=True)
(out / f"{args.device}-requirements.json").write_text(json.dumps(rows, indent=2))
with (out / f"{args.device}-requirements.md").open("w") as f:
    f.write(f"# Koky TV QA evidence: {args.device}\n\n")
    f.write(f"Process checks: cold={cold}, remote_survived={remote}, relaunch={restart}; "
            f"on-device fixture integration={'PASS' if passed else 'FAILED / NOT FINISHED'}.\n\n")
    f.write("**Important:** No successful native video codec, visual focus, provider-specific playback, "
            "multi-device NAS or actual-device crash test can be inferred from this run.\n\n")
    f.write("| ID | Requirement | Status | Evidence |\n|---:|---|---|---|\n")
    for r in rows:
        f.write(f"| {r['id']} | {r['requirement']} | {r['status']} | {r['evidence']} |\n")
print("Generated evidence-based coverage: %d requirements on %s" % (len(rows), args.device))
if not passed:
    print("WARNING: Android integration suite did not demonstrate a pass; no integration successes reported")
