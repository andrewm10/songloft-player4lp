import '../../../config/constants.dart';
import '../../playlist/domain/playlist.dart';

/// Homepage card preferences; independent of the playlist's hidden label.
class HomeFavoritesVisibility {
  final bool showFavorites;
  final bool showRadioFavorites;

  const HomeFavoritesVisibility({
    this.showFavorites = true,
    this.showRadioFavorites = true,
  });

  static const defaults = HomeFavoritesVisibility();

  factory HomeFavoritesVisibility.fromJson(Map<String, dynamic> json) =>
      HomeFavoritesVisibility(
        showFavorites:
            json['showFavorites'] is bool
                ? json['showFavorites'] as bool
                : true,
        showRadioFavorites:
            json['showRadioFavorites'] is bool
                ? json['showRadioFavorites'] as bool
                : true,
      );

  Map<String, dynamic> toJson() => {
    'showFavorites': showFavorites,
    'showRadioFavorites': showRadioFavorites,
  };

  HomeFavoritesVisibility copyWith({
    bool? showFavorites,
    bool? showRadioFavorites,
  }) => HomeFavoritesVisibility(
    showFavorites: showFavorites ?? this.showFavorites,
    showRadioFavorites: showRadioFavorites ?? this.showRadioFavorites,
  );

  bool includes(Playlist playlist) {
    if (!playlist.isBuiltIn) return true;
    if ('${playlist.id}' == AppConstants.favoritePlaylistId) {
      return showFavorites;
    }
    if ('${playlist.id}' == AppConstants.radioFavoritePlaylistId) {
      return showRadioFavorites;
    }
    return true;
  }
}
