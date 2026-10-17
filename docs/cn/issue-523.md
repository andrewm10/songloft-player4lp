# 歌词可读性与内置配色 · songloft-org/songloft#523

2026-10-15，关联 [songloft-org/songloft#523](https://github.com/songloft-org/songloft/issues/523)。本批修改 Flutter 手机与桌面全屏播放器及外观设置。

## 2026-10-17 · 封面颜色仍不明显

交付授权：用户在获知停止播放测试与原生切歌验收缺口后，明确授权本批提交、推送并回复 songloft-org/songloft#523。下方未提交状态为验证快照；保留失败与未跑边界，最终提交、CI、dev 包与评论按同一 SHA 核对，不新打正式版 tag、不关闭 Issue。

报告人[确认应用与系统无障碍开关均已关闭](https://github.com/songloft-org/songloft/issues/523#issuecomment-6228812821)，因此 10-16 的设置路径不能解释本次反馈。基线 `18b50cbca02a7470279ddcd04907f71365140a57` 还有独立缺口：默认取色过滤器丢弃暖棕和黑白颜色；渐变下半部在没有 mutedColor 时使用主题色；浅色背景仅叠加 12%→4% 封面色。相同相对封面路径的缓存没有服务器身份，切服可能沿用旧取色。这些是本地已确认缺陷，尚未确认报告人原机根因。

本批保留完整封面颜色，并在客户端按 100px 宽降采样；以实际资源 URL 缓存并订阅服务器变化。优先使用实际鲜艳色，无鲜艳色时使用主色；背景浅色 28%→18%、深色 24%→18%，底部无柔和色时沿用鲜艳色/主色。自审增加「相同黑色主色、不同彩色部分」回归，未优先鲜艳色时浅/深两项均因零色差失败。文字按封面黑白极值计算，覆盖切歌动画的旧背景，必要时在 onSurface 之后继续加强至黑/白。缺失/失败封面与增强对比度的兜底策略保留。

新增真实 HTTP + PNG 解码的暖棕/黑白、切服缓存回归，以及上下两端封面色差检查；旧实现下两项取色测试和两项色差测试均失败。初次加深背景又被原有对比度/动画测试拦截，补齐文字边界后相关 12 项通过。完整验证对象、命令、截图和剩余边界见 `/tmp/songloft-523-followup-20261017/validation.json`；使用独立数据库和合成媒体。

验收截图：[Android 浅色珊瑚封面](../assets/issue-523/followup-20261017/android-light-coral.png) · [Android 浅色海蓝封面](../assets/issue-523/followup-20261017/android-light-ocean.png)（暂停队列恢复）· [Web 黑底青蓝封面](../assets/issue-523/followup-20261017/black-cyan-web-light.png)。

最终验证：Linux amd64 / Flutter 3.44.8 / Dart 3.12.2；静态分析、格式、嵌入 Web 和 Android x64 Debug 构建通过。首轮候选 732 项完整测试通过；最终鲜艳色版本的全量重跑中，停止播放用例 `a pending source finishing after stop cannot auto-play` 失败（731 通过、1 失败），再次全量仍失败，单独运行该用例及其完整文件四项均通过。停止链路未改，原因未确认，不能把单跑通过当成全量通过；配色相关检查通过。Docker Chrome 实际手机浅色/桌面深色切歌与回退通过，背景上下区域封面像素差为 29–55，缺失/失败回退差为 0；新增黑底青蓝封面的实际解码与鲜艳色背景也在亮暗两种模式通过。Android 34 完成最终包浅/深各四首的暂停队列恢复与原生背景像素检查；这不等于实际播放切歌通过。验收早期候选时，原生点击下一首曾触发 `PlayerNotifier._syncLiveActivitySong` 读取歌词状态的 `CircularDependencyError` 并停留加载态，保留为未解决验收项；本批未改这两个 Provider，未证明错误来源。后一次坐标重试没有切换歌曲，不算成功或未复现证据。原始错误只在初次 logcat 读取中保留，后续种子脚本清除了设备日志，验证文件区分该限制。Not run：报告人原机、户外、其他原生平台与未提交工作树的远端 CI。脱敏截图与验证副本在 `/home/hanxi/work/songloft-review/issue-523-20261017/`。

## 2026-10-16 · 纯色背景反馈

交付授权：用户已于 2026-10-16 授权本批提交、推送及回复 songloft-org/songloft#523。下文未提交/未回复状态为验证阶段快照；最终提交、CI 与评论以 Git/GitHub 同一 SHA 的记录为准，不打正式版 tag、不关闭 Issue。

针对[后续评论](https://github.com/songloft-org/songloft/issues/523#issuecomment-6210432894)，核对当前 `bb03a5c49eb6701d90a73b924ed12ec388c6560c`：增强对比度会主动将播放器背景设为纯色 surface；应用开关与系统高对比度按 OR 合并，应用开关关闭不能覆盖系统设置。Flutter 的「减少透明度」只影响玻璃材质，不会单独停用这里的取色渐变。

临时 Flutter 测试直接运行实际 `AppTheme` 与 `PlayerBackdropColors`，确认有封面时普通模式仍取色，增强对比度时使用纯色。反馈截图与无障碍纯色路径吻合，但报告人的开关状态、实际安装版本及 Flutter 原生表现尚未确认，不能认定设备根因或宣称功能已经修好。

本次仅补充中/英/西班牙语增强对比度说明及生成物，明确纯色背景不会随封面变化；保留既有策略。证据目录 `/tmp/songloft-523-followup/`，最终验证对象、命令和结果见 `validation.json`。文案修改不新增回归测试，旧实现失败验证不适用。Not run：原生设备、户外与报告人曲库；未提交、推送、发布或回复 Issue。

本批验证：Linux amd64 / Flutter 3.44.8 / Dart 3.12.2；格式、静态分析、19 项相关测试、嵌入 Web 与 Go 嵌入构建通过；Docker Chrome 实际显示中英文开关说明，无页面异常。首次测试命令误写两条不存在的路径，修正后上述 19 项通过，原失败保留在证据目录。

## 行为与原因

- 原全屏背景把封面、darkMutedColor、主题渐变及黑色顶部遮罩叠加，歌词却仍直接使用主题 primary；亮色模式背景被压暗，歌词与背景没有协同计算。译文和罗马音额外降低透明度，进一步降低对比度。
- 手机和桌面共用 `PlayerBackdrop`：封面颜色混合到主题 surface，生成柔和渐变；不再绘制全屏模糊图层或黑色顶罩。Windows 同样使用取色渐变，避免历史 saveLayer 问题。
- 封面缺失、加载中、失败均回到主题默认渐变；不会按歌曲元数据生成不同的兜底背景。主题包的 playerGradient 首尾色用于默认渐变，封面取色可用时优先使用封面。增强对比度使用纯色 surface。
- 当前行、翻译、罗马音和逐字歌词共用按背景加强的主题强调色，目标对比度 7:1；其他歌词目标 4.5:1。顶部文字和按钮使用 onSurface。
- 外观设置提供星蓝、青碧、森绿、玫瑰、琥珀、紫藤六种内置配色，适配浅色/深色/系统模式。设备偏好 `built_in_palette` 不参与服务器同步；服务器主题包优先，恢复默认后重新使用设备保存的配色。中、英、西班牙文文案已生成。

## 验证对象

- `songloft-org/songloft-player` 基线 `b80e03b47eefb1aaedf07d611b1d9d3c3d48257b` + 未提交工作树，源码标识 `1eac2d6a16e45e318fe1325d5f29db00e965279c3429a99d1c7c0b3385bc8185`。算法：`git diff --binary -- lib test`，追加排序后的未跟踪 lib/test 路径、NUL、内容、NUL，再计算 SHA-256；不含文档。
- 父仓库基线 `9d008b8026abb56a2ee3aae29f71017460bcc1af`，本批仅修改相关双语文档；子模块仍为 dirty，未提交新指针。
- Linux amd64、Flutter 3.41.5、Web embedded、Docker browserless/Chrome 121。使用临时数据库及合成 WAV/封面/歌词，不操作用户曲库。

## 验证结果

- `dart format lib/ test/`、`flutter analyze --no-pub` 通过；`flutter test --no-pub` 全部 716 项通过。扩展后的两个新测试文件共 8 项通过，覆盖六种配色、亮暗模式、极端封面、渐变采样、增强对比度、翻译/逐字高亮、启动时选择、快速写入、恢复、主题包优先及实际 App 切换。
- 隔离副本只换回基线 `lyrics_view.dart`：亮/暗模式两项渲染回归按预期失败（分别在当前行 5.17:1 和译文 5.82:1 处低于 7:1 目标），不是编译失败。原工作树未被回退。
- `make build-frontend-web-embedded` 及 `go build -tags dev -o /tmp/songloft-523/server .` 通过。
- 浏览器完整结果见 `/tmp/songloft-523/browser-results.json`；背景像素比对见 `background-pixels.json`。手机中文浅色与桌面英文深色均从首页应用内进入播放器，切换珊瑚/海蓝/无封面/失效封面，检查歌词、默认背景和工具栏；外观页选择玫瑰后检查 localStorage，并刷新根页后应用内重新进入外观确认选中态恢复。截图见同目录 `zh-light-*.png`、`en-dark-*.png`、`*-settings-rose.png`。
- 日志与完整工作树对象见 `/tmp/songloft-523/{validation.json,analyze-final.log,all-tests.log,expanded-tests.log,before-lyrics.log,web-build.log,browser.log}`。
- Not run：Android/iOS/Windows/macOS/Linux 原生客户端构建及真机户外验收；本批未改原生桥接、音频或后端 API。Web 不能替代原生设备验收。

## 效果截图与交付

代表性原图：[手机浅色歌词](../assets/issue-523/light.png) · [桌面深色歌词](../assets/issue-523/dark.png) · [玫瑰配色设置](../assets/issue-523/palette.png)。完整 59 张跨客户端截图及验证记录保留在 `/home/hanxi/work/songloft-review/issue-523-lynx-20261015/`。

2026-10-15 用户已授权提交、推送和回复 songloft-org/songloft#523；先提交两个客户端，再更新父仓库指针。以上工作树状态为验证阶段快照，最终提交和开发包状态按 Git/同一 SHA 的 CI 核对。本批不打正式版 tag，不关闭 Issue。
