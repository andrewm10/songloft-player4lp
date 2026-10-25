import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme.dart';
import '../../../../core/utils/color_extraction.dart';

/// Opaque, surface-based cover tints keep the whole player readable in both modes.
/// No image blur/saveLayer is needed (including on Windows).
class PlayerBackdrop extends StatelessWidget {
  final CoverPalette? palette;
  final Widget child;

  const PlayerBackdrop({super.key, this.palette, required this.child});

  static PlayerBackdropColors colorsOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_BackdropScope>()?.colors ??
      PlayerBackdropColors.fromTheme(Theme.of(context));

  @override
  Widget build(BuildContext context) {
    final colors = PlayerBackdropColors.fromTheme(Theme.of(context), palette);
    return _BackdropScope(
      colors: colors,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 500),
        curve: Curves.easeInOut,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: colors.background,
          ),
        ),
        child: child,
      ),
    );
  }
}

class PlayerBackdropColors {
  final List<Color> background;
  final Color active;
  final Color secondary;

  const PlayerBackdropColors({
    required this.background,
    required this.active,
    required this.secondary,
  });

  factory PlayerBackdropColors.fromTheme(
    ThemeData theme, [
    CoverPalette? palette,
  ]) {
    final scheme = theme.colorScheme;
    final extension = theme.extension<SongloftThemeExtension>();
    final highContrast = extension?.increaseContrast ?? false;
    final gradient = extension?.playerGradientColors;
    // Missing/loading/failed covers all use the theme, never metadata hash colors.
    final top =
        palette?.vibrantColor ??
        palette?.dominantColor ??
        gradient?.firstOrNull ??
        scheme.primary;
    final bottom =
        palette?.mutedColor ??
        palette?.vibrantColor ??
        palette?.dominantColor ??
        gradient?.lastOrNull ??
        scheme.tertiary;
    final dark = theme.brightness == Brightness.dark;
    Color tint(Color color, double opacity) =>
        Color.alphaBlend(color.withValues(alpha: opacity), scheme.surface);
    final coverOpacity = dark ? .24 : .28;
    final background =
        highContrast
            ? [scheme.surface, scheme.surface]
            : [
              tint(top, palette == null ? (dark ? .18 : .12) : coverOpacity),
              tint(bottom, palette == null ? (dark ? .08 : .04) : .18),
            ];
    // Text updates immediately while AnimatedContainer can still show any prior
    // cover. The current gradient needs the full 7:1, but the 500ms transition
    // only needs to stay legible, so a both-extremes 7:1 bound would needlessly
    // neutralize every accent hue.
    final transition =
        highContrast
            ? background
            : [
              tint(Colors.black, coverOpacity),
              tint(Colors.white, coverOpacity),
            ];
    return PlayerBackdropColors(
      background: background,
      active: _readable(
        scheme.primary,
        scheme.onSurface,
        background,
        7,
        transition: transition,
        transitionMinimum: 4.5,
      ),
      secondary: _readable(
        Color.lerp(background.first, scheme.onSurface, .68)!,
        scheme.onSurface,
        background,
        highContrast ? 7 : 4.5,
        transition: transition,
        transitionMinimum: 4.5,
      ),
    );
  }

  static double _contrast(Color a, Color b) {
    final values = [a.computeLuminance(), b.computeLuminance()]..sort();
    return (values.last + .05) / (values.first + .05);
  }

  static Color _readable(
    Color preferred,
    Color foreground,
    List<Color> backgrounds,
    double minimum, {
    List<Color> transition = const [],
    double transitionMinimum = 0,
  }) {
    bool acceptable(Color candidate) =>
        backgrounds.every((bg) => _contrast(candidate, bg) >= minimum) &&
        transition.every((bg) => _contrast(candidate, bg) >= transitionMinimum);

    if (acceptable(preferred)) return preferred;
    // Keep the accent hue: move only lightness toward black/white instead of
    // mixing into the neutral onSurface, so the active lyric stays colored.
    final base = HSLColor.fromColor(preferred);
    final darken = foreground.computeLuminance() < .5;
    for (var step = 1; step <= 20; step++) {
      final lightness =
          darken
              ? base.lightness * (1 - step / 20)
              : base.lightness + (1 - base.lightness) * step / 20;
      final candidate = base.withLightness(lightness.clamp(0.0, 1.0)).toColor();
      if (acceptable(candidate)) return candidate;
    }
    for (var step = 1; step <= 20; step++) {
      final candidate = Color.lerp(preferred, foreground, step / 20)!;
      if (acceptable(candidate)) return candidate;
    }
    // A stronger cover wash can exceed onSurface's contrast headroom.
    final extreme = darken ? Colors.black : Colors.white;
    for (var step = 1; step <= 20; step++) {
      final candidate = Color.lerp(foreground, extreme, step / 20)!;
      if (acceptable(candidate)) return candidate;
    }
    return extreme;
  }
}

class _BackdropScope extends InheritedWidget {
  final PlayerBackdropColors colors;

  const _BackdropScope({required this.colors, required super.child});

  @override
  bool updateShouldNotify(_BackdropScope oldWidget) =>
      colors != oldWidget.colors;
}
