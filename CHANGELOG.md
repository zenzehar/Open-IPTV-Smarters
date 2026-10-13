# Changelog

## 0.12.17 — Seek reliability, VLC Back, remote OK hold and startup status

- Keep the movie seek thumb at the requested position until Media3 confirms the seek; retry ignored or unsupported seek operations through embedded VLC with the target position.
- Keep the VLC output attached during the deferred Back transition; route Back from VLC's visible/hidden control states through one guarded exit request. Prevent double pops if Back is pressed repeatedly.
- Implement native remote OK/Enter held-duration handling for Continue Watching cards, independent of Compose touch long-click. MENU key also opens the same actions; short OK still resumes.
- Remove the redundant preview poster and 'Select the poster to resume playback' pane in Continue Watching. Cards now occupy the available content width.
- Prevent false dashboard 'updating' badges during a warm cached VOD check; only show UPDATING for actual foreground provider downloads.
- Skip startup WorkManager enqueuing if persisted per-section stamps show the configured interval has not passed. Retain worker-level due checks and periodic Android scheduling.
- Keep existing package IDs and saved user data; device verification remains required for seek support, native VLC lifecycle and remote key events.


## 0.12.16 — Live category search, launch refresh, Continue Watching reset and safer navigation

- Search live category names within the TV sidebar or phone category bar, independent of the existing channel-title search.
- Remove the redundant dashboard initial Live refresh; configured automatic update frequency now controls launch and scheduled syncs through serialized workers.
- Long-press Continue Watching opens a choice between title details and a confirmed reset of the selected film or entire series' progress for the active profile.
- Keep VOD Back intercepted during deferred video exit; guard repeated Live navigation; restore Home when a player lacks a usable back destination.
- Brand the startup loading screen with the Koky TV emblem and name rather than just a turquoise spinner.
- No destructive migrations, package-ID changes or automatic deletion of provider data. Physical-device QA remains outstanding.


## 0.12.15 — Large-catalogue paging beyond 2,000 titles

- Movie and Series category searches and browse grids now use incremental 160-row SQLite LIMIT/OFFSET slices instead of an expanding 2,001-row limit, removing the prior ceiling and the no-op Load More button.
- Previously loaded posters remain visible while the next 160 are fetched. The Load More button is disabled during loading and hidden at the end of the query results. Category, provider and search changes reset pagination.
- No changes to app IDs, signing setup, credentials, Room schema or watch history. Real-device QA remains necessary for very large categories and playback.


## 0.12.14 — Continue Watching, season focus, accelerated seek, refreshed icon and automatic NAS sync

- First Back while VOD player controls are showing hides the controls and keeps playback running; the next Back returns to the detail screen. An open Media3 track chooser still dismisses before hiding playback controls.
- Long-hold Left or Right on Android TV accelerates Media3/VLC seek from normal 5/10-second steps to 20/30-second and then 60-second steps using Android key-repeat events; regular short taps remain precise.
- Long-press a movie or series entry in Continue Watching to open its full detail screen, retaining ordinary short-click to resume. Cards show watched minutes, total minutes and completion percentage.
- After launching an episode from a series detail screen, going Back restores that show's selected season, scrolls to the just-watched episode row, and requests TV focus on that episode.
- Movies and Series local search now runs a parameterised Room query against the entire *cached* provider category, with bounded 160-item pages and Load More rather than searching only the recent/curated shelves. Full catalogue search continues to depend on titles actually cached by provider sync.
- Redesigned Koky TV adaptive launcher icon: original cyan/violet TV and white play emblem, reproduced in the Home header. Existing package names unchanged.
- Opt-in automatic background sync using the user's existing configured SMB/NAS relay runs periodically at approximately 15-minute intervals through WorkManager, independent of the app staying open. Each device writes a durable JSON snapshot to the shared NAS and merges the other devices' last saved snapshots on its next online window. Both Android devices need not run concurrently, but the shared NAS/server must remain available. Background execution may be deferred by Android. Existing LAN/manual sync remains unchanged.
- No destructive migrations. QA still required for actual TV key repeats, long-press on remote controls, restoring episode selection, large-category database search and remote SMB reachability.


## 0.12.13 — category-local search and focusable remote seeking

- Search in Live TV, Movies and Series is now bound to the visible screen's actual state. The Live header and both Live guide layouts explicitly share one ChannelsViewModel; the selected Live category and provider continue to constrain search results.
- Changing Movies or Series categories no longer silently clears an active search; the same term filters the newly selected category in place. Source changes also preserve the query.
- Search filtering now covers the original “All” movie/series shelves (Continue Watching, Recommended, Recently Added and genre rows), not just standalone category grids. Empty matches show a clear “No matching titles” state instead of unfiltered or blank rows.
- Pressing the visible category Search icon focuses that field and explicitly opens the keyboard. Simply moving TV remote focus does not open the keyboard.
- VOD Media3 and VLC timelines are now explicitly focusable from the remote: Up from the control buttons selects the seek bar, Left/Right moves the playback cursor by 5 seconds per key press, Down returns to the controls, and Up from the timeline moves to the Back button. The focused timeline has an obvious highlight. While the timeline is focused, the control bar remains visible so the user can adjust an exact point.
- Background-video arrows still reveal controls and seek by 10 seconds when the bar is hidden, while standard Left/Right continue to navigate buttons when the seek bar itself is not focused.
- Preserve all package identifiers, provider credentials, watched history and existing player buffering settings. Verify precise Android TV D-pad navigation and search behaviour on physical hardware.


## 0.12.12 — TV remote playback, clean login focus and stable exit

- TV login now shows remote-focusable, clearly bordered, inert field tiles. Actual Android TextFields are created only after pressing OK; this prevents provider TV keyboards from flashing open while moving between login fields. Phone input remains native.
- Live player: dedicated Channel Up/Down still tune channels; ordinary D-pad arrows reveal the bar and navigate its selectable transport controls instead of unexpectedly changing channels.
- Playback: D-pad Up/Down reveals the transport overlay. Left/Right reveals the overlay and seeks by 10 seconds when playback background has focus or controls are hidden. When visible, focus is moved onto the Pause/Play button so directional navigation can select all transport chips; embedded VLC gets matching arrow handling and its own focusable Play/Pause.
- Player Back consistently exits when no track picker is open rather than consuming a press merely to hide the bar. Back at the top left also remains visible while playback is paused.
- Player return removes the media surface and navigates after a short frame, with deferred Media3 release on its required main looper, and VLC native stop/release performed away from Compose teardown. Saved position persists on exit; the same stream format fallback and autoplay behaviour remain.
- Title pages show a restrained gradient instead of stretching or cropping inconsistent provider backdrop art. Poster remains separately fitted, 144×214dp with rounded clipping.
- No Room or credentials migration, no package/signature change introduced intentionally. TV and codec-specific crashes must still be validated on hardware.


## 0.12.11 — Koky TV, TV stability and contextual search

- Display name and launcher label changed to **Koky TV** across device locales using an unlocalized launcher label. Retain Android application IDs for safe updates, Room data, favourites and history.
- Android TV Back behaviour: no blocking exit dialog. Back from a section returns Home; the second Back warns with a toast, and the third exits. The Home-only first Back primes the sequence, with a five-second timeout.
- Movies category crash mitigation: query one bounded 160-title page from Room instead of subscribing to the entire selected category; more titles via a Load More button. Perform quality-variant grouping off the main thread as before.
- Media3 video Back path now unmounts the Android player view before its native decoder is released. Embedded VLC suppresses late native callbacks during shutdown to reduce device-dependent hangs.
- VOD starts after about 1 second of media is buffered instead of 2.5 seconds; 15–60-second target reservoir and 5-second rebuffer threshold remain unchanged. Live/recording buffer profiles untouched.
- Stronger Android TV keyboard suppression: on mere D-pad focus, hide the platform IME repeatedly; pressing OK/Enter or tapping a text field deliberately enables it.
- Double Down/Up jumps now require 1–7 seconds between the presses, so rapid sequential category navigation does not jump unexpectedly.
- The Live TV top search field now scopes channels to the currently selected category/provider and preserves the existing EPG or channel-list layout. Movies and Series search icon focuses the local filter in their existing poster layouts instead of opening global Search. The Home tab retains global Search.
- No destructive database migration or credential deletion. Real TV testing and stack traces are still required to confirm crash causes and hardware-specific playback issues.


## 0.12.10 — TV keyboard, Movies responsiveness, catalogue progress and screen density

- Android TV keyboard: moving focus over login fields, category search or global search no longer opens the software keyboard. Explicit OK/Enter or pointer selection opens the current Android keyboard. Login submission still dismisses it.
- Movie-quality variant grouping runs on Dispatchers.Default rather than the Compose main/UI thread; a loading spinner replaces a frozen browsing grid during computation.
- Require deliberate Down/Up TV remote shortcut presses separated by at least 2 seconds (and no more than 7). Rapid presses now simply navigate; two deliberate Down jumps to the bottom navigation and Up to the selected Movies/Series category.
- Catalogue progress is emitted from actual milestones (authentication, category retrieval, stream retrieval, database writes) rather than only completed-provider counts. Home tiles animate and brighten as checkpoint progress advances. Network transfer without measurable bytes is still shown as indeterminate buffering, not a fictitious exact percentage.
- Live TV uses an explicit loading stage during initial provider onboarding, and cached Live is shown as available rather than Waiting while other catalogues update.
- Choose automatic channel, Movies, Series and EPG refresh frequency: Off, every 1, 3, 6, 12, or 24 hours. WorkManager updates/cancels its recurring task immediately; manual Update remains available. Existing installs default to 6 hours.
- Move Movies/Series title filter into the shared top header; remove redundant browse-area search controls to leave more room for posters.
- Shrink poster presets by one tier: Small 92dp, Medium 108dp (old Small), Large 124dp (old Medium); default Medium.
- Unified Live, VOD (Media3) and embedded VLC auto-hide control timeout: Never, 2, 3, 5, 8, 10 or 15 seconds. New default 3 seconds; pause/picker interactions suspend auto-hide.
- No Room migration, credential deletion, or playback-history reset. Verify keyboard focus, massive provider-category performance, D-pad timing and update cadence on physical devices before merging.


## 0.12.9 — TV login, player resilience, VOD navigation and visual polish

- TV provider fields use a cyan focused outline against muted idle borders. Submitting a login or Test Connection clears text focus and dismisses the Android keyboard.
- New installs default next-episode autoplay ON; existing saved preferences still take priority.
- Multi-season series detail has a top-centred season selector and displays episodes for the selected season.
- Title detail hero and poster use Fit instead of cropping provider artwork.
- Movies and Series open into Recently Added. Searching in a selected category filters only titles in that category, without leaving for global search. Empty grids display a useful empty result.
- Settings > Display offers Small/Medium/Large movie and series poster sizes, defaulting to smaller Medium. Resizing also changes whole-category grid density.
- Show cached movie and series catalogue entries while a background refresh proceeds, avoiding a second blocking load when selecting a section.
- Start an initial Live catalogue sync on a first run with no cached Live content; show per-provider completion progress in the appropriate Home tile. These are completed provider counts, not invented byte download percentages.
- For VOD startup that stays in Media3 Buffering for 25 seconds without ever Playing, switch to embedded VLC. Avoid switching after normal playback has started. The VLC timeline displays no greater than its known duration.
- In TV Movies/Series, two quick Down presses jump to bottom navigation and two quick Up presses return to the selected category tile.
- No credential or database migration; real-TV stream compatibility and remote-focus navigation still require testing.


## 0.12.8 — remote-focus reliability for compact Home and Settings

- Fix the missing initial D-pad focus on the Home dashboard when TV/remote layout is selected on a portrait or narrow screen. Choose the first available tile (Live TV, Movies, Series, or Recordings) consistently in both compact and wide Home layouts.
- Restore focus to the initial Home tile after changing between compact and wide screen layouts. Add focused unit tests for the tile-selection fallback policy.
- In TV/remote mode, open Settings with focus on its first action rather than relying on implicit Android focus discovery. Apply this to both the compact settings list and TV settings grid, including after a width change.
- Retain natural Android orientation, system-bar insets, existing Back behaviour, playback engines, IPTV credentials and watch history; no Room migrations.
- Signing is not yet permanent, and physical remote navigation still requires device verification.


## 0.12.7 — system orientation, TV remote navigation and title Back

- Stop forcing the Home screen into landscape. Phones and tablets follow Android's current orientation; Home rearranges responsively without deliberate scrollbars.
- Remove the obsolete Landscape Home toggle. TV/remote mode remains selectable under Settings > Device layout; Automatic remains the default.
- TV D-pad users land on a selectable Home tile, and the selected bottom TV navigation tab can take focus. Add clear white focus outlines for Home tiles, navigation and shortcuts; remote arrows move through Compose's standard focus tree and OK activates the selected control.
- Provide a fixed, focusable Back button on movie, series and cast-person detail screens. Android/TV remote Back invokes the same navigation; buttons remain visible while the detail content is loading or scrolling.
- Preserve video playback behaviour, saved logins, favourites, progress and the existing separate QA Android package. No database schema change.


## 0.12.6 — fixed Home, expiry and account management

- The dashboard no longer scrolls: Live TV, Movies, Series, shortcuts and the playlist/expiry footer are all sized to the screen’s available height.
- Display the Xtream provider's real account expiry date from `user_info.exp_date`, if returned, in the Home footer by default. Cache expiry locally per source and refresh the account response on startup at most daily; no extra Test button.
- Allow any viewing profile, including the original, to be removed if at least one will remain. Require explicit confirmation before deleting that profile’s resume history; preserve recordings and select a remaining profile if needed.
- Add **Factory reset** to Settings with a two-stage destructive confirmation. Android clears MyIPTV’s private application data, including provider credentials, playlists, favourites, profiles, playback history, settings and internal recordings. External NAS/USB files are not intentionally deleted.
- Reuse the existing signing and side-by-side MyIPTV QA mechanisms; no Room schema version change.


## 0.12.5 — login and TLS diagnostics

- Detect likely HTTPS/TLS-on-HTTP-port mismatch and explain what to check without showing passwords or private URLs.
- Offer explicit, consent-gated HTTP correction for Xtream HTTPS on port 80 only; never downgrade a connection automatically.
- Test provider authentication before saving or editing logins. A failed authentication stays on the login screen, with existing saved accounts unaffected.
- Test Connection now contacts M3U playlist servers instead of incorrectly returning an unconditional success.
- Failed TV phone-pairing setup returns to an editable, pre-filled login form and shows an actionable error.
- Clear outdated connection errors when any login field changes; ensure credential fields never join pasted lines.
- Side-by-side MyIPTV QA package remains available for non-destructive device testing. Real provider success must still be confirmed by the user.


## 0.12.4 — catalogue failure diagnostics

- Home refresh now reports safe per-category failure reasons. Tap the Failed footer for details and Retry; no IPTV account credentials are shown.
- Serialize Live, Movies and Series provider requests across manual, automatic and periodic catalogue refreshes to reduce provider rate limiting and request collisions.
- Constrain oversized header profile names so Search, Profiles and Settings remain visible on small landscape screens.
- Keep existing saved catalogue rows, favourites, playback history and credentials on failed network refreshes.
- Signing compatibility still depends on one permanent certificate; this development APK must be verified on a device before merging.

## 0.11.8

- **Edit a saved provider.** Settings → Providers now has an **Edit** button on each provider, so you
  can fix a server address, password, MAC or name in place instead of removing and re-adding it —
  your favourites, hidden channels and per-provider settings are kept.
- **Stalker portals: fewer dropped sessions.** A portal that occasionally rejected a call
  mid-session (channels loading only "sometimes") now gets one automatic re-handshake and retry, so
  an intermittent refusal becomes a silent re-auth rather than an empty channel list or a channel
  that won't tune.
- **Player controls stay clear of the system bar off-TV.** On phones and tablets the bottom control
  bar (subtitles, audio, quality, aspect ratio) could be drawn under the navigation bar and cut off;
  it's now inset above it. No change on Android TV.

## 0.11.7

- **Stalker portals: closer to a real box.** Building on v0.11.6, OpenTV now sends the rest of the
  identity a real MAG set-top box presents at login — the auth token in the session cookie (not just
  the header), a hardware-version hash, a timestamp and the box's API signature — and it probes a
  couple more portal paths. Some Ministra/Stalker panels only return the channel list when they see
  all of this, so lines that connected but showed no channels have a better chance of loading. If a
  portal still comes up empty, please open an issue and say which panel software it runs.

## 0.11.6

- **Stalker portals: full set-top-box identity.** OpenTV now authenticates to Stalker/Ministra
  portals the way a real MAG box does — sending the device serial, `device_id`, a signature and a
  metrics blob derived from your MAC, instead of the MAC on its own. Many portals won't hand over
  the channel list or stream links to a box that only presents a MAC, which is why some lines that
  worked in other apps came up empty in OpenTV. This lets those portals authorise OpenTV. If a
  portal still fails, please open an issue and say which one — it helps a lot.

## 0.11.5

- **Add a provider from your phone or laptop.** The "Manage on phone or laptop" web page now has an
  **Add a provider** section — pick Xtream, M3U or Stalker portal, fill it in with a real keyboard,
  then Test and Add. Channels load straight onto the TV, so you never have to type a server address
  or a MAC on the remote again.
- **Fixed the "camera won't scan it?" web address.** The manage screen was printing that link without
  its access token, so typing it into a browser hit a dead "Not found". It now shows the full working
  URL, and the token is short enough to type.


## 0.11.4

The big DVR release. Recordings behave like Sky Q, you can record straight to a NAS, watch a
recording while it's still taping, and the whole interface now speaks 30 languages.

### Recording & DVR

- **Watch a recording while it's still recording.** Start playing something the moment it begins
  taping — OpenTV reads the growing file straight off disk, so it costs **zero extra connections**
  to your provider. On a single-stream account that's the difference between "wait until it's
  finished" and "watch now". Fast-forward and rewind work within whatever's been taped so far.
- **Record straight to a NAS.** Point recordings at a network share over SMB (Settings →
  Recordings → NAS, or set it up from the phone/laptop web manager) so a cheap box isn't boxed in
  by its own storage — and every OpenTV in the house can reach the same recordings.
- **Single-connection auto-switch.** On a one-stream provider, if a recording is due to start on
  another channel, OpenTV moves the screen onto that recording as it begins — with a **30-second
  warning** first and a *Keep watching* button if you'd rather not. No more "why did my live stream
  just cut out?" (Recordings → Recording behaviour → *Auto-switch when recording starts*.)
- **Clash handling with multi-provider fallback.** Book two overlapping recordings and, if more
  than one of your providers carries that channel, OpenTV records each from a **different provider**
  so neither is cut. On a single provider it flags the clash instead.
- **Recording padding.** Start each recording a minute early and run a few minutes past the listed
  end, so a late kick-off or an overrun isn't clipped. Adjustable per side.
- **Series links show the next episode.** A series link now tells you exactly when it next records.
- **Storage readout.** See how much space recordings use and how much is left.

### Live TV

- **Pause & rewind live TV** *(experimental, opt-in).* Holds the last couple of minutes so you can
  pause and jump back. Off by default — it uses more memory.

### Languages

- **30 languages.** The whole interface is translated into Spanish, French, German, Italian,
  Portuguese, Dutch, Polish, Russian, Turkish, Arabic, Simplified Chinese, Japanese, Korean, Hindi,
  Swedish, Danish, Finnish, Norwegian, Czech, Greek, Romanian, Hungarian, Ukrainian, Indonesian,
  Thai, Vietnamese, Bulgarian, Slovak, Croatian and Persian. Settings → Language (each listed in
  its own name), or leave it on *System* to follow your device.

### Fixes & polish

- Smoother watch-while-recording, especially from a NAS: playback now keeps a buffer behind the
  live edge so a network share doesn't stutter at the write head.
- Back never lands you on a stray live channel or drops you out by accident — coming back from a
  recording returns to your recordings, and Back on the home screen asks before it exits.
- Password fields are masked, with a show/hide toggle.
- Recording stability: NAS write timeouts and reconnect handling, so a quiet network doesn't
  silently freeze a capture.

### Notes

- Everything stays on your device — provider logins never leave the box.
- Free and open source (GPL-3.0): https://github.com/opentvproject/opentv


## 0.10.0

- **Movies & Shows, redesigned.** A Plex-style layout with big artwork, cast & director, "more with
  this cast" rows, and cleaned-up titles (no more "NF -" / "(KR)" junk). Optional: add your own free
  TMDB key in Settings → Metadata and OpenTV fills in any posters, backdrops, cast or synopses your
  provider left blank — the key stays on your device.
- **No more stuck "Loading movies & shows".** The movies/series catalogue is cached, so it loads
  instantly on later launches instead of re-downloading every time — and the live preview no longer
  stutters while it loads.
- **Record to a USB / external drive.** Settings → Recordings → USB, pick a folder on a plugged-in
  drive, and recordings write straight there (no storage permission needed) and play back in-app.
- **Recordings screen overhaul.** Scheduled recordings show *when* they'll record and sit in their
  own "Scheduled" section; failed ones have a **Retry**; each is tagged to a profile; and bookings
  re-arm on every launch so a force-stop or app update can't quietly drop them.

## 0.9.0

- **Manage your channels from a phone or laptop.** A new "Manage on phone or laptop" screen in
  Settings shows a QR code and a link — open it on any device on your wifi and you get a proper web
  page to browse, **rename**, hide, favourite and **reorder** channels with a real keyboard and
  mouse. Changes apply to the TV instantly. Local-only: the TV is the server, nothing touches a
  cloud.
- **Recording keeps going when you leave the app or the box sleeps.** OpenTV can now ask Android to
  exempt it from battery optimisation (Settings → Recording → "Recording in the background"), which
  is what keeps recordings running in standby and when you switch away — swiping OpenTV out of
  recents no longer stops a recording either.
- **Rename channels.** Give a channel your own name; it sticks and survives a guide refresh (from
  the web manager for now).
- **Tidier update notes.** The "update available" prompt shows a clean summary of what's new instead
  of build metadata.

## 0.8.0

- **No more endless "Loading channels".** If a provider fails to load or comes back empty (a wrong
  login, a dead server), the guide now shows a clear error with **Retry** and **Provider setup**
  buttons instead of spinning forever.
- **Set up on a phone without the QR dance.** Installing on a phone now shows the login form
  directly so you can type your details in; the QR code is only offered on a TV, where typing is the
  painful part.
- **Paste into the setup fields.** The URL, username and password fields — and the on-screen
  keyboard — now have a **Paste** button, so a copied Xtream line drops straight in.
- **MPEG-TS or HLS, your choice.** Each Xtream provider now has a **Stream format** toggle. If your
  panel only serves `.ts` and channels wouldn't play, switch to MPEG-TS and they will. Defaults to
  HLS, so nothing changes unless you need it.
- **Parental controls scrolls.** The hidden-categories list is reachable to the bottom now, however
  many categories your provider has.
- **A real Channel Manager.** Settings → Channels is now a browsable, two-pane manager: pick a
  category on the left, see its channels on the right, hide or favourite each one — including
  hidden channels so you can bring them back. With more than one provider, a **source filter** keeps
  them separate instead of merged into one list.

## 0.3.0

- **Profiles.** Add local profiles (just a name — no accounts) from the person icon in the top
  menu, and switch between them. Each profile keeps its own resume points and watched state, so
  your half-watched film doesn't show as watched on someone else's profile. Existing resume points
  become the default "Me" profile's — nothing is lost.
- **Continue Watching.** Movies and Shows now open with a "Continue watching" shelf for the active
  profile — pick up your last film or episode where you left off, with a progress bar on each.
- **Local sync (no servers).** Settings → Sync copies your continue-watching between two OpenTV
  devices over your own wifi: one device shares behind a six-digit code, the other receives. It's
  local-only — nothing leaves the house — the household answer to the cloud sync that died with
  Viewella.

- **Proper player controls.** A single control bar slides up from the bottom with play/pause,
  rewind/forward (where the stream allows it), and pickers for **subtitles, audio track, quality
  and aspect ratio**. It hides after a few seconds and any remote button brings it back —
  nothing is ever left painted permanently over the picture.
- **Captions that actually show.** Subtitles are now chosen from the real tracks in the stream
  (and can be turned off), instead of a blind on/off that often left the renderer enabled but
  nothing on screen. Audio-track switching works the same way for multi-language streams.
- **Page through the guide.** Earlier / Now / Later buttons above the guide move the timeline
  forward and back, so you can see what's on later this evening with a remote.
- **Live preview in the guide.** The highlighted channel now plays, muted, inside the preview
  pane. It uses a single player that stops the instant you go full-screen or leave the guide —
  no second decoder — and it can be turned off in Display & playback for older boxes.
- **Channel manager.** Settings → Channels: search for a channel and hide it from the guide (or
  favourite it). Hidden channels drop out of Live TV and search but stay here, greyed, so you can
  bring them back. Hiding covers every quality variant of a channel at once.
- **Parental controls.** Set a 4-digit PIN and mark categories as hidden — they drop out of the
  guide, All channels and search until you unlock them (and with a PIN set, the parental screen
  itself is locked). In Settings → Parental controls.
- **Unified search with an on-screen keyboard.** Search now has its own screen with a d-pad
  keyboard (no more "type on your phone") and covers **channels, movies and shows** together, with
  live results as you type. Reachable from the top menu.
- **Loading indicators for Movies and Shows.** Both now show a spinner while the catalogue is
  syncing instead of a premature "nothing here".
- **Settings and Search in the top menu.** Both now live top-right, next to Live TV / Movies /
  Shows, instead of being buried behind the guide's icons.
- **A single Settings screen.** One Settings hub — Providers (add / remove / re-test sources), TV
  guide, Display & playback, Parental controls, and About (version, a manual update check,
  licence and links).
- **Dark / light / follow-system.** A new appearance setting. TV still defaults to dark; pick
  Dark or Light to force it on any device.
- **Quality picker only when there's a choice.** No more "Quality: Standard" on single-quality
  channels; the picker appears only when a channel actually has multiple qualities.
- New **Display & playback** settings screen (the sliders icon in the guide).

## 0.2.0

- **Guide detail pane** — highlighting a channel now shows its logo, what's on now with
  start/end times and a live progress bar, a synopsis when the guide carries one, and what's
  on next. Press to watch full-screen.
- **Live progress fill** on the current programme in the guide grid, and a focus highlight on
  the highlighted channel.
- **Fix: never asks for your provider again.** A cold launch could briefly decide "first run"
  before your saved sources had loaded and drop you on the setup screen — it now waits for the
  sources to load before deciding what to show.
- **Fix: no second video decoder on the guide**, which was locking up low-end boxes (a
  Chromecast could freeze). The detail pane is logo + guide info; the one decoder lives
  full-screen.

## 0.1.0

First public release.

- Xtream Codes and M3U/M3U8 sources
- Streaming XMLTV guide parsing with incremental, non-destructive sync
- Live TV playback on Media3/ExoPlayer with IPTV-tuned retry behaviour
- Movies and series catalogue
- Favourites, categories, search
- In-app self-update for sideloaded installs
- Single APK for Android TV, Fire TV, phones and tablets
