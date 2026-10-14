# Protected real-provider QA

The real provider URL, username, and password are deliberately **not in this
repository**. Do not place them in Issues, Pull Requests, code, Gradle command
line arguments, screenshots, or public test reports.

## One-time owner setup

1. Go to [Repository Settings → Environments](https://github.com/zenzehar/Open-IPTV-Smarters/settings/environments).
2. Create environment `live-provider-qa` and configure its **required
   reviewers** and branch restrictions so unreviewed code cannot retrieve the
   secrets.
3. Create three **environment secrets** with these exact names:
   `KOKY_PROVIDER_URL`, `KOKY_PROVIDER_USERNAME`, `KOKY_PROVIDER_PASSWORD`.
   Paste the private values directly in GitHub's secret editor; never commit them.
4. From the repository's Actions tab, select the existing **Build** workflow,
   choose **Run workflow** on the testing branch, enable `live_provider_qa`,
   and leave `allow_insecure_http` **unchecked** initially.
5. Approve the protected environment run after reviewing the branch. If HTTPS
   is unavailable, CI will fail safely without sending the password over HTTP.

**Security:** The supplied endpoint uses HTTP. When the HTTPS check fails,
do not enable plaintext testing casually: any intermediary can read the account
credentials. The workflow can allow HTTP only after a deliberate manual
`allow_insecure_http` selection, accepting that specific risk.

## What the secret-only job actually tests

- Python read-only requests: authentication, Live/movie/series categories,
  complete catalogue parsing, first-series episode schema, and optional bounded
  8 KiB range probe from one video.
- Koky TV's own Kotlin `XtreamApi` against the same protected endpoint:
  login, real Live/VOD/Series entries and episode metadata.
- TLS validation and redirect blocking; no accidental cross-host credential
  forwarding; account/session contents are neither saved nor printed.
- JSON status artifact includes only PASS/FAIL/NOT_REQUESTED per check. It never
  contains real titles, URLs, credentials, account status, media samples, or
  provider response bodies.

The private job is **never** called by PR or push events and cannot run unless
selected manually; ordinary CI continues using only synthetic credentials.

## Important limits

A real-provider metadata test does not prove TV hardware codecs, movie seeking,
libVLC exits, TV remote focus, HDMI rendering, server bandwidth, or multi-device
NAS synchronization. Those require separately instrumented emulator workflows
or actual device logs. Avoid assuming that a server-side HTTP byte-range test
proves a player's seekbar works.

The connected GitHub integration currently cannot configure GitHub secret
values directly. The repository owner must add them to the protected GitHub
environment through the private GitHub Settings UI.
