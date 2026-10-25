# Lyric Palette Feedback · songloft-org/songloft#537

2026-10-24, related to [songloft-org/songloft#537](https://github.com/songloft-org/songloft/issues/537). [中文](../cn/issue-537.md).

## Report and Screenshot Assessment

The reporter supplied two screenshots of the same song. In “normal mode” the top of the background is dark, the active line and the refresh/tune icons are `#415F91`, inactive lines are near 50% onSurface, and a black overlay covers the top. The “MIoT” screenshot shows the plugin's own full player with a vivid purple ambient background and white lyrics.

Pixel sampling of the first screenshot finds `#415F91` (the light primary) in both the active line and icons, and RGB(91, 85, 95) in the background immediately around the active line: roughly 1.1:1 contrast. This combination belongs to the Flutter desktop full player from before #523, not to the current implementation. `git tag --contains` shows the readability change (`8a21e26`) shipping from v2.13.3 and the deeper cover wash (`a9e5dda`) from v2.13.4; v2.13.6 no longer paints a dark top overlay or uses the raw theme primary for the active line. The reporter's installed version and platform remain unconfirmed, so the screenshot cannot establish that the current version still has the dark-gray background and light-blue highlight.

## Residual Issue in the Current Version

Baseline `8fa1d6e` (v2.13.6). To stay readable during the song-change animation, `PlayerBackdropColors.fromTheme` also held the black/white cover extremes to 7:1, although the transition only requires 4.5:1 (the existing test asserts 4.5:1). That over-constraint collapsed the active line of all six built-in palettes into near-neutral gray: light blue `#1F2631`, teal `#122C2C`, forest `#1F281A`, rose `#37232A`, amber `#352515`, violet `#2D2536`; dark blue `#E8E8ED`, teal `#E4E9E9`, forest `#E7E9E1`, rose `#F2E5E7`, amber `#F1E6DD`, violet `#ECE6ED`. The accent was neutralized into gray-white, still matching the reporter's “the font is also dark gray” description.

The fix keeps the existing contrast targets:

- The static background requires 7:1; a previous background that can only be visible during the 500ms animation requires 4.5:1.
- Foreground adjustments prefer changing HSL lightness only, preserving the theme accent hue, while keeping the onSurface and black/white strengthening fallbacks.
- With the default blue theme the light active line changes from `#1F2631` to `#2D4266`, and dark from `#E8E8ED` back to primary `#AAC7FF`; the other palettes keep their own hue, and other lines keep the 4.5:1 gray hierarchy.

## Validation

Objects: `songloft-org/songloft-player` baseline `8fa1d6e` + uncommitted worktree; parent `137586f` + documentation worktree. Linux amd64 / Flutter 3.44.8 / Dart 3.12.2.

- `dart format lib/ test/` reports no diff; `flutter analyze --no-pub` is clean; the full `flutter test --no-pub` suite passes all 774 tests.
- The new hue regression fails on the old implementation in both modes (light active saturation delta 0.15595 > 0.02; dark hue delta 20.47° > 1.5°). After the fix all six palettes stay within 0.6° hue and 0.007 saturation of primary, and the dark active line equals primary exactly. Existing contrast/transition regressions still pass.
- Docker HeadlessChrome 121 at 1280×800, with an isolated database and synthetic WAV/cover/sidecar lyrics; logged in from the home page, opened the auto-created playlist, played the song, and opened the desktop full player.
- Measured light active line and icons `#2D4266` against RGB(236, 237, 246), contrast 8.7:1; dark active line and icons `#AAC7FF` against RGB(37, 39, 49), contrast 8.1:1. Inactive lines measured around `#44464A` / `#B9BBC2`.
- Acceptance screenshots: [light](../assets/issue-537/light.png) · [dark](../assets/issue-537/dark.png).
- Not run: the reporter's device and installed version, native Windows/macOS/Linux/Android/iOS packages, a release build, and remote CI for the uncommitted worktree. A browser and synthetic library do not replace hardware or the reporter's library.

## Status

Not committed, pushed, or replied to. Commits, publication, and Issue comments follow separate user authorization; the local change does not alter the version assessment of the report screenshots.
