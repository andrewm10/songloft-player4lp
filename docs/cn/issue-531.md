# 电台断流后保留当前台并持续重连

关联 [songloft-org/songloft#531](https://github.com/songloft-org/songloft/issues/531)，2026-10-20。

## 行为与根因

Flutter 客户端将直播的 `completed` 当成普通歌曲完成，进入播放模式、下一首预约及按歌曲数计算的睡眠定时器；加载失败另有最多七次网络重试，耗尽后自动跳台。这两个入口都需要修复。

现在 `isLive` 直播的完成和运行中错误都只重连当前台；加载失败持续重试，间隔按 2、4、8、10 秒退避，此后保持 10 秒。退避指数在计算前截断，避免长期重试的数值溢出导致零延迟循环。断流不发送歌曲完成事件，不消费下一首预约或睡眠歌曲数，播放模式保持原值。普通歌曲继续使用原有有限重试与完成行为。

暂停、停止、清空队列、手动换台及 Provider 销毁使旧重连代次失效；暂停同时废弃音频 Handler 内的未完成加载，防止其结束后自动播放。通知栏播放键可重新加载暂停/停止后的直播。按时长的睡眠定时器仍可停止重连。

原生验证进一步发现加载失败会使底层进入 idle，媒体会话随之失活。重连等待期间现在发布 buffering 与播放意图，保留前台媒体会话和暂停/停止控件；暂停、停止立即清除此状态。

## 验证对象

- 仓库 `songloft-org/songloft-player`，基线 `d89934eea468c22f635d28e5bfd0b5fe81c98147` + 未提交工作树；父仓库基线 `79fb357a59dc9f0eb9364b2a17e1b42c8b67d908`。
- 代码差异标识：`git diff --binary -- lib test` 的 SHA-256 为 `639a6245afa55c42998dde10e307e8a19c1c4630bf6ed6f2c8c1a7b9d39863d2`。详细对象和日志位于 `/tmp/songloft-531-20261020/`。
- 环境：Linux amd64、Flutter 3.44.8、Dart 3.12.2；隔离 Go dev,lite 后端（本机运行）和两个合成 MP3 电台，不访问私人曲库。

## 验证记录

- 首轮全量 764 项通过；补充媒体会话修复后，相关 291 项通过，最终 `flutter test --no-pub --concurrency=2` 全量 767 项通过（`full-final.log`），`flutter analyze --no-pub` 无问题（`analyze-media-final.log`）。格式与 `git diff --check` 通过。
- 回归覆盖五种播放模式、下一首预约、睡眠歌曲数、离线三分钟后原台恢复、初次加载取消、重复完成事件、暂停/停止/换台/销毁、通知播放恢复、按时长睡眠，以及重连媒体会话。
- 隔离旧 Provider 与退避实现，完成路由、重试耗尽和超长退避的新回归按目标行为失败；旧实现会切到 B 台，或将极大重试次数的退避算成零。9 项目标回归均失败（`before-final.log`）；单独破坏媒体会话保持后，buffering 回归也按预期失败（`media-negative.log`）。编译错误不计作反向验证。
- 首轮 Android Debug x86_64 构建通过；API 34 模拟器从真实界面的「播放全部」启动两台队列，MediaSession 确认 A 台位置推进；断网 20 秒内持续请求 A 台。后续因宿主内存压力模拟器退出，110 秒采样超时，该轮不算完整验收。宿主争用期间还出现过测试超时；最终在模拟器停止后重跑的 767 项全量测试通过，未掩盖首轮失败日志。发现的 idle 媒体会话问题已补修。
- 最终 Android Debug x86_64 APK 编译通过（`apk-final.log`），SHA-256 `6ce61569845db6933fd2fd5a1c413f26869cb093ee85b9ac0e3cff0d03f2d96f`。API 34 模拟器在两台队列/列表循环下断网 110 秒，12 次上游请求全部指向 A 台，媒体会话始终 active/buffering；恢复后原台播放，3 秒采样中位置从 17,575ms 推进至 20,933ms。媒体暂停后 27 秒不再请求，网络恢复仍保持暂停；媒体播放键恢复 A 台；断网期间停止后恢复网络，超过 10 秒重试间隔仍无新请求，媒体失活。`native-result.json`、`native-probe.log` 与 `native-{playing,offline,recovered,stopped}.png` 保存状态及画面，`validation.json` 汇总验证对象。
- Not run：报告人的 Android 真机及 Docker 部署环境、iOS/桌面/Web 的实际音频与长后台、DLNA 投屏、Bundle 后端和远端 CI；本次修改 Flutter 客户端，Lynx 客户端未修改。

本地验证执行于提交前。用户随后授权提交、推送和回复 Issue；提交前同步远端 `2621aecda59f70fec344954f000ba57532aa7abf`（子仓库）与 `ebd9bc42f1d2fe9a6a39be58437fba719a5a6a05`（父仓库），新增内容均仅为 CHANGELOG，已核对上述代码差异标识不变。最终提交与推送后 CI 以对应 SHA 的 Git 历史/工作流为准，本轮不打正式版 tag。

建议提交信息：`fix(player): 电台断流持续重连并保留当前台 (Ref songloft-org/songloft#531)`。

补充排查：Lynx 远端 `main` 的 `f3f78b3aae6e74323b963a9e8c940bf74b3613b5` 仍存在同类入口：直播 completed 沿用普通完成路由，错误重试 1/3/9 秒后可能跳台；本批未修改或验收 Lynx，Issue 回复须明确此边界，不据此关闭 Issue。
