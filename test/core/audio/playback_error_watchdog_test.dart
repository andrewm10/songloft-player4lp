import 'package:flutter_test/flutter_test.dart';
import 'package:songloft_flutter/core/audio/playback_error_watchdog.dart';

void main() {
  const window = Duration(milliseconds: 60);
  const threshold = Duration(milliseconds: 20);
  const settle = Duration(milliseconds: 150);

  late Duration position;
  late bool playing;
  late int fatalCount;
  late PlaybackErrorWatchdog watchdog;

  setUp(() {
    position = Duration.zero;
    playing = true;
    fatalCount = 0;
    watchdog = PlaybackErrorWatchdog(
      position: () => position,
      isPlaying: () => playing,
      onFatal: () => fatalCount++,
      window: window,
      progressThreshold: threshold,
    );
  });

  tearDown(() => watchdog.cancel());

  test('播放仍在推进时不判定致命（songloft-org/songloft#529）', () async {
    position = const Duration(seconds: 25);
    watchdog.arm();
    position = const Duration(seconds: 26);
    await Future<void>.delayed(settle);
    expect(fatalCount, 0);
  });

  test('播放停住时判定致命（songloft-org/songloft#357）', () async {
    position = const Duration(seconds: 25);
    watchdog.arm();
    await Future<void>.delayed(settle);
    expect(fatalCount, 1);
  });

  test('推进量低于阈值仍判定致命', () async {
    position = const Duration(seconds: 25);
    watchdog.arm();
    position = const Duration(seconds: 25, milliseconds: 5);
    await Future<void>.delayed(settle);
    expect(fatalCount, 1);
  });

  test('暂停中不自动切歌', () async {
    watchdog.arm();
    playing = false;
    await Future<void>.delayed(settle);
    expect(fatalCount, 0);
  });

  test('cancel 后不再判定', () async {
    watchdog.arm();
    watchdog.cancel();
    expect(watchdog.isArmed, isFalse);
    await Future<void>.delayed(settle);
    expect(fatalCount, 0);
  });

  test('连续错误不重置窗口，只判定一次', () async {
    position = const Duration(seconds: 25);
    watchdog.arm();
    for (var i = 0; i < 4; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
      watchdog.arm();
    }
    await Future<void>.delayed(settle);
    expect(fatalCount, 1);
  });

  test('判定后可重新 arm', () async {
    watchdog.arm();
    await Future<void>.delayed(settle);
    expect(fatalCount, 1);
    watchdog.arm();
    expect(watchdog.isArmed, isTrue);
    await Future<void>.delayed(settle);
    expect(fatalCount, 2);
  });
}
