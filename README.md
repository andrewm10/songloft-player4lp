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
    ├── inject_provider.py         # 增量注入 Provider/Receiver 到 AndroidManifest（幂等）
    └── pin_shared_prefs.py        # 钉住 shared_preferences_android=2.4.28（绕开 2.5.0 JNI 崩溃）
```

把 `patch/` 和 `.github/` 两个目录原样放进你的 `songloft-player` fork 即可。

---

## 2. 方式 A：GitHub Actions 自动出包（推荐，无需本机工具链）

### 2.1 Fork 并放入补丁

> ⚠️ **最常见踩坑：Fork 错仓库。**
> 你最初拿到的链接 `https://github.com/songloft-org/songloft` 是**父 monorepo**，真正的 Flutter
> 客户端在它的子模块 `clients/player` 里。直接 Fork 父仓库后，子模块**不会**被自动拉下来，
> 仓库里既没有源码、也没有 `pubspec.yaml`，于是 `flutter pub get` 会报
> `Expected to find project root` 然后失败。
> **正确做法：Fork 下面这个仓库（客户端本体）：**

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
# 车机（ARM SoC）—— 方式 A：USB 调试（需开启「开发者选项 → USB 调试」）
adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
# 车机 —— 方式 B：把 apk 拷到 U 盘 / 车机文件管理器里手动安装

# PC 模拟器（雷电 / LDPlayer 等 x86_64）—— 必须装 x86_64 包，别装 arm64 包
adb install -r build/app/outputs/flutter-apk/app-x86_64-release.apk
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

## 7. 安装后卡在「正在启动中」如何排查

先澄清：**方控补丁不会造成卡死**——它的初始化仅在 App 启动早期注册一个广播接收器，全程 `try/catch`、
且不参与主界面流程。若卡在加载页，按下面顺序查：

1. **抓日志定位**（车机接电脑）：
   ```bash
   adb logcat -c
   # 启动 Songloft 等它卡住
   adb logcat -d > launch.txt
   ```
   用记事本搜：`FATAL` / `ANR` / `flutter` / `Songloft` / `ConnectException` / `timeout`。

2. **`NoSuchMethodError: SharedPreferencesApiRegistrar.getAll(...Continuation)`** ← **已定位并修复**
   现象（实测抓到的完整链路）：
   ```
   I flutter : [Main] Token 预加载失败: PlatformException(... getAll(...) ...)
   I flutter : [Main] 🚀 开始初始化 AudioService...   ← 能继续，不崩
   I flutter : [PlatformError] 同样的 getAll 报错
   （之后 App 完全静默，Activity 虽已 Fully drawn，但一直停在「正在启动中」）
   ```
   含义：所有 SharedPreferences 读写全废 → App 读不到保存的 token / 服务器配置 → 卡在加载页
   （是**卡住**，不是崩溃，所以 tombstone 里抓不到 App 的崩溃栈）。

   **根因（已确证）：`shared_preferences_android` 2.5.0 的上游破坏性改动。**
   该版本发布于 **2026-10-17**，changelog 只有一行：
   > Updates internal implementation to use Pigeon JNI.

   即内部实现从传统 Pigeon `BasicMessageChannel` 换成了 **jnigen 直接 JNI 调用**。
   迁移后 Dart 侧会去调原生 `SharedPreferencesApiRegistrar.getAll(String, List, Continuation)`，
   APK 内该签名一旦不可用就抛 `NoSuchMethodError`。
   而 `shared_preferences` 2.5.6 对它的约束是 `^2.3.4`，**pub 默认解析到最新的 2.5.0，等于自动踩坑**。

   **修复：钉回 2.4.28**（最后一个传统 Pigeon 通道实现版本，在 `^2.3.4` 约束之内）。
   - 由 `patch/pin_shared_prefs.py` 在 CI 时把
     `dependency_overrides: { shared_preferences_android: 2.4.28 }` 注入 `pubspec.yaml`
     —— 不直接改仓库里的 pubspec（sync-upstream 会覆盖）；`dependency_overrides` 优先级最高，
     能盖住上游既有的 override。脚本幂等，四种 pubspec 形态均已实测通过。
   - ⚠️ **反面教训：不要删 `pubspec.lock` 去「强制重新解析」**——那只会让 pub 解析到**最新**
     （含坏的 2.5.0），反而必踩坑。本仓库早期的错误做法已撤销。
   - ⚠️ `cache: false` + `flutter clean` 同样**修不掉它**（clean 不清 pub-cache、不改 lock）。
     这两项作为卫生习惯保留，但别指望它们解决问题。
   - 验证：构建日志里那段
     `=== shared_preferences 系列实际解析版本 ===` 必须显示 **`2.4.28`**（不是 2.5.0）才算生效。
   - 若某天上游 2.5.x 修好了 JNI 路径想升级：删掉注入即可，但同时要确认
     `android/app/build.gradle(.kts)` 里 `minifyEnabled` 为 `false`——
     R8 会把「只有 Dart 通过 JNI 调用」的 Kotlin 方法当成无用代码剥离，这是 JNI 的经典陷阱
     （CI 已打印该配置值，便于核对）。

3. **`native_contract.json` 缺失**：App 启动读该资产，缺失会卡死。本 fork 已保证该资产必生成
   （脚本失败也回退占位），正常不会出现。

4. **网络/登录**：流媒体 App 的「正在启动中」常是在等后端握手。请确认车机有外网、账号能登录；
   若纯离线环境，加载页可能一直转（上游行为，非 bug）。

5. **⚠️ 抓日志的两个大坑（务必先看这条，别被假崩溃带偏）**
   - **坑一：别在模拟器里用 `grep` 过滤 logcat。** 模拟器的 `toybox grep` 自身会 SIGSEGV 崩溃
     （tombstone 里 `name: grep`、栈在 `toybox do_grep/regexec`），导致日志末尾出现
     `Segmentation fault … 返回码 139`，**这是抓日志的工具崩了，不是 App 崩了**。
     正确做法：**在电脑上抓取完整日志、再在电脑上筛选**——
     ```bash
     adb logcat -c
     # 启动 Songloft，等它卡住（30 秒以上）
     adb logcat -d > launch.txt     # 不加任何管道/过滤
     # 然后在电脑上用记事本或 grep 搜 FATAL / flutter / Songloft / ConnectException / timeout
     ```
   - **坑二：`tombstone` 要先确认是不是 App 的。** 模拟器/系统里任何进程崩溃都会留一个 tombstone，
     拉之前先看它的 `Cmdline` / `name` 字段，必须是 App 进程才算数：
     ```bash
     adb shell ls -lat /data/tombstones/          # 取时间最新的
     adb shell head -20 /data/tombstones/tombstone_XX   # 确认 name/Cmdline 是 com.songloft...
     adb pull /data/tombstones/tombstone_XX
     ```
   - 同理，判断崩溃归属时看栈里 `.so`：
     `libflutter` / `impeller` / `vk*` / `libGLESv2` → Flutter 渲染；
     `libmpv` / `libav*` / `libmedia_kit` → 某原生插件；
     栈全在 `toybox` / `grep` 里 → 与我方 App 无关，忽略。

6. **⚠️ 必须按环境装对包：车机装 arm64，x86_64 模拟器装 x86_64**
   本 fork 出两个包：`arm64-v8a`（车机，ARM SoC）与 `x86_64`（PC 模拟器）。
   **x86_64 的 PC 模拟器（如雷电/LDPlayer，tombstone 里会显示 `ABI: 'x86_64'`）如果装了 arm64 包，
   就变成靠 ARM 二进制翻译（houdini）运行**，Flutter 引擎这种重度原生代码在翻译层下极易出现
   原生崩溃、卡在启动页、性能异常——**这类结果不能当作"App 有问题"的证据**。
   - 判断你当前环境的 ABI：`adb shell getprop ro.product.cpu.abi`
     （输出 `arm64-v8a` = 真机/ARM 环境；`x86_64` = PC 模拟器）。
   - 实测经验：在 x86_64 模拟器上装 arm64 包，会出现 `Segmentation fault`、卡启动页等假故障；
     换成 x86_64 包后这些现象应消失。若换包后仍有问题，才是真的 App 层问题。

> 方控补丁自身日志标签为 `Songloft-Leapmotor`；出现 `方控初始化入口（ContentProvider）已触发`
> 即表示补丁正常，卡死与方控无关。
