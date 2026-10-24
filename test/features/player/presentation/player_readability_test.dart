import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:songloft_flutter/core/theme/app_theme.dart';
import 'package:songloft_flutter/core/theme/built_in_palette.dart';
import 'package:songloft_flutter/core/utils/color_extraction.dart';
import 'package:songloft_flutter/features/player/domain/lyric_parser.dart';
import 'package:songloft_flutter/features/player/domain/player_state.dart';
import 'package:songloft_flutter/features/player/presentation/providers/lyric_provider.dart';
import 'package:songloft_flutter/features/player/presentation/providers/player_provider.dart';
import 'package:songloft_flutter/features/player/presentation/widgets/karaoke_line.dart';
import 'package:songloft_flutter/features/player/presentation/widgets/lyrics_view.dart';
import 'package:songloft_flutter/features/player/presentation/widgets/player_backdrop.dart';
import 'package:songloft_flutter/l10n/app_localizations.dart';

double contrast(Color text, Color background) {
  final values = [
    Color.alphaBlend(text, background).computeLuminance(),
    background.computeLuminance(),
  ]..sort();
  return (values.last + .05) / (values.first + .05);
}

class _Player extends PlayerNotifier {
  @override
  PlayerState build() => const PlayerState();
}

class _Lyrics extends LyricNotifier {
  @override
  LyricState build() => const LyricState(
    currentIndex: 0,
    lyrics: [
      LyricLine(
        time: Duration.zero,
        text: 'Active lyric',
        translation: 'Translation',
        romaji: 'Romaji',
      ),
      LyricLine(time: Duration(seconds: 5), text: 'Next lyric'),
    ],
  );

  void karaoke() =>
      state = const LyricState(
        currentIndex: 0,
        lyrics: [
          LyricLine(
            time: Duration.zero,
            text: 'Karaoke',
            words: [
              LyricWord(
                text: 'Karaoke',
                start: Duration.zero,
                end: Duration(seconds: 2),
              ),
            ],
          ),
        ],
      );
}

void main() {
  for (final dark in [false, true]) {
    final theme = dark ? AppTheme.darkTheme() : AppTheme.lightTheme();
    final defaults = PlayerBackdropColors.fromTheme(theme);
    test('cover hue remains visible across the whole backdrop ($dark)', () {
      for (final mostlyBlack in [false, true]) {
        final red = PlayerBackdropColors.fromTheme(
          theme,
          CoverPalette(
            dominantColor: mostlyBlack ? Colors.black : Colors.red,
            vibrantColor: mostlyBlack ? Colors.red : null,
            onImageColor: Colors.white,
          ),
        );
        final blue = PlayerBackdropColors.fromTheme(
          theme,
          CoverPalette(
            dominantColor: mostlyBlack ? Colors.black : Colors.blue,
            vibrantColor: mostlyBlack ? Colors.blue : null,
            onImageColor: Colors.white,
          ),
        );
        for (var i = 0; i < red.background.length; i++) {
          final a = red.background[i];
          final b = blue.background[i];
          // At least 32 sRGB levels of hue separation, including the lower controls.
          expect((a.r - b.r).abs() * 255, greaterThanOrEqualTo(32));
          expect((a.b - b.b).abs() * 255, greaterThanOrEqualTo(32));
        }
      }
    });
    test('cover extremes and transitions preserve readable text ($dark)', () {
      PlayerBackdropColors? previous;
      for (final color in [
        Colors.black,
        Colors.white,
        Colors.red,
        Colors.green,
        Colors.blue,
        Colors.yellow,
        Colors.purple,
      ]) {
        final colors = PlayerBackdropColors.fromTheme(
          theme,
          CoverPalette(dominantColor: color, onImageColor: Colors.white),
        );
        for (var i = 0; i <= 20; i++) {
          final background =
              Color.lerp(
                colors.background.first,
                colors.background.last,
                i / 20,
              )!;
          expect(contrast(colors.active, background), greaterThanOrEqualTo(7));
          expect(
            contrast(colors.secondary, background),
            greaterThanOrEqualTo(4.5),
          );
          if (previous != null) {
            // New text may already be visible while the old background animates out.
            final transition =
                Color.lerp(
                  previous.background.first,
                  colors.background.first,
                  i / 20,
                )!;
            expect(
              contrast(colors.active, transition),
              greaterThanOrEqualTo(4.5),
            );
          }
        }
        previous = colors;
      }
      expect(
        defaults.background.first.computeLuminance(),
        dark ? lessThan(.1) : greaterThan(.7),
      );
    });

    test('active lyric keeps the accent hue ($dark)', () {
      for (final palette in BuiltInPalette.values) {
        final paletteTheme =
            dark
                ? AppTheme.darkTheme(seedColor: palette.seedColor)
                : AppTheme.lightTheme(seedColor: palette.seedColor);
        final primary = HSLColor.fromColor(paletteTheme.colorScheme.primary);
        final active = HSLColor.fromColor(
          PlayerBackdropColors.fromTheme(paletteTheme).active,
        );
        var hueDelta = (primary.hue - active.hue).abs();
        if (hueDelta > 180) hueDelta = 360 - hueDelta;
        // Only HSL lightness may change; dark mode keeps the primary outright.
        expect(hueDelta, lessThan(1.5), reason: palette.name);
        expect(
          (active.saturation - primary.saturation).abs(),
          lessThan(.02),
          reason: palette.name,
        );
      }
    });

    testWidgets(
      'line, translated and karaoke highlights use backdrop contrast ($dark)',
      (tester) async {
        final container = ProviderContainer(
          overrides: [
            playerStateProvider.overrideWith(_Player.new),
            lyricStateProvider.overrideWith(_Lyrics.new),
          ],
        );
        addTearDown(container.dispose);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: MaterialApp(
              theme: theme,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: const Scaffold(
                body: PlayerBackdrop(
                  child: LyricsView(currentPosition: Duration.zero),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        for (final text in ['Active lyric', 'Translation', 'Romaji']) {
          final color = tester.widget<Text>(find.text(text)).style!.color!;
          for (final bg in defaults.background) {
            expect(contrast(color, bg), greaterThanOrEqualTo(7), reason: text);
          }
        }
        final secondary =
            tester.widget<Text>(find.text('Next lyric')).style!.color!;
        expect(
          contrast(secondary, defaults.background.first),
          greaterThanOrEqualTo(4.5),
        );
        (container.read(lyricStateProvider.notifier) as _Lyrics).karaoke();
        await tester.pump();
        expect(
          tester.widget<KaraokeLine>(find.byType(KaraokeLine)).activeColor,
          defaults.active,
        );
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
}
