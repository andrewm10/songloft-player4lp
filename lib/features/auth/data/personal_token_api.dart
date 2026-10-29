import 'package:dio/dio.dart';

import '../../../config/app_config.dart';
import '../../../core/network/api_exceptions.dart';

class PersonalToken {
  final String id;
  final String name;
  final String prefix;
  final DateTime createdAt;
  final DateTime? lastUsedAt;
  final DateTime? revokedAt;

  PersonalToken.fromJson(Map<String, dynamic> json)
    : id = json['id'] as String,
      name = json['name'] as String,
      prefix = json['prefix'] as String,
      createdAt = DateTime.parse(json['created_at'] as String),
      lastUsedAt = DateTime.tryParse(json['last_used_at'] as String? ?? ''),
      revokedAt = DateTime.tryParse(json['revoked_at'] as String? ?? '');
}

class PersonalTokenPage {
  final List<PersonalToken> tokens;
  final int total;

  PersonalTokenPage.fromJson(Map<String, dynamic> json)
    : tokens =
          (json['tokens'] as List)
              .map((e) => PersonalToken.fromJson(e as Map<String, dynamic>))
              .toList(),
      total = json['total'] as int;
}

class PersonalTokenApi {
  final Dio dio;
  PersonalTokenApi(this.dio);
  static String get path => '${AppConfig.apiPrefix}/auth/personal-tokens';

  Future<PersonalTokenPage> list(int offset) async {
    try {
      final response = await dio.get(
        path,
        queryParameters: {'limit': 20, 'offset': offset},
      );
      return PersonalTokenPage.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<String> create(String name) async {
    try {
      final response = await dio.post(path, data: {'name': name.trim()});
      return (response.data as Map<String, dynamic>)['token'] as String;
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> revoke(String id) async {
    try {
      await dio.delete('$path/${Uri.encodeComponent(id)}');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
