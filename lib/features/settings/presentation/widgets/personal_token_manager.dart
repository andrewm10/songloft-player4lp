import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_client.dart';
import '../../../../core/network/base_url_provider.dart';
import '../../../../l10n/app_localizations.dart';
import '../../../auth/data/personal_token_api.dart';

final personalTokenApiProvider = Provider<PersonalTokenApi>(
  (ref) => PersonalTokenApi(ref.watch(dioProvider)),
);
final personalTokenListProvider = FutureProvider.autoDispose
    .family<PersonalTokenPage, int>((ref, offset) {
      ref.watch(baseUrlProvider);
      return ref.watch(personalTokenApiProvider).list(offset);
    });

class PersonalTokenManager extends ConsumerStatefulWidget {
  const PersonalTokenManager({super.key});
  @override
  ConsumerState<PersonalTokenManager> createState() =>
      _PersonalTokenManagerState();
}

class _PersonalTokenManagerState extends ConsumerState<PersonalTokenManager> {
  int offset = 0;
  String? revoking;
  String? error;

  Future<void> create() async {
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const _CreateTokenDialog(),
    );
    if (mounted) ref.invalidate(personalTokenListProvider);
  }

  Future<void> revoke(PersonalToken token) async {
    final l10n = AppLocalizations.of(context);
    final api = ref.read(personalTokenApiProvider);
    final server = ref.read(baseUrlProvider);
    final confirmed = await showDialog<bool>(
      context: context,
      builder:
          (context) => AlertDialog(
            title: Text(l10n.settingsTokenConfirmRevoke),
            content: Text('${token.name}\n${l10n.personalTokenRevokeHelp}'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: Text(l10n.commonCancel),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: Text(l10n.settingsTokenRevoke),
              ),
            ],
          ),
    );
    if (confirmed != true || !mounted || server != ref.read(baseUrlProvider)) {
      return;
    }
    setState(() {
      revoking = token.id;
      error = null;
    });
    try {
      await api.revoke(token.id);
      if (mounted && server == ref.read(baseUrlProvider)) {
        ref.invalidate(personalTokenListProvider);
      }
    } catch (_) {
      if (mounted) setState(() => error = l10n.personalTokenFailed);
    } finally {
      if (mounted) setState(() => revoking = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final dates = MaterialLocalizations.of(context);
    String formatDate(DateTime value) {
      final local = value.toLocal();
      return '${dates.formatMediumDate(local)} ${dates.formatTimeOfDay(TimeOfDay.fromDateTime(local))}';
    }

    final list = ref.watch(personalTokenListProvider(offset));
    return ExpansionTile(
      leading: const Icon(Icons.key),
      title: Text(l10n.personalTokenTitle),
      subtitle: Text(l10n.personalTokenHelp),
      children: [
        FilledButton.icon(
          onPressed: create,
          icon: const Icon(Icons.add),
          label: Text(l10n.personalTokenCreate),
        ),
        if (error != null)
          Text(
            error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        list.when(
          loading:
              () => const Padding(
                padding: EdgeInsets.all(16),
                child: CircularProgressIndicator(),
              ),
          error:
              (_, _) => Column(
                children: [
                  Text(l10n.personalTokenFailed),
                  TextButton(
                    onPressed:
                        () => ref.invalidate(personalTokenListProvider(offset)),
                    child: Text(l10n.commonRetry),
                  ),
                ],
              ),
          data:
              (page) => Column(
                children: [
                  if (page.tokens.isEmpty)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(l10n.settingsTokenEmpty),
                    ),
                  for (final token in page.tokens)
                    ListTile(
                      title: Text(token.name),
                      isThreeLine: true,
                      subtitle: Text(
                        '${token.prefix}… · ${token.revokedAt == null ? l10n.personalTokenPermanent : l10n.settingsTokenStatusRevoked}\n${l10n.personalTokenCreated}: ${formatDate(token.createdAt)}\n${l10n.personalTokenLastUsed}: ${token.lastUsedAt == null ? l10n.personalTokenNeverUsed : formatDate(token.lastUsedAt!)}',
                      ),
                      trailing:
                          token.revokedAt != null
                              ? null
                              : IconButton(
                                onPressed:
                                    revoking == null
                                        ? () => revoke(token)
                                        : null,
                                icon: const Icon(Icons.delete_outline),
                                tooltip: l10n.settingsTokenRevoke,
                              ),
                    ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        onPressed:
                            offset > 0
                                ? () => setState(() => offset -= 20)
                                : null,
                        icon: const Icon(Icons.chevron_left),
                        tooltip: l10n.personalTokenPrevious,
                      ),
                      Text(
                        '${offset + (page.tokens.isEmpty ? 0 : 1)}–${offset + page.tokens.length} / ${page.total}',
                      ),
                      IconButton(
                        onPressed:
                            offset + page.tokens.length < page.total
                                ? () => setState(() => offset += 20)
                                : null,
                        icon: const Icon(Icons.chevron_right),
                        tooltip: l10n.personalTokenNext,
                      ),
                    ],
                  ),
                ],
              ),
        ),
      ],
    );
  }
}

class _CreateTokenDialog extends ConsumerStatefulWidget {
  const _CreateTokenDialog();
  @override
  ConsumerState<_CreateTokenDialog> createState() => _CreateTokenDialogState();
}

class _CreateTokenDialogState extends ConsumerState<_CreateTokenDialog> {
  final name = TextEditingController();
  String? token;
  String? error;
  bool busy = false;
  bool copying = false;
  bool copied = false;

  @override
  void dispose() {
    name.dispose();
    token = null;
    super.dispose();
  }

  Future<void> generate() async {
    if (busy || name.text.trim().isEmpty) return;
    final l10n = AppLocalizations.of(context);
    final api = ref.read(personalTokenApiProvider);
    final server = ref.read(baseUrlProvider);
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final value = await api.create(name.text);
      if (mounted && server == ref.read(baseUrlProvider)) {
        setState(() => token = value);
      }
    } catch (_) {
      if (mounted) setState(() => error = l10n.personalTokenFailed);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> copy(bool header) async {
    if (copying || token == null) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      copying = true;
      copied = false;
      error = null;
    });
    try {
      await Clipboard.setData(
        ClipboardData(text: header ? 'Authorization: Bearer $token' : token!),
      );
      if (mounted) setState(() => copied = true);
    } catch (_) {
      if (mounted) setState(() => error = l10n.personalTokenCopyFailed);
    } finally {
      if (mounted) setState(() => copying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return PopScope(
      canPop: !busy,
      child: AlertDialog(
        title: Text(l10n.personalTokenCreate),
        content: SizedBox(
          width: 440,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.personalTokenHelp),
                const SizedBox(height: 16),
                if (token == null)
                  TextField(
                    controller: name,
                    enabled: !busy,
                    maxLength: 80,
                    decoration: InputDecoration(
                      labelText: l10n.personalTokenName,
                      hintText: 'Home Assistant',
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                if (token != null) ...[
                  Text(l10n.personalTokenOnce),
                  const SizedBox(height: 12),
                  SelectableText(token!),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    children: [
                      FilledButton(
                        onPressed: copying ? null : () => copy(false),
                        child: Text(l10n.personalTokenCopy),
                      ),
                      OutlinedButton(
                        onPressed: copying ? null : () => copy(true),
                        child: Text(l10n.personalTokenCopyHeader),
                      ),
                    ],
                  ),
                  if (copied) Text(l10n.personalTokenCopied),
                ],
                if (error != null)
                  Text(
                    error!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: busy ? null : () => Navigator.pop(context),
            child: Text(
              token == null ? l10n.commonCancel : l10n.personalTokenDone,
            ),
          ),
          if (token == null)
            FilledButton(
              onPressed: busy || name.text.trim().isEmpty ? null : generate,
              child: Text(busy ? l10n.commonLoading : l10n.personalTokenCreate),
            ),
        ],
      ),
    );
  }
}
