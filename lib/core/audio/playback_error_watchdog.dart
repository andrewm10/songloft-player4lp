import 'dart:async';

/// 播放过程中收到解码/流错误时，判定它是否**真的**致命。
///
/// libmpv 的 `ad`（音频解码器）模块会把可恢复的错误也按 `level=error` 上报——
/// 例如 MP3 文件中间夹了一段损坏数据，解码器丢掉该帧、重新同步后仍能继续播到
/// 结尾。media_kit 把这类日志原样转成 `player.stream.error`，若直接当作曲目
/// 结束，整首歌会在损坏点被跳过（songloft-org/songloft#529）。
///
/// 反之 songloft-org/songloft#357 的场景（FLAC 文件尾部解码错误、网络流中途
/// 断开）确实不可恢复，必须判定结束触发自动切歌，否则播放器原地卡死。
///
/// 两者在错误事件本身上无法区分，只能看错误之后播放是否还在推进：收到错误时
/// 记下当前位置并等待 [window]，窗口结束时若位置推进不足 [progressThreshold]
/// 才判定致命。
class PlaybackErrorWatchdog {
  PlaybackErrorWatchdog({
    required Duration Function() position,
    required bool Function() isPlaying,
    required void Function() onFatal,
    this.window = const Duration(seconds: 3),
    this.progressThreshold = const Duration(milliseconds: 500),
  }) : _position = position,
       _isPlaying = isPlaying,
       _onFatal = onFatal;

  final Duration Function() _position;
  final bool Function() _isPlaying;
  final void Function() _onFatal;

  /// 观察窗口长度。
  final Duration window;

  /// 窗口内认定"播放仍在推进"所需的最小位置增量。
  final Duration progressThreshold;

  Timer? _timer;
  Duration _baseline = Duration.zero;

  bool get isArmed => _timer != null;

  /// 收到播放中错误时调用。重复错误（损坏区域常连续刷屏）不重置窗口，
  /// 否则错误不断则窗口永远不到期。
  void arm() {
    if (_timer != null) return;
    _baseline = _position();
    _timer = Timer(window, _evaluate);
  }

  /// 加载新媒体、seek、正常结束、释放时调用：这些情况下位置基线失效。
  void cancel() {
    _timer?.cancel();
    _timer = null;
  }

  void _evaluate() {
    _timer = null;
    // 暂停中无从判断是否推进，且此时自动切歌本身就是错的。
    if (!_isPlaying()) return;
    if (_position() - _baseline >= progressThreshold) return;
    _onFatal();
  }
}
