import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:songloft_flutter/core/network/api_client.dart';
import 'package:songloft_flutter/core/network/base_url_provider.dart';
import 'package:songloft_flutter/core/storage/secure_storage.dart';
import 'package:songloft_flutter/features/auth/data/personal_token_api.dart';
import 'package:songloft_flutter/features/settings/presentation/widgets/personal_token_manager.dart';
import 'package:songloft_flutter/l10n/app_localizations.dart';

const rawToken = 'sl_pat_test_credential';

class TokenAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  bool created = false;
  bool revoked = false;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    requests.add(options);
    if (options.method == 'DELETE') {
      revoked = true;
      return ResponseBody.fromString('', 204);
    }
    final token = {
      'id': 'device1',
      'name': 'Home Assistant',
      'prefix': 'sl_pat_test',
      'created_at': '2026-10-28T00:00:00Z',
      'last_used_at': null,
      'revoked_at': revoked ? '2026-10-28T01:00:00Z' : null,
    };
    if (options.method == 'POST') {
      created = true;
      return ResponseBody.fromString(
        jsonEncode({...token, 'token': rawToken}),
        201,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }
    return ResponseBody.fromString(
      jsonEncode({
        'tokens': created ? [token] : [],
        'total': created ? 1 : 0,
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'creates, copies with failure recovery, hides the secret and revokes',
    (tester) async {
      final adapter = TokenAdapter();
      final api = PersonalTokenApi(Dio()..httpClientAdapter = adapter);
      bool failCopy = true;
      String? clipboard;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            if (failCopy) throw PlatformException(code: 'denied');
            clipboard = (call.arguments as Map)['text'] as String;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            personalTokenApiProvider.overrideWithValue(api),
            baseUrlProvider.overrideWith(() => BaseUrlNotifier()),
          ],
          child: const MaterialApp(
            locale: Locale('en'),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: SingleChildScrollView(child: PersonalTokenManager()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Permanent access tokens'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Create token'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Home Assistant');
      await tester.pump();
      await tester.tap(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.widgetWithText(FilledButton, 'Create token'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(rawToken), findsOneWidget);
      await tester.tap(find.text('Copy token'));
      await tester.pumpAndSettle();
      expect(
        find.text('Copy failed. Select and copy the token manually.'),
        findsOneWidget,
      );
      expect(find.text(rawToken), findsOneWidget);
      failCopy = false;
      await tester.tap(find.text('Copy Authorization header'));
      await tester.pumpAndSettle();
      expect(clipboard, 'Authorization: Bearer $rawToken');
      await tester.tap(find.text('Copy token'));
      await tester.pumpAndSettle();
      expect(clipboard, rawToken);
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(find.text(rawToken), findsNothing);
      expect(find.text('Home Assistant'), findsOneWidget);
      await tester.tap(find.byTooltip('Revoke'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Revoke'));
      await tester.pumpAndSettle();
      expect(adapter.revoked, isTrue);
      expect(find.byTooltip('Revoke'), findsNothing);
    },
  );

  test(
    'full token is excluded from debug logs and API uses authenticated server paths',
    () async {
      final previous = SecureStorageService.cachedAccessToken;
      SecureStorageService.cachedAccessToken = 'session-jwt';
      addTearDown(() => SecureStorageService.cachedAccessToken = previous);
      final dio = createDio(
        secureStorage: SecureStorageService(),
        customBaseUrl: 'https://server.test/music',
      );
      final adapter = TokenAdapter();
      dio.httpClientAdapter = adapter;
      addTearDown(dio.close);
      final api = PersonalTokenApi(dio);
      final logs = <String>[];
      await runZoned(
        () async {
          expect(await api.create(' HA '), rawToken);
          expect((await api.list(20)).total, 1);
          await api.revoke('device1');
        },
        zoneSpecification: ZoneSpecification(
          print: (_, _, _, line) => logs.add(line),
        ),
      );
      expect(logs.join('\n'), isNot(contains(rawToken)));
      expect(adapter.requests.first.data, {'name': 'HA'});
      expect(adapter.requests[1].queryParameters, {'limit': 20, 'offset': 20});
      expect(
        adapter.requests.every(
          (r) =>
              r.uri.path.startsWith('/music/api/v1/auth/personal-tokens') &&
              r.headers['Authorization'] == 'Bearer session-jwt',
        ),
        isTrue,
      );
    },
  );
}
