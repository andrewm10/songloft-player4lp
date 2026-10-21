import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:songloft_flutter/core/network/base_url_provider.dart';
import 'package:songloft_flutter/core/storage/app_preferences.dart';
import 'package:songloft_flutter/features/auth/domain/auth_state.dart';
import 'package:songloft_flutter/features/auth/presentation/providers/auth_provider.dart';
import 'package:songloft_flutter/features/home/domain/home_favorites_visibility.dart';
import 'package:songloft_flutter/features/home/presentation/providers/home_favorites_visibility_provider.dart';
import 'package:songloft_flutter/features/playlist/domain/playlist.dart';

Playlist playlist(int id, {bool builtIn = false, String name = '收藏'}) =>
    Playlist(
      id: id,
      name: name,
      type: id == 2 ? 'radio' : 'normal',
      labels: builtIn ? ['built_in'] : [],
      createdAt: DateTime.utc(2026),
      updatedAt: DateTime.utc(2026),
    );

void main() {
  test('defaults and malformed values show both cards', () {
    final defaults = HomeFavoritesVisibility.fromJson({});
    expect(defaults.toJson(), {
      'showFavorites': true,
      'showRadioFavorites': true,
    });
    expect(
      HomeFavoritesVisibility.fromJson({
        'showFavorites': 'false',
      }).showFavorites,
      isTrue,
    );
  });

  test(
    'only built-in identity is filtered; names and playlist contents are untouched',
    () {
      const config = HomeFavoritesVisibility(
        showFavorites: false,
        showRadioFavorites: false,
      );
      final favorite = playlist(1, builtIn: true, name: 'Renamed favorites');
      expect(config.includes(favorite), isFalse);
      expect(config.includes(playlist(2, builtIn: true)), isFalse);
      expect(config.includes(playlist(3)), isTrue);
      expect(config.includes(playlist(1)), isTrue);
      expect(favorite.labels, ['built_in']);
      expect(config.copyWith(showFavorites: true).includes(favorite), isTrue);
    },
  );

  test(
    'local choices persist, restore and stay isolated across accounts and servers',
    () async {
      SharedPreferences.setMockInitialValues({'last_username': 'alice'});
      final prefs = await AppPreferences.create();
      final container = ProviderContainer(
        overrides: [
          appPreferencesProvider.overrideWith((ref) async => prefs),
          authStateProvider.overrideWith(_Authenticated.new),
        ],
      );
      addTearDown(container.dispose);
      await container.read(appPreferencesProvider.future);
      final server = container.read(baseUrlProvider);
      final alice = jsonEncode([server, 'alice']);
      expect(container.read(homeFavoritesScopeProvider), alice);
      final notifier = container.read(homeFavoritesVisibilityProvider.notifier);
      final first = notifier.setShowFavorites(false);
      final second = notifier.setShowRadioFavorites(false);
      final third = notifier.setShowFavorites(true);
      await Future.wait([first, second, third]);
      expect(jsonDecode(prefs.getHomeFavoritesVisibility(alice)!), {
        'showFavorites': true,
        'showRadioFavorites': false,
      });
      container.invalidate(homeFavoritesVisibilityProvider);
      expect(
        container.read(homeFavoritesVisibilityProvider).showRadioFavorites,
        isFalse,
      );

      await prefs.setLastUsername('bob');
      container.read(authStateProvider.notifier).checkAuth();
      // The fake checkAuth emits a new authenticated state, as login does.
      expect(
        container.read(homeFavoritesScopeProvider),
        jsonEncode([server, 'bob']),
      );
      expect(
        container.read(homeFavoritesVisibilityProvider).showRadioFavorites,
        isTrue,
      );
      await container
          .read(homeFavoritesVisibilityProvider.notifier)
          .setShowFavorites(false);

      await prefs.setLastUsername('alice');
      container.read(baseUrlProvider.notifier).set('https://other.example');
      expect(
        container.read(homeFavoritesVisibilityProvider).showRadioFavorites,
        isTrue,
      );
      container.read(baseUrlProvider.notifier).set(server);
      expect(
        container.read(homeFavoritesVisibilityProvider).showRadioFavorites,
        isFalse,
      );
      expect(
        container.read(homeFavoritesVisibilityProvider).showFavorites,
        isTrue,
      );
      expect(prefs.getHomeShowRadios(), isTrue);
      expect(prefs.getPlaylistViewMode(), 'grid');
      container.read(authStateProvider.notifier).setUnauthenticated();
      expect(container.read(homeFavoritesScopeProvider), isNull);
      expect(
        container.read(homeFavoritesVisibilityProvider).toJson(),
        HomeFavoritesVisibility.defaults.toJson(),
      );
      expect(
        prefs.getHomeFavoritesVisibility(jsonEncode([server, 'bob'])),
        isNotNull,
      );
    },
  );
}

class _Authenticated extends AuthNotifier {
  @override
  AuthState build() => const AuthState(status: AuthStatus.authenticated);
  @override
  Future<void> checkAuth() async {
    state = state.authenticated();
  }
}
