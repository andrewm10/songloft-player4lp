# Lyric Readability and Built-in Palettes · songloft-org/songloft#523

2026-10-15, related to [songloft-org/songloft#523](https://github.com/songloft-org/songloft/issues/523). This batch updates the Flutter mobile and desktop full players and appearance settings.

## 2026-10-17 · Cover Colors Remain Too Subtle

Delivery authorization: after being informed of the stop-test and native-switching acceptance gaps, the user explicitly authorized commits, pushes and a reply to songloft-org/songloft#523 for this batch. Uncommitted states below are validation snapshots. Preserve failed/unrun limits; verify final commits, CI, dev assets and the comment against the same SHA. No new stable tag or Issue closure.

The reporter [confirmed app and system accessibility toggles are off](https://github.com/songloft-org/songloft/issues/523#issuecomment-6228812821), so the 10-16 settings path does not explain this feedback. Baseline `18b50cbca02a7470279ddcd04907f71365140a57` has separate gaps: the default palette filter discards warm browns and monochrome colors; without mutedColor the lower gradient uses the theme color; light mode blends only 12%→4% artwork. Relative cover cache keys lack server identity and can reuse colors after switching servers. These are confirmed local defects, not confirmed causes on the reporter's device.

This batch preserves cover colors and downsamples client decoding to 100px width, caches by actual resource URL, and subscribes to server changes. Actual vibrant color takes priority, falling back to the dominant color. Cover blending becomes 28%→18% in light mode and 24%→18% in dark mode; the lower end falls back from muted to vibrant/dominant color. Self-review adds covers sharing a black dominant color but differing in vivid regions: both light/dark regressions fail with zero color separation before vibrant selection. Text uses black/white cover bounds, including the previous background during song transitions, and strengthens beyond onSurface toward black/white when needed. Missing/failed cover and increased-contrast fallbacks remain.

New real HTTP + PNG decoding tests cover warm/monochrome colors and server switching; color-separation tests cover both gradient ends. Both decoding tests and both separation tests failed on the old implementation. Existing contrast/transition tests also caught the first stronger wash; all 12 related tests pass after extending text bounds. Full objects, commands, screenshots and remaining limits are recorded in `/tmp/songloft-523-followup-20261017/validation.json`, using an isolated database and synthetic media.

Acceptance screenshots: [Android light coral cover](../assets/issue-523/followup-20261017/android-light-coral.png) · [Android light ocean cover](../assets/issue-523/followup-20261017/android-light-ocean.png) (paused queue restoration) · [Web mostly-black cyan cover](../assets/issue-523/followup-20261017/black-cyan-web-light.png).

Final validation: Linux amd64 / Flutter 3.44.8 / Dart 3.12.2; analysis, formatting, embedded Web and Android x64 Debug builds pass. The first candidate passed all 732 tests. Full runs of the final vibrant-color version fail `a pending source finishing after stop cannot auto-play` (731 pass, 1 fail), including a repeat; that test alone and its entire four-test file pass independently. The stop path is unchanged and the cause unconfirmed; an isolated pass does not establish a passing full suite. Color-related checks pass. Docker Chrome mobile/light and desktop/dark switching and fallbacks pass, with upper/lower cover pixel differences of 29–55 and missing/failed fallback difference 0; real decoding and vibrant backgrounds for a mostly-black cyan cover also pass in both modes. Android 34 passes paused restoration and native pixel checks for four songs per mode on the final APK; this does not establish live playback switching. During early-candidate validation, a live Next tap raised `CircularDependencyError` while `PlayerNotifier._syncLiveActivitySong` read lyric state and left loading active; this remains an unresolved acceptance item. Neither provider changed in this batch, and the error's origin is unproven. A later coordinate retry did not advance the song and cannot count as success or non-reproduction. The original error was captured only in the initial logcat tool response; later seed scripts cleared device logs, a limitation recorded in validation. Not run: reporter device, outdoor use, other native platforms and remote CI for the uncommitted worktree. Sanitized screenshots and validation are copied to `/home/hanxi/work/songloft-review/issue-523-20261017/`.

## 2026-10-16 · Solid Background Feedback

Delivery authorization: on 2026-10-16 the user authorized this batch's commits, pushes and a reply to songloft-org/songloft#523. Uncommitted/unposted states below are validation snapshots; Git/GitHub records for the same SHA determine final commits, CI and the comment. No stable tag or Issue closure.

For the [follow-up comment](https://github.com/songloft-org/songloft/issues/523#issuecomment-6210432894), checked current `bb03a5c49eb6701d90a73b924ed12ec388c6560c`: increased contrast deliberately uses a solid surface in the player. App and system high contrast combine with OR; disabling the app toggle cannot override the system. Flutter's Reduce Transparency affects glass materials and does not independently disable this cover-tinted gradient.

A temporary Flutter test runs the actual `AppTheme` and `PlayerBackdropColors`, confirming a cover tint in normal mode and a solid background with increased contrast. The screenshots are consistent with the accessibility path, but the reporter's settings, installed versions and native Flutter behavior remain unconfirmed; this does not establish their device's cause or a functional fix.

This change only clarifies the contrast description in Chinese, English and Spanish, including generated localizations, while preserving the existing policy. Evidence: `/tmp/songloft-523-followup/`, with final validation objects, commands and results in `validation.json`. Copy changes add no regression tests; old-implementation failure checks are inapplicable. Not run: native devices, outdoor use and the reporter's library. No commit, push, release or Issue reply.

Validation: Linux amd64 / Flutter 3.44.8 / Dart 3.12.2; formatting, analysis, 19 relevant tests, embedded Web and Go embedding builds pass. Docker Chrome displays both Chinese and English switch descriptions without page exceptions. The first test command named two nonexistent files; the corrected command passes all 19 tests, and the original failure is retained in the evidence directory.

## Behavior and Cause

- The original full-player background combined cover art, darkMutedColor, theme gradients, and a black top overlay, while lyrics directly used the theme primary. Light-mode backgrounds became dark without coordinated foreground colors. Translations and romaji further reduced contrast with extra opacity.
- Mobile and desktop share `PlayerBackdrop`: cover colors blend into the theme surface as a soft gradient, without full-screen image blur or a black top overlay. Windows uses the same gradient, avoiding the historical saveLayer issue.
- Missing, loading, and failed covers use the theme gradient, without metadata-generated fallback colors. Theme-pack playerGradient endpoints provide the default; available cover colors take precedence. Increased contrast uses a solid surface.
- Active lines, translations, romaji, and karaoke share a strengthened theme accent targeting 7:1 contrast; other lyrics target 4.5:1. Top text and buttons use onSurface.
- Appearance offers Blue, Teal, Forest, Rose, Amber, and Violet for light/dark/system modes. Device preference `built_in_palette` is outside server synchronization. Server theme packs take precedence; restoring the default resumes the saved device palette. Chinese, English, and Spanish localization outputs are generated.

## Validation Object

- `songloft-org/songloft-player` baseline `b80e03b47eefb1aaedf07d611b1d9d3c3d48257b` plus uncommitted worktree, source identifier `1eac2d6a16e45e318fe1325d5f29db00e965279c3429a99d1c7c0b3385bc8185`. Algorithm: SHA-256 of `git diff --binary -- lib test`, followed by sorted untracked lib/test paths, NUL, contents, NUL. Documentation excluded.
- Parent baseline `9d008b8026abb56a2ee3aae29f71017460bcc1af`; this batch only updates related bilingual documentation there. The submodule remains dirty, without a new committed gitlink.
- Linux amd64, Flutter 3.41.5, Web embedded, Docker browserless/Chrome 121. Temporary database and synthetic WAV, covers, and lyrics; no user library changes.

## Validation Results

- `dart format lib/ test/` and `flutter analyze --no-pub` passed; all 716 tests passed with `flutter test --no-pub`. The expanded two new test files passed 8 tests, covering six palettes, both brightness modes, extreme covers, gradient samples, increased contrast, translated/karaoke highlights, initialization, rapid writes, restoration, theme-pack precedence, and actual App switching.
- An isolated copy using only the baseline `lyrics_view.dart` failed both brightness-mode rendering regressions as expected: the active line at 5.17:1 and translation at 5.82:1 missed the 7:1 target. These were assertion failures, not compilation failures. The original worktree was not reverted.
- `make build-frontend-web-embedded` and `go build -tags dev -o /tmp/songloft-523/server .` passed.
- Full browser results: `/tmp/songloft-523/browser-results.json`; background pixel comparisons: `background-pixels.json`. Chinese mobile/light and English desktop/dark navigate from the home screen into the player, switch coral/ocean/missing/failed covers, and inspect lyrics, fallback backgrounds, and toolbars. Selecting Rose is checked in localStorage, then restored selection is checked after reloading the root and navigating back in-app. Screenshots: `zh-light-*.png`, `en-dark-*.png`, and `*-settings-rose.png` in the same directory.
- Logs and worktree identity: `/tmp/songloft-523/{validation.json,analyze-final.log,all-tests.log,expanded-tests.log,before-lyrics.log,web-build.log,browser.log}`.
- Not run: Android/iOS/Windows/macOS/Linux native builds and outdoor device acceptance. No native bridge, audio, or backend API changes. Web checks do not replace native device acceptance.

## Screenshots and Delivery

Representative originals: [mobile light lyrics](../assets/issue-523/light.png) · [desktop dark lyrics](../assets/issue-523/dark.png) · [Rose appearance settings](../assets/issue-523/palette.png). All 59 cross-client screenshots and validation records remain in `/home/hanxi/work/songloft-review/issue-523-lynx-20261015/`.

On 2026-10-15, the user authorized commits, pushes and a reply to songloft-org/songloft#523. Commit both clients before updating parent gitlinks. Working-tree states above are validation snapshots; Git and CI for the same SHA determine final commits/development packages. No stable tag or Issue closure in this batch.
