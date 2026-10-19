# Songloft × 零跑 2024+ 车机方控 适配补丁

让 **Songloft 音乐播放器（Flutter Android 端 `songloft-player`）** 在 **零跑 2024–2027 款车机**
（C10 及以后）上正确响应方向盘媒体键（方控：上一首 / 下一首 / 播放暂停）。

> 适用车型：**零跑 2024–2027 款**。2021–2023 款用的是另一套完全不同的协议
> （CAN→ICU→广播 `leapmotor.cantoapp.update`），本补丁**不覆盖**那一代理由：
> 两代车机给第三方 App 的接口完全不是一套，混用会互相污染（详见上游 LEAPMOTOR24.md）。
> 若你的车是 2021–2023 款，请另告知，我单独出适配。

---

## 0. 原理（必读）

- 你给的 `LEAPMOTOR24.md` 是 **DiPlay（CarPlay/Android 投屏 App）** 的零跑 2024+ 适配说明，
  里面大量代码（CarPlay 会话、仪表投屏、车速/挡位 AIDL 诊断）与 Songloft 无关。
  真正能复用的只有 **「方向盘按键 → 广播 → 媒体键」** 这一段。
- 零跑 2024+ 车机把方控作为**一个广播**下发（不是标准 Android KeyEvent，也不是 21–23 那套 ICU 广播）：
  - `action` = `com.leapmotor.steerwheelcontrol.media`
  - int extra `action`：**1 → 上一首，4 → 下一首，7 → 播放/暂停**（其余值归车机，忽略）
- Songloft 本身已用 `audio_service` 实现了 Android `MediaSession` 与
  `MediaButtonReceiver`（`com.ryanheise.audioservice.MediaButtonReceiver`）。
  因此本补丁只做一件事：**把这条广播翻译成标准 `MEDIA_BUTTON` 键事件，转发给 Songloft 已有的媒体会话。**
  不需要 CarPlay、不需要改播放逻辑、不碰仪表/挡位。
- **关键实现点**：该广播来自车机自身控制服务（非特权 UID），必须用 `RECEIVER_EXPORTED` 注册；
  用 `RECEIVER_NOT_EXPORTED` 会被系统过滤、收不到。这正是 2024+ 与 2021–2023 的差异之一。

---

## 1. 文件清单

```
songloft-leapmotor-patch/
├── README.md
├── .github/workflows/
│   ├── build-apk.yml        # GitHub Actions：按 CPU 架构拆分编译 APK 并发布到 Releases
│   └── sync-upstream.yml    # （可选）自动跟随上游 songloft-player 更新
└── patch/
    ├── Leapmotor24Wheel.kt       # 新增：零跑 2024+ 方控广播监听 + 媒体键转发 + 来源模式
    ├── LeapmotorInitProvider.kt  # 新增：ContentProvider 自动初始化入口（无需改上游 Application）
    ├── Leapmotor24ConfigReceiver.kt # 新增：ADB 配置入口，实车切换方控来源（规避跳两首）
    └── inject_provider.py         # 增量注入 Provider/Receiver 到 AndroidManifest（幂等）
```

把 `patch/` 和 `.github/` 两个目录原样放进你的 `songloft-player` fork 即可。

---

## 2. 方式 A：GitHub Actions 自动出包（推荐，无需本机工具链）

### 2.1 Fork 并放入补丁

1. 在 GitHub 上 Fork **`songloft-org/songloft-player`** 到你自己的账号。
   （确认 fork 后根目录能看到 `pubspec.yaml` + `android/` + `lib/`，才算 Fork 对了。）
2. 把本目录里的 `.github/` 和 `patch/` 两个文件夹，整体复制进你的 fork 根目录
   （与 `pubspec.yaml`、`android/` 同级）。
3. 提交并推送到 fork 的 `main`（或 `master` / `build-apk`）分支。

> 工作流会自动在仓库内定位 `pubspec.yaml`；若你仍放错了仓库（找不到 pubspec.yaml），
> 它会直接报错并提示改用 `songloft-player`。推送后 GitHub 自动触发
> `Build Songloft APK (Leapmotor 方控)` 工作流。

### 2.2 取 APK（Releases 页，推荐）

**每次构建成功都会自动发布到 Releases 页，版本号跟随上游源码。**

- 工作流从 `pubspec.yaml` 的 `version:` 字段读取上游版本（例如 `version: 2.13.3+1` →
  Release 版本号 `2.13.3`），以此为 tag 在 **Releases** 页发布一个 **正式 Latest Release**，
  APK 作为 Asset 附上。
- 进你 fork 的 **Releases** 页 → 找到 `Songloft 零跑方控 2.13.3` → 在 Assets 下载
  按车机芯片选对应的文件（**已按 CPU 架构拆分，体积更小、下载更快**）：
  - `songloft-leapmotor-arm64-v8a.apk` —— **64 位车机**（主流 2024+ 零跑，如 C10 / C11 / C16 等，优先下这个）；
  - `songloft-leapmotor-armeabi-v7a.apk` —— **32 位车机**（较老 SoC）。
  - 不确定就先下 `arm64-v8a`；若安装提示 `INSTALL_FAILED_CPU_ABI_INCOMPATIBLE` 再换 `armeabi-v7a`。
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
> 仅在其基础上做了面向方控的改造：① 拷贝方控补丁；② **按 CPU 架构拆分构建 APK（arm64-v8a / armeabi-v7a）**，
> 仅保留车机所需的 ARM 架构、剔除 x86_64，体积更小、下载更快；③ 版本号**跟随上游 `pubspec.yaml`**（而非原版的 dev 滚动），
> 并加了 Gradle 4G 堆内存兜底与失败日志上传。

1. `setup-java@17`（`temurin`）+ `android-actions/setup-android@v3`（显式准备 Android SDK 并导出
   `ANDROID_HOME`，官方 build-android 亦如此；`build-tools` 下的 `aapt` 供后续热更补丁读取各 ABI 的
   `versionCode` 使用）+ `subosito/flutter-action`（Flutter **3.44.6**，与官方一致；
   `pubspec` 约束为 `flutter: '>=3.41.0'`，但官方 CI 验证过的是 3.44.6，直接用同款最稳）。
   并 `flutter config --android-sdk "$ANDROID_HOME"` 让构建与补丁步骤共用同一份 SDK。
2. 另设 `GRADLE_OPTS=-Xmx4g` 防止多插件 release 打包 OOM。
3. 把 `patch/Leapmotor24Wheel.kt`、`patch/LeapmotorInitProvider.kt`、`patch/Leapmotor24ConfigReceiver.kt` 拷进
   `android/app/src/main/kotlin/com/songloft/songloft_flutter/`，并运行 `patch/inject_provider.py`
   把 `LeapmotorInitProvider` 与 `Leapmotor24ConfigReceiver` 注入 `AndroidManifest.xml`（幂等，已注入则跳过）。
   全程**不覆盖、不改动**上游原 `SongloftApplication.kt`——方控通过独立的 ContentProvider 自动初始化，
   因此上游哪怕改了 Application 类名/包名/初始化逻辑，本补丁都不受影响（见第 3 节「上游改动免疫」）。
4. `flutter pub get` → 跑官方同款 **`Compute native contract hash`**（必执行，把
   `android/app/src/main/assets/native_contract.json` 写进 APK 资产——该文件 App 启动时会被读取，
   缺失会导致「正在启动中」卡死，故脚本失败也回退生成占位资产，绝不静默跳过）。
   随后 `Setup Android signing`：若仓库配了 `ANDROID_KEYSTORE_BASE64` 则解码为 `release.jks` 供 Gradle 签名；
   未配则回退 debug 签名（仍可侧装）。
5. `flutter build apk --release --split-per-abi`（**按 CPU 架构拆分**，
   一次产出 arm64-v8a / armeabi-v7a / x86_64 三个包，仅保留车机需要的 ARM 两个并剔除 x86_64）
   （与原版一致的 `--dart-define`：FRONTEND_VERSION / BUILD_TIME / GIT_COMMIT / FLUTTER_BINDING /
   TRACELY_*）→ 收集为 `songloft-leapmotor-arm64-v8a.apk` 与 `songloft-leapmotor-armeabi-v7a.apk`
   两个独立文件。
6. **前端热更补丁（对齐官方 `flutter_patcher`）**：用 `aapt` 读取两个 ARM APK 各自真实的 `versionCode`
   （split-per-abi 后 versionCode 被改写为 ABI 偏移值，必须读真实值），调 `dart run flutter_patcher:pack`
   产出 `patch-<abi>-<commit>.zip` + `manifest-<abi>.json`（按 ABI 各一份），随 Release 一并上传，
   供 App 内置热更机制拉取。该步骤为「尽力而为」——缺 flutter_patcher / aapt 时仅告警跳过，不影响 APK 发布。
7. 另产出 `version.json`（设置页「检查客户端更新」dev 渠道精确比较用，缺失会「永远显示已是最新」）。
8. **发布 Release**：从 `pubspec.yaml` 取上游版本号作为 tag，先 `gh release delete --cleanup-tag`
   删旧再 `gh release create --latest` 重建（与原版 release job 的滚动逻辑一致），把 APK、
   热更 `patch-*.zip` / `manifest-*.json`、`version.json` 一并作为 Asset 上传。

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

# 4) 拉依赖
flutter pub get

# 5) 按 CPU 架构拆分编译 release APK（仅保留车机需要的 ARM 包，剔除 x86_64）
flutter build apk --release --split-per-abi

# 产物（按架构拆分，按需取用）：
#   build/app/outputs/flutter-apk/app-arm64-v8a-release.apk     # 64 位车机
#   build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk   # 32 位车机
#   build/app/outputs/flutter-apk/app-x86_64-release.apk        # 模拟器用，车机用不到，可删
```

### 安装到车机
```bash
# 方式 A：USB 调试（车机需开启「开发者选项 → USB 调试」）
# 64 位车机：
adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
# 32 位车机：
adb install -r build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk

# 方式 B：把对应 apk 拷到 U 盘 / 车机文件管理器里手动安装
```

### 验证方控是否生效（车机接上电脑 adb）
```bash
adb logcat -s Songloft-Leapmotor
```
- 按方控「下一首」应看到：`Songloft-Leapmotor: 方控转发 action=4 -> keyCode=87`
- 若 log 里只有 `方控忽略（非媒体键）action=...` 或**毫无任何 `Songloft-Leapmotor` 日志**：
  说明你的车机固件广播的 action / extra 命名与本文不同，把那段 logcat 发我，我补进协议即可。

---

## 5. 可选：正式签名（GitHub Actions 内）

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
