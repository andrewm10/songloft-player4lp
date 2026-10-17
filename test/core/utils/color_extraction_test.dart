import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:songloft_flutter/config/app_config.dart';
import 'package:songloft_flutter/core/network/base_url_provider.dart';
import 'package:songloft_flutter/core/utils/color_extraction.dart';

class _RealHttp extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('real cover decoding preserves warm, black and white artwork', () async {
    await HttpOverrides.runZoned(() async {
      for (final color in [
        const Color(0xffb87450),
        Colors.black,
        Colors.white,
      ]) {
        final recorder = ui.PictureRecorder();
        Canvas(recorder).drawColor(color, BlendMode.src);
        final picture = recorder.endRecording();
        final image = await picture.toImage(24, 24);
        final bytes =
            (await image.toByteData(
              format: ui.ImageByteFormat.png,
            ))!.buffer.asUint8List();
        image.dispose();
        picture.dispose();
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          request.response.headers.contentType = ContentType('image', 'png');
          request.response.add(bytes);
          await request.response.close();
        });
        final container = ProviderContainer();
        try {
          final palette = await container.read(
            coverColorsProvider('http://127.0.0.1:${server.port}/cover').future,
          );
          expect(palette, isNotNull);
          // Quantization may shift a channel slightly, but must retain the cover.
          expect((palette!.dominantColor.r - color.r).abs(), lessThan(.06));
          expect((palette.dominantColor.g - color.g).abs(), lessThan(.06));
          expect((palette.dominantColor.b - color.b).abs(), lessThan(.06));
        } finally {
          container.dispose();
          await server.close(force: true);
        }
      }
    }, createHttpClient: _RealHttp().createHttpClient);
  });

  test('same relative cover path reloads when the server changes', () async {
    await HttpOverrides.runZoned(() async {
      final originalBase = AppConfig.baseUrl;
      final originalResolved = AppConfig.resolvedBaseUrl;
      final originalPath = AppConfig.basePath;
      final servers = <HttpServer>[];
      final container = ProviderContainer();
      try {
        AppConfig.basePath = '';
        for (final color in [Colors.blue, Colors.green]) {
          final recorder = ui.PictureRecorder();
          Canvas(recorder).drawColor(color, BlendMode.src);
          final picture = recorder.endRecording();
          final image = await picture.toImage(24, 24);
          final bytes =
              (await image.toByteData(
                format: ui.ImageByteFormat.png,
              ))!.buffer.asUint8List();
          image.dispose();
          picture.dispose();
          final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
          servers.add(server);
          server.listen((request) async {
            request.response.headers.contentType = ContentType('image', 'png');
            request.response.add(bytes);
            await request.response.close();
          });
          container
              .read(baseUrlProvider.notifier)
              .set('http://127.0.0.1:${server.port}');
          final palette = await container.read(
            coverColorsProvider('/api/v1/songs/523/cover').future,
          );
          expect(palette, isNotNull);
          expect((palette!.dominantColor.b - color.b).abs(), lessThan(.06));
          expect((palette.dominantColor.g - color.g).abs(), lessThan(.06));
        }
      } finally {
        container.dispose();
        for (final server in servers) {
          await server.close(force: true);
        }
        AppConfig.baseUrl = originalBase;
        AppConfig.resolvedBaseUrl = originalResolved;
        AppConfig.basePath = originalPath;
      }
    }, createHttpClient: _RealHttp().createHttpClient);
  });
}
