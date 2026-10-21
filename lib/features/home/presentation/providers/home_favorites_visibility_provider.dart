import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/base_url_provider.dart';
import '../../../../core/network/servers_provider.dart';
import '../../../auth/domain/auth_state.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../domain/home_favorites_visibility.dart';

/// Keep the two card choices local, isolated by server identity and account.
final homeFavoritesScopeProvider = Provider<String?>((ref) {
  final server = ref.watch(baseUrlProvider);
  final auth = ref.watch(authStateProvider);
  final prefs = ref.watch(appPreferencesProvider).asData?.value;
  final servers =
      ref.watch(serversProvider).asData?.value ?? prefs?.getApiServers();
  final username =
      servers?.where((entry) => entry.url == server).firstOrNull?.username ??
      prefs?.getLastUsername();
  if (auth.status != AuthStatus.authenticated ||
      auth.isLoading ||
      username == null) {
    return null;
  }
  return jsonEncode([server, username]);
});

class HomeFavoritesVisibilityNotifier
    extends Notifier<HomeFavoritesVisibility> {
  Future<void> _writes = Future.value();
  final Map<String, HomeFavoritesVisibility> _choices = {};

  @override
  HomeFavoritesVisibility build() {
    final scope = ref.watch(homeFavoritesScopeProvider);
    final prefs = ref.watch(appPreferencesProvider).asData?.value;
    if (scope == null || prefs == null) return HomeFavoritesVisibility.defaults;
    if (_choices.containsKey(scope)) return _choices[scope]!;
    try {
      final raw = prefs.getHomeFavoritesVisibility(scope);
      if (raw == null) return HomeFavoritesVisibility.defaults;
      return HomeFavoritesVisibility.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (error) {
      debugPrint('[HomeFavorites] Failed to load preference: $error');
      return HomeFavoritesVisibility.defaults;
    }
  }

  Future<void> setShowFavorites(bool value) =>
      _save(state.copyWith(showFavorites: value));
  Future<void> setShowRadioFavorites(bool value) =>
      _save(state.copyWith(showRadioFavorites: value));

  Future<void> _save(HomeFavoritesVisibility next) {
    final scope = ref.read(homeFavoritesScopeProvider);
    final prefs = ref.read(appPreferencesProvider).asData?.value;
    if (scope == null || prefs == null) return Future.value();
    state = next;
    _choices[scope] = next;
    final saved = jsonEncode(next.toJson());
    _writes = _writes.then((_) async {
      try {
        await prefs.setHomeFavoritesVisibility(scope, saved);
      } catch (error) {
        debugPrint('[HomeFavorites] Failed to save preference: $error');
      }
    });
    return _writes;
  }
}

final homeFavoritesVisibilityProvider =
    NotifierProvider<HomeFavoritesVisibilityNotifier, HomeFavoritesVisibility>(
      HomeFavoritesVisibilityNotifier.new,
    );
