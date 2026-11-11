#!/usr/bin/env python3
"""幂等地把零跑方控所需组件注入 android/app/src/main/AndroidManifest.xml。

严格对齐《方向盘方控方案》第 6 部分「移植到别的 App 的完整步骤」：

  第 1 步  进程级初始化        -> LeapmotorInitProvider（ContentProvider，早于 Application.onCreate，
                                  等价于文档的「在 Application.onCreate 里注册」，但不改上游 Application）
  第 3 步  manifest 注册静态接收器 -> Leapmotor24SteeringReceiver（exported=true + intent-filter）
  另加     配置入口            -> Leapmotor24ConfigReceiver（ADB 广播改模式/映射/抢源开关）

注入三项（各自幂等，可重复运行）：
  1. LeapmotorInitProvider：自动初始化入口，内部依次注册方控接收器 + 启动媒体源声明。
  2. Leapmotor24SteeringReceiver：静态接收器（文档第 3 步的兜底路径）。
  3. Leapmotor24ConfigReceiver：接收 ADB 配置广播。

- 已存在对应声明则跳过。
- 找不到 <application> 标签则报错退出，避免静默漏掉方控，便于上游结构大改时第一时间发现。

用法（在仓库根目录执行）：
    python3 patch/inject_provider.py
"""
import re
import sys
from pathlib import Path

MANIFEST = Path("android/app/src/main/AndroidManifest.xml")

STEER_ACTION = "com.leapmotor.steerwheelcontrol.media"
CONFIG_ACTION = "com.songloft.songloft_flutter.leapmotor24.CONFIGURE"
PROVIDER_AUTHORITY = "com.songloft.songloft_flutter.leapmotor.init"

PROVIDER_MARKER = 'android:name=".LeapmotorInitProvider"'
PROVIDER_BLOCK = f'''        <!-- 方控初始化入口（ContentProvider 自动初始化，等价于 Application.onCreate 注册） -->
        <provider
            android:name=".LeapmotorInitProvider"
            android:authorities="{PROVIDER_AUTHORITY}"
            android:exported="false"
            android:multiprocess="true" />
'''

STEERING_MARKER = 'android:name=".Leapmotor24SteeringReceiver"'
STEERING_BLOCK = f'''        <!-- 方控静态接收器（文档第 3 步；Android 8+ 自定义 action 主要靠动态注册，此处为兜底） -->
        <receiver
            android:name=".Leapmotor24SteeringReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="{STEER_ACTION}" />
            </intent-filter>
        </receiver>
'''

CONFIG_MARKER = 'android:name=".Leapmotor24ConfigReceiver"'
CONFIG_BLOCK = f'''        <!-- 方控配置入口（ADB 广播：切换来源模式 / 改写键码映射 / 开关媒体源声明） -->
        <receiver
            android:name=".Leapmotor24ConfigReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="{CONFIG_ACTION}" />
            </intent-filter>
        </receiver>
'''


def main() -> int:
    if not MANIFEST.exists():
        print(
            "::error::未找到 android/app/src/main/AndroidManifest.xml，请确认仓库结构未变。",
            flush=True,
        )
        return 1

    text = MANIFEST.read_text(encoding="utf-8")

    # 在第一个 <application ...> 开始标签之后插入（确保位于 <application> 内部）。
    match = re.search(r"<application\b[^>]*>", text)
    if not match:
        print(
            "::error::未在 AndroidManifest.xml 中找到 <application> 标签，无法注入；"
            "上游 manifest 结构可能已变更，请人工排查。",
            flush=True,
        )
        return 1

    insert_at = match.end()
    segments: list[str] = []
    injected: list[str] = []

    checks = [
        (PROVIDER_MARKER, PROVIDER_BLOCK, "LeapmotorInitProvider"),
        (STEERING_MARKER, STEERING_BLOCK, "Leapmotor24SteeringReceiver"),
        (CONFIG_MARKER, CONFIG_BLOCK, "Leapmotor24ConfigReceiver"),
    ]
    for marker, block, name in checks:
        if marker not in text:
            segments.append(block)
            injected.append(name)
        else:
            print(f"已存在 {name} 声明，跳过（幂等）。", flush=True)

    if not segments:
        print("方控组件声明均已存在，跳过注入（幂等）。", flush=True)
        return 0

    addition = "\n" + "".join(segments)
    new_text = text[:insert_at] + addition + text[insert_at:]
    MANIFEST.write_text(new_text, encoding="utf-8")
    print(f"已注入：{', '.join(injected)} 到 AndroidManifest.xml。", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
