# 永久访问 Token（songloft-org/songloft#546）

设置 → 账户 → 永久访问 Token 可输入名称并创建独立设备凭证。创建弹窗支持复制 Token 和 Authorization 请求头；完整凭证只显示一次。复制失败时保留弹窗，可重试或手动选择复制。列表分页显示名称、前缀、永久/撤销状态、创建时间、最近使用时间，撤销前需确认。

`PersonalTokenApi` 使用当前服务器的认证 Dio，接口为 `/api/v1/auth/personal-tokens`。完整凭证只保留于弹窗临时状态，不进入 Provider 或持久存储，调试日志跳过这些端点。退出登录不会撤销设备 Token。需要配套支持此接口的后端；本批未发布。

2026-10-28，Linux x86_64 / Flutter 3.44.8，基线 `224fec0f74f31139761935c612e1ceb8d8e24717` + 未提交工作树：`dart format lib/ test/`、`flutter analyze --no-pub`、793 项测试与 embedded Web release 构建通过。新测试覆盖创建、两种复制、失败恢复、关闭后隐藏凭证、确认撤销、API 请求及日志脱敏。最终源码指纹与跨仓库验证索引 `/tmp/songloft-546-verification.json`；浏览器证据 `/tmp/songloft-546-flutter-ui-result.json` 与对应 PNG。

Not run：六平台原生构建/真机、真实 Home Assistant / ESPHome 和音箱。本批已获提交、推送、同步父指针和回复 #546 的授权；正式发布与关闭 Issue 不在授权内。

最后调整：列表日期使用系统本地化日期/分钟，隐藏无用的秒与微秒。最终工作树已重新通过静态分析、全量 793 项测试；Web release 构建和真实 Chrome 的创建/两种复制/隐藏凭证/确认撤销/401 全流程再次通过。
