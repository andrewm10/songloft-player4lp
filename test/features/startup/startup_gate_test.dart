import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:songloft_flutter/features/startup/presentation/startup_gate.dart';

void main() {
  testWidgets('全新安装且无服务器时进入登录流程', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(
      const ProviderScope(
        child: StartupGate(
          child: MaterialApp(home: Scaffold(body: Text('ready'))),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('ready'), findsOneWidget);
  });
}
