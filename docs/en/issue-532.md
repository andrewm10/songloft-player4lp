# #532: Built-in favorites cards on home

Tracks [songloft-org/songloft#532](https://github.com/songloft-org/songloft/issues/532), 2026-10-21; [中文](../cn/issue-532.md).

Settings → Appearance → Home layout now includes “Show Favorites on home” and “Show Radio favorites on home”, both enabled by default. Choices are stored on this device per server identity URL and signed-in account, outside server preference synchronization. Disabling a choice only filters its home card. Library entries, favorite buttons, favorite membership, playlist labels and song lists retain their behavior. The existing radio-section switch still controls the entire section and preserves the independent Radio favorites choice when enabled again.

Identity uses `built_in` and built-in playlist IDs, never playlist names. Filtering precedes carousel/grid/list limits, and pagination counts visible cards when filling the preview. Existing edit/delete/visibility protections for built-in playlists remain. Do not reuse the `hidden` label: existing song queries exclude songs belonging to hidden playlists by default.

Validation object: `songloft-org/songloft-player@98fe97d0ae23a16e5034a56337134c4b4aa142ce` plus this uncommitted worktree. Logs and final source fingerprints are under `/tmp/songloft-532-implementation/`; browser screenshots and reports are recorded at delivery.

- `dart format lib/ test/`, `flutter analyze --no-pub` and 108 home/settings tests passed.
- The broad Flutter run reached 434 passing tests in the shared environment, then was stopped during unrelated 30,000-item queue validation. This is not a complete-suite pass; final affected checks ran separately.
- The Web build passed. Its Wasm dry run exited under resource pressure and is not counted as a successful Wasm build; the final JavaScript Web output succeeded. Actual Chrome checks passed disabling, persistence after reload, retained library entries and recovery. Real API snapshots confirmed unchanged songs, favorite membership and playlist labels, with no page exceptions. Screenshots and final results are recorded in `validation.json` in the same directory.
- Not run: Android/iOS/macOS/Windows/Linux native devices; no native host or audio implementation changed.
- This session authorizes committing, pushing and replying to the Issue. Stable publication and native-device acceptance remain separate. Final commits and CI status are recorded in the follow-up Issue reply and delivery validation record.
