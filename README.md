# Songloft × 零跑 2024+ 车机方控 适配补丁

让 **Songloft 音乐播放器（Flutter Android 端 `songloft-player`）** 在 **零跑 2024–2027 款车机**
（C10 及以后）上正确响应方向盘媒体键（方控：上一首 / 下一首 / 播放暂停）。**==请注意，本项目还未实车验证。==**

[![Build and Release](https://github.com/songloft-org/songloft-player-flutter/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/songloft-org/songloft-player-flutter/actions/workflows/build-and-release.yml)
[![GitHub License](https://img.shields.io/github/license/songloft-org/songloft-player-flutter)](https://github.com/songloft-org/songloft-player-flutter)
[![GitHub Release](https://img.shields.io/github/v/release/songloft-org/songloft-player-flutter)](https://github.com/songloft-org/songloft-player-flutter/releases/latest)
[![Stars](https://img.shields.io/github/stars/songloft-org/songloft-player-flutter)](https://github.com/songloft-org/songloft-player-flutter/stargazers)

---

## 0. 原理（必读）

- 零跑 2024+ 车机把方控作为**一个广播**下发（不是标准 Android KeyEvent，也不是 21–23 那套 ICU 广播）：
  - `action` = `com.leapmotor.steerwheelcontrol.media`
  - int extra `action`：**1 → 上一首，4 → 下一首，7 → 播放/暂停**（其余值归车机，忽略）
- Songloft 本身已用 `audio_service` 实现了 Android `MediaSession` 与
  `MediaButtonReceiver`（`com.ryanheise.audioservice.MediaButtonReceiver`）。
  因此本补丁只做一件事：**把这条广播翻译成标准 `MEDIA_BUTTON` 键事件，转发给 Songloft 已有的媒体会话。**
  不需要 CarPlay、不需要改播放逻辑、不碰仪表/挡位。
- **关键实现点**：该广播来自车机自身控制服务（非特权 UID），必须用 `RECEIVER_EXPORTED` 注册；
  用 `RECEIVER_NOT_EXPORTED` 会被系统过滤、收不到。这正是 2024+ 与 2021–2023 的差异之一。

<p align="center">
  <a href="https://github.com/songloft-org/songloft-player-flutter">🏠 GitHub</a> •
  <a href="https://github.com/songloft-org/songloft-player-flutter/releases/latest">📥 最新正式版</a> •
  <a href="https://github.com/songloft-org/songloft-player-flutter/releases/tag/dev">下载 dev</a> •
  <a href="https://github.com/songloft-org/songloft-player-flutter/issues">💬 问题反馈</a>
</p>

## 1. 文件清单

```
songloft-leapmotor-patch/
├── README.md
├── .github/workflows/
│   ├── build-apk.yml        # GitHub Actions：按 CPU 架构拆分编译 APK 并发布到 Releases
│   └── sync-upstream.yml    # （可选）自动跟随上游 songloft-player 更新
└── patch/
    ├── Leapmotor24Wheel.kt              # 第一层：方控广播监听 + 400ms 去重 + 补发 + 按下/抬起成对转发
    ├── LeapmotorMediaClaim.kt           # 第二层：媒体源声明 MEDIA.INFO（1s 心跳 + 前台门），防双控/抢归属
    ├── Leapmotor24SteeringReceiver.kt   # 静态接收器（文档第 3 步的兜底路径，与动态注册共用去重状态）
    ├── LeapmotorInitProvider.kt         # 进程级初始化入口（ContentProvider，等价 Application.onCreate 注册）
    ├── Leapmotor24ConfigReceiver.kt     # ADB 配置入口：来源模式 / 键码映射 / 媒体源开关
    ├── inject_provider.py               # 增量注入 Provider/Receiver 到 AndroidManifest（幂等）
    └── pin_shared_prefs.py              # 钉住 shared_preferences_android=2.4.28（绕开 2.5.0 JNI 崩溃）
```

把 `patch/` 和 `.github/` 两个目录原样放进你的 `songloft-player` fork 即可。

从 [GitHub Releases](https://github.com/songloft-org/songloft-player-flutter/releases/latest) 下载最新版本：

Release 正文展示版本变更，并保留本节的安装说明链接；历史记录保存在中文 [CHANGELOG.md](CHANGELOG.md)，在版本发布成功后自动更新。各平台安装说明见下表。

本仓库发布 Flutter 标准版，后端仓库发布 Bundle 本地模式客户端。标准版连接独立的 [Songloft 服务器](https://github.com/songloft-org/songloft)；Bundle 版在本机启动随包内嵌的 Go 后端，可在登录页或设置页切换本地/远程模式。Bundle 流水线为各平台固定同一后端提交和客户端子模块提交。

在 Release 页的 **Assets** 中选择适合设备的文件；部分安装格式可能未提供，以实际附件为准。

| 平台 | 下载链接 | 说明 |
|------|----------|------|
| 🌐 **Web (standalone)** | [songloft-web-standalone.tar.gz](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-web-standalone.tar.gz) | 解压到静态服务器，打开站点后配置后端 API 地址；跨源部署需要后端允许该站点访问 |
| 🌐 **Web (embedded)** | [songloft-web-embedded.tar.gz](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-web-embedded.tar.gz) | 用于同源静态服务或后端嵌入构建，隐藏 API 地址输入；解压不会自动替换已运行的服务器前端 |
| 🐧 **Linux** | [songloft-linux-x64.tar.gz](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-linux-x64.tar.gz) | 解压后运行应用，保留完整目录 |
| | [songloft-linux-x64.deb](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-linux-x64.deb) | Debian/Ubuntu x64，使用系统包管理器安装 |
| | [songloft-linux-x64.rpm](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-linux-x64.rpm) | Fedora/RHEL/CentOS x64，使用系统包管理器安装 |
| | [songloft-linux-x64.AppImage](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-linux-x64.AppImage) | 添加执行权限后运行 |
| 🪟 **Windows** | [songloft-windows-x64.zip](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-windows-x64.zip) | x64 便携版，解压后运行应用，保留同目录全部文件 |
| 🍎 **macOS** | [songloft-macos.dmg](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-macos.dmg) | Universal (Intel/Apple Silicon)，打开 DMG 后将应用拖入「应用程序」 |
| | [songloft-macos.zip](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-macos.zip) | Universal (Intel/Apple Silicon)，解压 ZIP 后将应用移入「应用程序」 |
| 🤖 **Android** | [正式版附件](https://github.com/songloft-org/songloft-player-flutter/releases/latest) · [开发版 APK](https://github.com/songloft-org/songloft-player-flutter/releases/download/dev/songloft-android.apk) | 通用包 `songloft-android.apk` 支持 ARM64、ARMv7 和 x86_64。打开 APK 后按提示允许安装 |
| 📱 **iOS** | [songloft-ios-nosign.ipa](https://github.com/songloft-org/songloft-player-flutter/releases/latest/download/songloft-ios-nosign.ipa) | 未签名设备包，需使用自己的开发者证书和适用于目标设备的 provisioning profile 重签后安装 |

> 开发版可在 [dev 分支 Release](https://github.com/songloft-org/songloft-player-flutter/releases/tag/dev) 获取。
> 下载内嵌后端的客户端时，请按 [Bundle 选包说明](#bundle-本地模式)中的文件名选择。

`patch-*.zip`、`manifest-*.json` 和 `version.json` 供客户端检查更新使用，无需手动安装。

## 功能特性

- **跨平台支持**: iOS、Android（手机/平板）、macOS、Windows、Linux、Web
- **车机版本**: 车载信息娱乐系统请使用 [songloft-tv](https://github.com/boluofan/songloft-tv)
- **Bundle 本地模式**: 内嵌 Go 后端，无需服务器，支持本地/远程双模式切换
- **响应式布局**: 3 级断点自适应（Mobile < 600px, Tablet 600-900px, Desktop 900px+），超宽屏（宽高比 > 2.2:1）自动切换 Dock 导航
- **自适应导航**: 手机底栏、平板侧栏、桌面侧边菜单
- **音乐播放**: 基于 just_audio，支持本地和网络歌曲，后台播放
- **歌单管理**: 创建、编辑、删除歌单，添加/移除歌曲
- **歌曲库**：分页搜索与歌曲编辑，可组合筛选本地、网络歌曲与电台。曲库的「播放全部」排除电台、隐藏歌单与有声书歌单中的歌曲
- **电台**：编辑单条电台，或多选电台批量设置自动、HLS、HTTP-FLV 或仅音频播放模式。仅音频模式会关闭视频内容
- **主题切换**: 亮色/暗色/跟随系统
- **JWT 认证**: 双 Token 机制，安全存储（自动降级）

**每次构建成功都会自动发布到 Releases 页，版本号跟随上游源码。**

- 工作流从 `pubspec.yaml` 的 `version:` 字段读取上游版本（例如 `version: 2.13.3+1` →
  Release 版本号 `2.13.3`），以此为 tag 在 **Releases** 页发布一个 **正式 Latest Release**，
  APK 作为 Asset 附上。
- 进你 fork 的 **Releases** 页 → 找到 `Songloft 零跑方控 2.13.3` → 在 Assets 下载
  按**你的运行环境**选对应的文件（**已按 CPU 架构拆分，体积更小、下载更快**）：
  - `songloft-leapmotor-arm64-v8a.apk` —— **64 位 arm64-v8a**，**车机**（零跑 C10 / C11 / C16 等 ARM SoC）用这个。
  - `songloft-leapmotor-x86_64.apk` —— **x86_64**，**PC 模拟器**（雷电 / LDPlayer）用这个。
    ⚠️ 模拟器必须装 x86_64 包：x86_64 模拟器装 arm64 包会走 ARM 二进制翻译（houdini），
    Flutter 引擎在翻译层下极易崩溃 / 卡在启动页，测出的现象**不能代表 App 本身**。
  - 极少数较老 32 位 ARM 车机（armeabi-v7a）本 Release 不提供，需单独构建。
  直链形如：`https://github.com/<你的账号>/songloft-player/releases/tag/2.13.3`
- 每次重新构建（包括 `sync-upstream` 同步上游后）会**删除旧的同名 Release+tag 再重建**，
  所以 Releases 页始终保留最新一份、不会越攒越多。上游一旦升级版本号（如 `2.14.0`），
  新 Release 会自动变成 `2.14.0`。

**备用通道：Actions Artifact**
若 Release 创建失败，构建仍会把 APK 上传为 Actions **Artifacts**（`songloft-leapmotor-apk`，
保留 30 天），进 **Actions → 对应 run → Artifacts** 也能取到。

### 2.3 触发方式

- **分支推送**即触发构建并发布 Release（`push` 到 main/master/build-apk，或 `sync-upstream` 同步后推送）。
- **手动触发**：到 **Actions → Build Songloft APK → Run workflow**（`workflow_dispatch`）。
- 也可打 `v*` tag 推送：构建同样以 pubspec 版本号发布 Release（`GITHUB_REF` 为 tag 时
  Release 直接挂在原 tag 上，不再额外加版本 tag）。

### 2.4 工作流里做了什么

> 本工作流**对齐官方 `.github/workflows/build-and-release.yml` 的 `build-android` job 与其 release 段**
> （已核对：Flutter 钉 `3.44.6`、依赖覆盖走 `third_party/*`、发布用官方同款 `gh` CLI），
> 仅在其基础上做了面向方控的改造：① 拷贝方控补丁；② **按 CPU 架构拆分 APK，只出 arm64-v8a（车机）
> 与 x86_64（PC 模拟器调试）两个包，剔除 armeabi-v7a 32 位 ARM 包**，
> 体积更小、下载更快；③ 版本号**跟随上游 `pubspec.yaml`**（而非原版的 dev 滚动），
> 并加了 Gradle 4G 堆内存兜底与失败日志上传。

1. `setup-java@17`（`temurin`）+ `android-actions/setup-android@v4.0.4`（显式准备 Android SDK 并导出
   `ANDROID_HOME`，官方 build-android 亦如此，构建本身需要 Android SDK）+ `subosito/flutter-action`（Flutter **3.44.6**，与官方一致；
   `pubspec` 约束为 `flutter: '>=3.41.0'`，但官方 CI 验证过的是 3.44.6，直接用同款最稳）。
   并 `flutter config --android-sdk "$ANDROID_HOME"` 让构建与补丁步骤共用同一份 SDK。
2. 另设 `GRADLE_OPTS=-Xmx4g` 防止多插件 release 打包 OOM。
3. 把 `patch/Leapmotor24Wheel.kt`、`patch/LeapmotorInitProvider.kt`、`patch/Leapmotor24ConfigReceiver.kt` 拷进
   `android/app/src/main/kotlin/com/songloft/songloft_flutter/`，并运行 `patch/inject_provider.py`
   把 `LeapmotorInitProvider` 与 `Leapmotor24ConfigReceiver` 注入 `AndroidManifest.xml`（幂等，已注入则跳过）。
   全程**不覆盖、不改动**上游原 `SongloftApplication.kt`——方控通过独立的 ContentProvider 自动初始化，
   因此上游哪怕改了 Application 类名/包名/初始化逻辑，本补丁都不受影响（见第 3 节「上游改动免疫」）。
4. `flutter clean` → **`Pin shared_preferences_android to 2.4.28`**（避免该包 2.5.0 的 Pigeon JNI
   改动导致启动 `NoSuchMethodError` 卡加载页，详见第 7 节第 2 条）→ `flutter pub get`
   → 跑官方同款 **`Compute native contract hash`**（必执行，把
   `android/app/src/main/assets/native_contract.json` 写进 APK 资产——该文件 App 启动时会被读取，
   缺失会导致「正在启动中」卡死，故脚本失败也回退生成占位资产，绝不静默跳过）。
   随后 `Setup Android signing`：若仓库配了 `ANDROID_KEYSTORE_BASE64` 则解码为 `release.jks` 供 Gradle 签名；
   未配则回退 debug 签名（仍可侧装）。
5. `flutter build apk --release --split-per-abi`（**按 CPU 架构拆分**，
   一次产出 arm64-v8a / armeabi-v7a / x86_64 三个包，**保留 arm64-v8a（车机）与 x86_64（模拟器调试），
   剔除 armeabi-v7a 32 位 ARM**）
   （与原版一致的 `--dart-define`：FRONTEND_VERSION / BUILD_TIME / GIT_COMMIT / FLUTTER_BINDING /
   TRACELY_*）→ 收集为 `songloft-leapmotor-arm64-v8a.apk` 与 `songloft-leapmotor-x86_64.apk` 两个文件。
   > 本 fork **不生成前端热更补丁（flutter_patcher）**：仅出完整 APK，安装即覆盖更新，无增量热更产物。
6. 另产出 `version.json`（设置页「检查客户端更新」dev 渠道精确比较用，缺失会「永远显示已是最新」）。
7. **发布 Release**：从 `pubspec.yaml` 取上游版本号作为 tag，先 `gh release delete --cleanup-tag`
   删旧再 `gh release create --latest` 重建（与原版 release job 的滚动逻辑一致），把 APK、
   `version.json` 一并作为 Asset 上传。

> 因为 `android/app/build.gradle.kts` 未配置签名时会**回退到 debug 签名**，产出的 APK 可直接
> `adb install` 侧装到车机。若要发布正式签名包，在仓库 **Settings → Secrets** 里配
> `ANDROID_KEYSTORE_BASE64`（base64 后的 jks）、`ANDROID_KEY_ALIAS`、`ANDROID_KEYSTORE_PASSWORD`、
> `ANDROID_KEY_PASSWORD`，并在 workflow 里解码后让 `signingConfigs.release` 生效即可（见第 5 节）。

### 2.5 自动跟随上游更新（推荐开启）

本目录附带第二条工作流 `.github/workflows/sync-upstream.yml`：

- **每天 UTC 03:00** 自动检查上游 `songloft-org/songloft-player` 是否有新提交；
- 有更新 → 把上游合并进你的 fork（冲突统一取上游，保证拿到最新源码）→ 重打方控补丁 → 推回 fork；
- 推送会**自动触发 `build-apk.yml`** 重新出包并发布到 Releases（版本号跟随上游源码，
  每次重建同名 Release），Releases 页里就是含最新上游 + 方控的 APK。
- 上游无更新时直接跳过，**不会**空跑构建。

开启方式：把本目录的 `.github/`（含 `build-apk.yml` 与 `sync-upstream.yml`）一同放进 fork 即可，
无需额外配置。也可到 **Actions → Sync upstream & build → Run workflow** 手动立即同步一次。

> 想调频率：改 `sync-upstream.yml` 里的 `cron`（GitHub 为 UTC）。例如每 6 小时：`cron: "0 */6 * * *"`。
> 说明：同步会把合并结果**推回你的 `main` 分支**（fork 因此自动跟随上游），随后由 build-apk 出包；
> 若你不想让 fork 的 main 被自动改动，把 sync 工作流里的 `git push` 那一步去掉、改成直接调用构建即可（按需告知我）。

---

## 3. 行为说明 / 排错要点

- **映射表**（int extra `action` → 媒体键，仅「方控安全」集合，其余归车机）：
  | 广播 int extra `action` | 媒体键 |
  |---|---|
  | `1` | 上一首 |
  | `4` | 下一首 |
  | `7` | 播放 / 暂停 |
  - 音量、电话等其它值不属于媒体控制，直接忽略，不影响车机自身功能。
- **350ms 去重**：2024+ 同一次物理按键可能经两条路径到达（车机广播 + 普通 Android 媒体键），
  窗口内同一 keyCode 只放行一次，避免「按一下切两首 / 暂停后又立刻播放」。
- **实车「按一下跳两首」的规避（方控来源模式，ADB 可切）**：2024+ 同一次按键可能同时产生
  「车机广播」与「原生 Android 媒体键」两条事件；本架构下原生媒体键由 `audio_service` 直接处理、
  无法被补丁拦截，故交叉去重采用「只保留一条通道」——通过 ADB 切换来源模式：
  - `auto`（默认）：转发车机广播（350ms 窗口去重同通道重复），绝大多数车型开箱即用；
  - `factory`：等同 auto，留给「只发广播」的车型；
  - `android`：**忽略车机广播、只认原生媒体键**——若你的车出现「按一下跳两首」，执行下面命令切到它即可消除重复。
  - 模式持久化在 SharedPreferences，重启不丢；卸载重装回到 `auto`。
  - 切换命令（车机开 USB 调试后执行）：
    `adb shell am broadcast -a com.songloft.songloft_flutter.leapmotor24.CONFIGURE --es wheel_source android`
    （把末尾 `android` 换成 `auto`/`factory` 即可切回；`adb logcat -s Songloft-Leapmotor` 可见切换日志）
- **仅在播放时生效**：`audio_service` 仅在存在活动媒体会话时响应媒体键，未播放时按方控不会动作。
- **崩溃安全**：方控初始化代码被 `try/catch` 包裹，即便它因任何原因失败，主程序也照常启动——
  从设计上杜绝「装了补丁后 App 打不开（不能进入）」。方控日志用 `adb logcat -s Songloft-Leapmotor` 观察。
- **上游改动免疫（方控与 Application 解耦）**：本补丁**不覆盖**上游 `SongloftApplication.kt`，
  而是通过独立的 `LeapmotorInitProvider`（ContentProvider 自动初始化）挂接方控监听。
  上游哪怕把 `SongloftApplication` 改名、改包名、增删 `onCreate()` 里的初始化逻辑，都和本方控补丁完全无关，
  不会出现「整文件覆盖导致丢失上游改动或编译失败」的情况。`AndroidManifest.xml` 的注入也是增量、幂等、
  且**找不到插入点会显式报错**（而非静默漏掉方控），上游结构大改时你能第一时间发现。
- **CI 构建失败怎么看原因**：`flutter build apk` 失败时，GitHub 收尾只打印
  `Execution failed for task ':app:compileFlutterBuildRelease'` 这一行尾巴，**真正的报错
  在它上面**。工作流已做两件事帮你定位：
  1. 构建步骤把完整日志 `tee` 到文件，**仅当失败时**上传为 **`build-log`** 产物——去 Actions 页下载它看全文；
  2. 失败时自动 `grep` 出首个错误附近内容，直接贴在任务日志里。
  - 若日志里出现 `OutOfMemoryError` / `GC overhead limit exceeded`：工作流已设 `GRADLE_OPTS=-Xmx4g`，
    重跑即可；若仍 OOM，把 `build-apk.yml` 里的 `GRADLE_OPTS` 调到 `-Xmx6g`。
  - 若日志里出现 `Leapmotor24Wheel.kt` / `LeapmotorInitProvider.kt` / `Leapmotor24ConfigReceiver.kt` 的 Kotlin 报错：把那几行发回来，我改补丁。
  - 若日志里是 Dart 编译错误（如 `lib/...: Error:`）或 Flutter 解析/版本报错：那是基础仓库
    `songloft-player` 自身问题（注意本工作流已用官方同款 Flutter **3.44.6**），需看具体信息再判断。
  - 若报 SDK / NDK 缺失（如 `failed to find platform android-35`）：本工作流依赖 GitHub 运行器预装的
    Android SDK；出现此情况多半是 runner 镜像变化，把 `build-apk.yml` 里"校验工程根"之后补一段
    `sdkmanager --install ...` 即可，或告知我处理。

---

## 4. 方式 B：本机构建 APK（备选）

前置：已安装 **Flutter 3.44.6**（与官方构建脚本一致）、**Android SDK（platform 35/36 + build-tools + NDK）**、
**JDK 17**，且 `flutter doctor` 通过。

```bash
# 1) 取源码（在本机执行）
git clone https://github.com/songloft-org/songloft-player.git
cd songloft-player

# 2) 放入补丁（不改动上游 SongloftApplication.kt）
mkdir -p android/app/src/main/kotlin/com/songloft/songloft_flutter
cp /path/to/patch/Leapmotor24Wheel.kt \
   android/app/src/main/kotlin/com/songloft/songloft_flutter/Leapmotor24Wheel.kt
cp /path/to/patch/LeapmotorInitProvider.kt \
   android/app/src/main/kotlin/com/songloft/songloft_flutter/LeapmotorInitProvider.kt
cp /path/to/patch/Leapmotor24ConfigReceiver.kt \
   android/app/src/main/kotlin/com/songloft/songloft_flutter/Leapmotor24ConfigReceiver.kt

# 3) 把 LeapmotorInitProvider + Leapmotor24ConfigReceiver 增量注入 AndroidManifest.xml（幂等，已注入则跳过）
python3 /path/to/patch/inject_provider.py

# 4) 钉住 shared_preferences_android=2.4.28（绕开 2.5.0 的 Pigeon JNI 启动崩溃，见第 7 节第 2 条）
python3 /path/to/patch/pin_shared_prefs.py

# 5) 拉依赖
flutter pub get

# 6) 按 CPU 架构拆分编译 release APK
flutter build apk --release --split-per-abi
# 注：--split-per-abi 会同时产出 arm64-v8a / armeabi-v7a / x86_64，
#     本 fork 只取 arm64-v8a（车机）与 x86_64（模拟器调试），删除 armeabi-v7a。

# 产物：
#   build/app/outputs/flutter-apk/app-arm64-v8a-release.apk   # 车机（ARM SoC）
#   build/app/outputs/flutter-apk/app-x86_64-release.apk      # PC 模拟器（x86_64）
```

### 安装
```bash
# 各平台构建
flutter build web --no-web-resources-cdn                                       # Web (standalone)
flutter build web --no-web-resources-cdn --dart-define=DEPLOY_MODE=embedded    # Web (嵌入模式)
flutter build apk --release                                                    # Android APK
flutter build ios --no-codesign                                                # iOS
flutter build macos                                                            # macOS
flutter build linux                                                            # Linux
flutter build windows                                                          # Windows

# PC 模拟器（雷电 / LDPlayer 等 x86_64）—— 必须装 x86_64 包，别装 arm64 包
adb install -r build/app/outputs/flutter-apk/app-x86_64-release.apk
```

详细构建说明参见 [构建指南](docs/cn/build_guide.md)。

## CI/CD

本仓库通过 GitHub Actions 自动构建和发布：

- **推送 `v*` tag** → 自动构建所有平台并创建正式 Release
- **手动触发** → 构建并发布到分支名对应的 Release（如 `main`）

工作流文件：[`.github/workflows/build-and-release.yml`](.github/workflows/build-and-release.yml)

## 项目结构

```
lib/
├── config/          # 应用配置（API 地址、常量）
├── core/            # 核心层
│   ├── a11y/        # 无障碍辅助
│   ├── audio/       # 音频播放服务
│   ├── backend/     # Bundle 本地模式（嵌入后端抽象层）
│   ├── env/         # 环境信息
│   ├── network/     # HTTP 客户端、认证拦截器
│   ├── platform/    # 平台检测
│   ├── router/      # GoRouter 路由配置
│   ├── storage/     # 本地存储、安全存储
│   ├── theme/       # 主题、响应式断点
│   ├── tracely/     # 前端监控
│   └── utils/       # 工具函数
├── features/        # 功能模块
│   ├── auth/        # 认证（登录/登出/Token 管理/本地模式入口）
│   ├── dlna/        # DLNA 投屏
│   ├── home/        # 首页
│   ├── jsplugin/    # JS 插件管理
│   ├── startup/     # 启动流程（本地/远程模式自动引导）
│   ├── library/     # 歌曲库
│   ├── player/      # 播放器（桌面/移动/迷你）
│   ├── playlist/    # 歌单管理
│   └── settings/    # 设置（主题/扫描/插件/升级）
├── shared/          # 共享层
│   ├── constants/   # 常量定义
│   ├── layouts/     # 自适应布局（AdaptiveScaffold、ShellLayout）
│   ├── mixins/      # 通用 Mixin
│   ├── models/      # 数据模型（Song、ApiResponse、Pagination）
│   ├── utils/       # 共享工具函数
│   └── widgets/     # 通用组件
├── main.dart        # 应用入口
scripts/
├── build-frontend.sh         # 多平台构建脚本
├── bump-version.sh           # 版本发布脚本（语义化版本控制）
├── docker-build-frontend.sh  # Docker 构建便捷脚本
└── download-fonts.sh         # 字体下载脚本
```

## 文档

| 文档 | 说明 |
|------|------|
| [docs/cn/build_guide.md](docs/cn/build_guide.md) | 多平台构建指南 |
| [docs/cn/development.md](docs/cn/development.md) | 开发指南 |
| [docs/cn/architecture.md](docs/cn/architecture.md) | 架构补充说明 |
| [docs/cn/platform-notes.md](docs/cn/platform-notes.md) | 平台特定注意事项 |
| [scripts/README.md](scripts/README.md) | 构建与发布脚本指南 |

## 技术栈

| 类别 | 技术 |
|------|------|
| 状态管理 | Riverpod |
| 路由 | GoRouter |
| HTTP | Dio + JWT 拦截器 |
| 音频 | just_audio + audio_service |
| 本地存储 | SharedPreferences |
| 图片缓存 | CachedNetworkImage |

## 部署模式

| 模式 | 编译参数 | 说明 |
|------|---------|------|
| **standalone** | 默认（不传 `--dart-define`） | 前后端分离部署，显示 API 地址配置 UI，用户手动填写后端地址 |
| **embedded** | `--dart-define=DEPLOY_MODE=embedded` | 嵌入 Go 后端同域部署，自动使用当前域名，隐藏 API 地址 UI |
| **bundle** | `--dart-define=HAS_BACKEND=true` | 客户端内嵌 Go 后端，无需服务器，支持本地/远程双模式切换 |

默认构建（不传 `--dart-define`）等同于 standalone 模式。

### Bundle 本地模式

Bundle 版将 Go 后端嵌入到客户端中，用户无需单独部署服务器：

- **移动端（Android/iOS）**：Go 后端通过 gomobile 编译为原生库（`.aar` / `.xcframework`），Flutter 通过 MethodChannel 调用
- **桌面端（macOS/Windows/Linux）**：Go 后端编译为 `songloft-server` 可执行文件，启动时作为子进程运行
- **Web**：不支持 Bundle 模式

本地构建以 Android 为例。先准备主仓库源码并初始化 `pkg/tag`，脚本使用该 checkout 的 HEAD：

```bash
SONGLOFT_BACKEND_SOURCE=/absolute/songloft bash scripts/build-bundle-backend.sh android
flutter build apk --dart-define=HAS_BACKEND=true
```
- 按方控「下一首」应看到：`Songloft-Leapmotor: 方控转发 action=4 -> keyCode=87`
- 若 log 里只有 `方控忽略（非媒体键）action=...` 或**毫无任何 `Songloft-Leapmotor` 日志**：
  说明你的车机固件广播的 action / extra 命名与本文不同，把那段 logcat 发我，我补进协议即可。

Android、iOS、Linux、Windows、macOS 的 Bundle 由 [后端仓库](https://github.com/songloft-org/songloft) 构建和发布，流水线固定客户端和后端提交。本仓库发布标准版。

Android 和桌面端首次启用本地模式时选择音乐目录，iOS 使用应用 Documents 目录；无需在首次启动时通过独立弹窗选择运行模式。Bundle 资产在[后端仓库 Releases](https://github.com/songloft-org/songloft/releases/latest)中，文件名以 `songloft-bundled-` 开头：

| 平台 | Bundle 下载 |
|---|---|
| Android ARM64 / ARMv7 / x86_64 | 通用包 `songloft-bundled-android.apk`。[正式版附件](https://github.com/songloft-org/songloft/releases/latest) · [开发版 APK](https://github.com/songloft-org/songloft/releases/download/dev/songloft-bundled-android.apk) |
| iOS | `songloft-bundled-ios-nosign.ipa`（未签名，需自行重签） |
| Linux x86_64 | `songloft-bundled-linux-x64.tar.gz` |
| macOS Apple Silicon | `songloft-bundled-macos.zip` |
| Windows x86_64 | `songloft-bundled-windows-x64.zip` |

[完整包名清单](https://github.com/songloft-org/songloft/blob/main/docs/release-packages.md)包含 Flutter 标准版和 Bundle。

仓库 `Settings → Secrets and variables → Actions → New repository secret` 添加：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 your.keystore` 的输出 |
| `ANDROID_KEY_ALIAS` | keystore 里的 alias |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_PASSWORD` | key 密码 |

`android/app/build.gradle.kts` 已读取这些环境变量填充 `signingConfigs.release`，
配置后产物即为正式签名 APK。未配置则回退 debug 签名（仍可侧装）。

---

## 6. 已知限制

- 覆盖**媒体控制**（上/下一首/播放暂停）。**仪表投屏、车速/挡位联动**属于 DiPlay/CarPlay 专属能力，
  且零跑 2024+ 的挡位走 AIDL 接口、语义尚未确认（上游 LEAPMOTOR24.md 明确本代不做挡位驱动行为），
  Songloft 是纯音乐 App，本补丁不包含这些。
- 若车机固件用的是本文之外的广播命名，把 `logcat` 里的相关日志发我，我把它加进
  `Leapmotor24Wheel.ACTION_MEDIA` / `EXTRA_ACTION` 即可。
- 适配范围：**零跑 2024–2027 款**（C10 及以后）。2021–2023 款为另一套协议，需单独适配。
- 仓库 `android/app/libs/` 下无 Go 后端 AAR（仅为空占位），`BackendPatchManager` 是仓库自带
  Kotlin 源码，故 clean checkout 即可直接编译；Go 后端缺失仅影响该后端对应功能，不影响方控与主播放。

     换成 x86_64 包后这些现象应消失。若换包后仍有问题，才是真的 App 层问题。

**标准版**需要配合 [Songloft 后端](https://github.com/songloft-org/songloft) 服务运行。默认连接 `http://localhost:58091`，可在登录页中修改 API 地址。

**Bundle 版**内嵌 Go 后端，无需单独部署服务器；客户端使用本地后端生成并保存在应用数据目录中的管理员凭据自动登录。

默认账号：admin / admin

🔗 **服务端 GitHub**: [https://github.com/songloft-org/songloft](https://github.com/songloft-org/songloft)

## 许可证 / 第三方组件

本项目**源代码**基于 [Apache-2.0 license](LICENSE) 开源。

> **LGPL 合规提示**：本客户端在 Windows / Linux 上通过 `just_audio_media_kit` 调用 libmpv（LGPL-2.1+）作为音频后端。Windows 端打包的是 **audio-only LGPL 构建**（不含 GPL 编码器如 libx264 / libx265），Linux 端动态链接系统的 libmpv。完整的第三方组件清单、许可证类型与源码获取途径见 [NOTICE](NOTICE)。
