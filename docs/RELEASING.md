# Building and releasing MyIPTV

The Android application IDs are `app.opentv.debug` for internal/debug builds and
`app.opentv` for release builds. They are different Android apps and do not
upgrade over one another. To update an installed app while retaining its data,
the application ID **and** signer certificate must both match, and versionCode
must not decrease.

## Required signing setup

The build and release workflows both use these four **GitHub Actions repository secrets**:

- `MYIPTV_KEYSTORE_BASE64`: Base64 encoding of the project's saved JKS file.
- `MYIPTV_KEYSTORE_PASSWORD`: password to open the JKS.
- `MYIPTV_KEY_ALIAS`: the key alias.
- `MYIPTV_KEY_PASSWORD`: password for the signing key.

Configure at Repository → Settings → Secrets and variables → Actions →
New repository secret. Never place keys, passwords or Base64 keystore bytes in
source control, tickets or build logs. Preserve a separately secured backup
of the keystore and credentials. Losing the key prevents in-place upgrades.

Until all four secrets are set:
- `.github/workflows/build.yml` still compiles and tests a **temporary debug
  APK** for development, but warns that its signer is ephemeral. Do not
  present it as an update-safe build.
- `.github/workflows/release.yml` **fails before packaging** rather than
  publishing a release signed with a random runner key.
- Running `assembleRelease` locally without a signing configuration can
  produce an unsigned APK; it must not be distributed.

When secrets are configured, the debug workflow signs `app.opentv.debug`
with the permanent certificate, and the tagged release workflow signs
`app.opentv` with that same project key. Both build types have distinct
package names and therefore cannot upgrade each other.

The build workflow logs the APK's public SHA-256 signer fingerprint and
attaches `signing-fingerprint.txt` alongside the APK. Compare that digest
across consecutive runs, rather than inferring identity from successful builds.

## Updating an existing installation

1. Confirm the installed package name and its certificate fingerprint.
2. Compare both with the next APK and confirm its versionCode is higher.
3. If package and certificate match, install the new APK as an update and
   retain the existing app data.
4. If the old temporary debug private key is unavailable, in-place upgrading
   from that old certificate is impossible. Export any supported provider
   setup/data first. A one-time uninstall/reinstall may be necessary, and
   uninstalling can erase app-local data.

## Versioning

Bump both `versionCode` (monotonically increasing integer) and
`versionName` in `app/build.gradle.kts`. The initial stability patch is
`0.11.9` / `21`. Android compares `versionCode` to enforce upgrades,
not `versionName`.

## Publishing

Run the unit tests, build and inspect GitHub Actions logs, and verify the
signer before cutting a `v*` tag. A successful automated workflow is not
proof of real-device playback or UI correctness; validate those separately
with licensed/authorised IPTV sources before distributing.
