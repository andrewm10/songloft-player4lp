# Permanent access tokens (songloft-org/songloft#546)

Settings → Account → Permanent access tokens creates a separate named device credential. The creation dialog copies either the token or the Authorization header and shows the complete credential only once. Clipboard failure leaves it open for retry or manual selection. The paginated list displays names, prefixes, permanent/revoked state and creation/last-use times. Revocation requires confirmation.

`PersonalTokenApi` uses the current server's authenticated Dio at `/api/v1/auth/personal-tokens`. Complete credentials remain only in temporary dialog state, outside Providers and persistent storage; debug logging skips these endpoints. Signing out does not revoke device tokens. A server supporting these endpoints is required; this batch is not released.

2026-10-28, Linux x86_64 / Flutter 3.44.8, baseline `224fec0f74f31139761935c612e1ceb8d8e24717` plus the uncommitted worktree: `dart format lib/ test/`, `flutter analyze --no-pub`, all 793 tests and the embedded release Web build pass. New tests cover creation, both copy formats, clipboard recovery, hiding the credential after closing, confirmed revocation, authenticated API requests and log redaction. Source fingerprints and cross-repository log index: `/tmp/songloft-546-verification.json`; browser evidence: `/tmp/songloft-546-flutter-ui-result.json` and matching PNG.

Not run: native builds/devices across the six platforms, actual Home Assistant / ESPHome and speakers. Commit, push, parent gitlink synchronization and a reply to #546 are authorized for this batch; formal release and Issue closure are outside authorization.

Final adjustment: list timestamps use localized dates/minutes without seconds or microseconds. Analysis and all 793 tests were repeated on the final worktree and pass. The release Web build and actual Chrome create/copy-both-formats/hide/confirm-revoke/401 flow were also repeated and pass.
