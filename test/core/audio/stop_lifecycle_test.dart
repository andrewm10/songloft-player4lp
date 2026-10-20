import 'dart:async';
import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:just_audio/just_audio.dart' as ja;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:songloft_flutter/core/audio/audio_service.dart';
import 'package:songloft_flutter/shared/models/song.dart';

class _Player implements ja.AudioPlayer {
  final events = StreamController<ja.PlaybackEvent>.broadcast(sync: true);
  final states = StreamController<ja.ProcessingState>.broadcast(sync: true);
  final playingEvents = StreamController<bool>.broadcast(sync: true);
  final errors = StreamController<ja.PlayerException>.broadcast(sync: true);
  @override
  Stream<ja.PlayerException> get errorStream => errors.stream;
  @override
  bool playing = true;
  @override
  ja.ProcessingState processingState = ja.ProcessingState.ready;
  int playCalls = 0;
  final sourceStarted = Completer<void>();
  final sourceLoaded = Completer<Duration?>();
  @override
  Future<Duration?> setAudioSource(
    ja.AudioSource source, {
    bool preload = true,
    int? initialIndex,
    Duration? initialPosition,
  }) {
    sourceStarted.complete();
    return sourceLoaded.future;
  }

  bool failStop = false;
  @override
  Stream<ja.PlaybackEvent> get playbackEventStream => events.stream;
  @override
  Stream<ja.ProcessingState> get processingStateStream => states.stream;
  @override
  Stream<bool> get playingStream => playingEvents.stream;
  @override
  Duration get position => Duration.zero;
  @override
  Duration get bufferedPosition => Duration.zero;
  @override
  double get speed => 1;
  @override
  Future<void> stop() async {
    if (failStop) throw StateError('stop failed');
    playing = false;
  }

  @override
  Future<void> play() async {
    playCalls++;
    playing = true;
    playingEvents.add(true);
  }

  @override
  Future<void> pause() async => playing = false;

  void emit(ja.ProcessingState state) {
    processingState = state;
    states.add(state);
    events.add(ja.PlaybackEvent(processingState: state));
  }

  @override
  Future<void> dispose() async {
    await events.close();
    await states.close();
    await playingEvents.close();
    await errors.close();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory temporaryDirectory;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    temporaryDirectory = await Directory.systemTemp.createTemp(
      'songloft-stop-',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.flutter.io/path_provider'),
          (_) async => temporaryDirectory.path,
        );
  });
  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.flutter.io/path_provider'),
          null,
        );
    await temporaryDirectory.delete(recursive: true);
  });
  for (final failStop in [false, true]) {
    test(
      'late events cannot resurrect stopped session (failure=$failStop)',
      () async {
        final player = _Player()..failStop = failStop;
        final handler = SongloftAudioHandler(player: player);
        addTearDown(handler.dispose);
        var completed = 0;
        handler.onSongCompleted = () => completed++;
        if (failStop) {
          await expectLater(handler.stop(), throwsStateError);
        } else {
          await handler.stop();
        }
        for (final state in [
          ja.ProcessingState.ready,
          ja.ProcessingState.completed,
        ]) {
          player.emit(state);
          player.playingEvents.add(true);
          expect(
            handler.playbackState.value.processingState,
            AudioProcessingState.idle,
          );
          expect(handler.playbackState.value.playing, false);
        }
        expect(completed, 0);
        await handler.play();
        player.emit(ja.ProcessingState.ready);
        expect(player.playCalls, 1);
        expect(handler.playbackState.value.playing, true);
        expect(
          handler.playbackState.value.processingState,
          AudioProcessingState.ready,
        );
      },
    );
  }

  test(
    'live reconnect keeps buffering media session and pause clears intent',
    () async {
      final player = _Player()..playing = false;
      final handler = SongloftAudioHandler(player: player);
      addTearDown(handler.dispose);
      player.emit(ja.ProcessingState.idle);
      handler.setLiveReconnecting(true);
      expect(
        handler.playbackState.value.processingState,
        AudioProcessingState.buffering,
      );
      expect(handler.playbackState.value.playing, isTrue);
      expect(
        handler.playbackState.value.controls,
        contains(MediaControl.pause),
      );
      player.emit(ja.ProcessingState.idle);
      expect(
        handler.playbackState.value.processingState,
        AudioProcessingState.buffering,
      );
      await handler.pause();
      expect(handler.playbackState.value.playing, isFalse);
      expect(
        handler.playbackState.value.processingState,
        AudioProcessingState.idle,
      );
      handler.setLiveReconnecting(true);
      await handler.stop();
      expect(handler.playbackState.value.playing, isFalse);
      expect(
        handler.playbackState.value.processingState,
        AudioProcessingState.idle,
      );
    },
  );

  test(
    'stop invalidates song load waiting for session initialization',
    () async {
      final player = _Player();
      final handler = SongloftAudioHandler(player: player);
      addTearDown(handler.dispose);
      final song = Song(
        id: 1,
        type: 'local',
        title: 'test',
        duration: 10,
        url: '/api/v1/songs/1/play',
        addedAt: DateTime(2026),
        updatedAt: DateTime(2026),
      );
      final loading = handler.playSong(song);
      await handler.stop();
      await loading;
      expect(player.playCalls, 0);
      expect(
        handler.playbackState.value.processingState,
        AudioProcessingState.idle,
      );
    },
  );
  test('a pending source finishing after stop cannot auto-play', () async {
    final player = _Player();
    final handler = SongloftAudioHandler(player: player);
    addTearDown(handler.dispose);
    await handler.ensureInitialized();
    final song = Song(
      id: 99,
      type: 'local',
      title: 'pending',
      duration: 10,
      url: '/api/v1/songs/99/play',
      addedAt: DateTime(2026),
      updatedAt: DateTime(2026),
    );
    final loading = handler.playSong(song);
    await player.sourceStarted.future.timeout(const Duration(seconds: 5));
    await handler.stop();
    player.emit(ja.ProcessingState.ready);
    player.sourceLoaded.complete(const Duration(seconds: 10));
    await loading;
    expect(player.playCalls, 0);
    expect(
      handler.playbackState.value.processingState,
      AudioProcessingState.idle,
    );
  });

  test(
    'pause invalidates a pending load and cancels reconnect intent',
    () async {
      final player = _Player();
      final handler = SongloftAudioHandler(player: player);
      addTearDown(handler.dispose);
      await handler.ensureInitialized();
      var cancellations = 0;
      handler.onPlaybackCancelled = () => cancellations++;
      final loading = handler.playSong(
        Song.fromJson({
          'id': 1,
          'title': 'radio',
          'type': 'radio',
          'is_live': true,
          'url': 'https://radio.example.test/live',
        }),
      );
      await player.sourceStarted.future.timeout(const Duration(seconds: 5));
      await handler.pause();
      player.sourceLoaded.complete(null);
      await loading;
      expect(cancellations, 1);
      expect(player.playCalls, 0);
    },
  );

  test(
    'stream errors reconnect only before stop and play delegates resume',
    () async {
      final player = _Player();
      final handler = SongloftAudioHandler(player: player);
      addTearDown(handler.dispose);
      var errors = 0;
      handler.onPlaybackError = () => errors++;
      player.errors.add(ja.PlayerException(1, 'network lost', null));
      expect(errors, 1);
      await handler.stop();
      player.errors.add(ja.PlayerException(1, 'late error', null));
      expect(errors, 1);
      handler.onPlayRequested = () async => true;
      await handler.play();
      expect(player.playCalls, 0);
    },
  );
}
