# Keep the current radio station and reconnect after a stream interruption

Related: [songloft-org/songloft#531](https://github.com/songloft-org/songloft/issues/531), 2026-10-20.

## Behavior and cause

The Flutter client treated live `completed` events as ordinary song completion, applying play mode, queued-next requests and the song-count sleep timer. Loading failures also exhausted a seven-retry network budget before skipping stations. Both paths need correction.

Completion and runtime errors for `isLive` streams now reconnect only the current station. Failed loads retry indefinitely with 2, 4, 8 and then 10-second delays. The exponent is clamped before calculation so long outages cannot overflow into zero-delay retries. Interruptions do not report a finished song, consume queued-next requests or decrement the sleep song count; the selected play mode stays unchanged. Ordinary songs retain their existing finite retry and completion behavior.

Pause, stop, queue clearing, manual station changes and Provider disposal invalidate the old reconnect generation. Pause also invalidates pending Handler loads so they cannot start playback afterward. Notification play can reload a paused/stopped live stream. Duration-based sleep timers can still stop reconnection.

Native validation also found that failed loads put the underlying player into idle and deactivate the media session. Reconnect waits now publish buffering with playback intent, preserving the foreground media session and pause/stop controls. Pause and stop clear this state immediately.

## Validation object

- Repository `songloft-org/songloft-player`, baseline `d89934eea468c22f635d28e5bfd0b5fe81c98147` plus uncommitted worktree; parent baseline `79fb357a59dc9f0eb9364b2a17e1b42c8b67d908`.
- Code diff identifier: SHA-256 of `git diff --binary -- lib test` is `639a6245afa55c42998dde10e307e8a19c1c4630bf6ed6f2c8c1a7b9d39863d2`. Detailed objects and logs are in `/tmp/songloft-531-20261020/`.
- Environment: Linux amd64, Flutter 3.44.8, Dart 3.12.2; an isolated Go dev,lite backend running locally and two synthetic MP3 stations, without a private music library.

## Validation record

- The initial full suite passed 764 tests. After the media-session correction, 291 focused tests passed and the final `flutter test --no-pub --concurrency=2` passed all 767 tests (`full-final.log`). `flutter analyze --no-pub` found no issues (`analyze-media-final.log`); formatting and `git diff --check` pass.
- Regressions cover all five play modes, queued-next requests, sleep song counts, recovery on the same station after three simulated offline minutes, initial-load cancellation, duplicate completion events, pause/stop/skip/dispose, notification resume, duration sleep timers and reconnect media state.
- With the old Provider and backoff isolated, new completion-routing, exhausted-retry and long-backoff regressions fail on the intended behavior: switching to station B or calculating a zero delay for a very large retry count. All 9 targeted regressions failed (`before-final.log`). Separately disabling media-session preservation also failed the buffering regression as expected (`media-negative.log`). Compilation errors do not count as reverse validation.
- The first Android Debug x86_64 build passed. On the API 34 emulator, the real UI's Play all action started the two-station queue; MediaSession confirmed advancing position on station A and continued requests for A over the first 20 offline seconds. Host memory pressure then terminated the emulator and the 110-second sample timed out; this is incomplete acceptance. A test also timed out during host contention; the final 767-test suite passed after stopping the emulator, with earlier failure logs retained. The observed idle media-session issue was corrected.
- The final Android Debug x86_64 APK builds successfully (`apk-final.log`), SHA-256 `6ce61569845db6933fd2fd5a1c413f26869cb093ee85b9ac0e3cff0d03f2d96f`. On the API 34 emulator with two stations and list loop, a 110-second outage produces 12 upstream requests, all for A, while the media session stays active/buffering. Recovery plays A again; position advances from 17,575ms to 20,933ms over a 3-second sample. Media pause stops requests for 27 seconds and remains paused after network recovery; media play resumes A. Stopping during another outage prevents new requests after recovery beyond the 10-second retry interval and deactivates the session. `native-result.json`, `native-probe.log` and `native-{playing,offline,recovered,stopped}.png` capture state/screens; `validation.json` summarizes the validation object.
- Not run: the reporter's Android hardware and Docker deployment, actual iOS/desktop/Web audio and long background sessions, DLNA casting, the Bundle backend and remote CI. This change targets the Flutter client; the Lynx client is unchanged.

Local validation preceded commit. The user subsequently authorized commit, push and an Issue reply. Before committing, the client fast-forwarded to `2621aecda59f70fec344954f000ba57532aa7abf` and the parent to `ebd9bc42f1d2fe9a6a39be58437fba719a5a6a05`; both remote changes only update CHANGELOG, and the code diff identifier above is unchanged. Final commits and post-push CI are tracked by their exact SHAs in Git/workflows. No stable release tag is created in this batch.

Suggested commit: `fix(player): 电台断流持续重连并保留当前台 (Ref songloft-org/songloft#531)`.

Additional investigation: Lynx remote `main` at `f3f78b3aae6e74323b963a9e8c940bf74b3613b5` retains equivalent paths: live completed events use ordinary completion routing, and failed retries at 1/3/9 seconds may skip stations. This batch neither changes nor validates Lynx; the Issue reply must state this boundary and must not close the Issue.
