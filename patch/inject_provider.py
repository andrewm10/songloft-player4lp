#!/usr/bin/env python3
"""幂等地把零跑方控所需组件注入 android/app/src/main/AndroidManifest.xml。

注入两项（各自幂等，可重复运行）：
  1. LeapmotorInitProvider：ContentProvider 自动初始化入口（App 启动时挂接方控监听）。
  2. Leapmotor24ConfigReceiver：接收 ADB 配置广播，用于实车切换方控来源模式
     （auto/factory/android，规避「按一下跳两首」）。

- 已存在对应声明则跳过。
- 找不到 <application> 标签则报错退出，避免静默漏掉方控，便于上游结构大改时第一时间发现。

用法（在仓库根目录执行）：
    python3 patch/inject_provider.py
"""
import re
import sys
from pathlib import Path

MANIFEST = Path("android/app/src/main/AndroidManifest.xml")

PROVIDER_AUTHORITY = "com.songloft.songloft_flutter.leapmotor.init"
PROVIDER_MARKER = 'android:name=".LeapmotorInitProvider"'
PROVIDER_BLOCK = f'''        <provider
            android:name=".LeapmotorInitProvider"
            android:authorities="{PROVIDER_AUTHORITY}"
            android:exported="false"
            android:multiprocess="true" />
'''

CONFIG_ACTION = "com.songloft.songloft_flutter.leapmotor24.CONFIGURE"
RECEIVER_MARKER = 'android:name=".Leapmotor24ConfigReceiver"'
RECEIVER_BLOCK = f'''        <receiver
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

    if PROVIDER_MARKER not in text:
        segments.append(PROVIDER_BLOCK)
        injected.append("LeapmotorInitProvider")
    else:
        print("已存在 LeapmotorInitProvider 声明，跳过（幂等）。", flush=True)

    if RECEIVER_MARKER not in text:
        segments.append(RECEIVER_BLOCK)
        injected.append("Leapmotor24ConfigReceiver")
    else:
        print("已存在 Leapmotor24ConfigReceiver 声明，跳过（幂等）。", flush=True)

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
