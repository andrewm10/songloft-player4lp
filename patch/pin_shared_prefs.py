"""把 shared_preferences_android 钉到 2.4.28（传统 Pigeon 通道实现），避免启动时 NoSuchMethodError。

背景
----
shared_preferences_android **2.5.0**（2026-10-17 发布）把内部实现从 Pigeon
BasicMessageChannel 换成了 **Pigeon JNI**（jnigen 直接 JNI 调用）。迁移后 Dart 侧会去调
原生 Kotlin 的：

    SharedPreferencesApiRegistrar.getAll(String, List, Continuation)

一旦该签名在 APK 内不可用，就会抛：

    java.lang.NoSuchMethodError: ... SharedPreferencesApiRegistrar.getAll(...Continuation)

后果是**所有偏好读写全部失效** → App 读不到已保存的 token / 服务器配置 → 卡在「正在启动中」
（是卡住，不是崩溃，所以 tombstone 里看不到 App 的崩溃栈）。

而 shared_preferences 2.5.6 对它的约束是 ^2.3.4，pub 默认解析到最新的 2.5.0，
等于自动踩进这个新坑。钉回 **2.4.28** 即回到自洽的传统 Pigeon 通道实现，
且 2.4.28 在 ^2.3.4 约束之内，不需要动上游其它依赖。

为什么不直接改仓库里的 pubspec.yaml
------------------------------------
本 fork 的 pubspec.yaml 来自上游（sync-upstream 会覆盖），直接改会在同步时被冲掉。
改为在 CI 里用 dependency_overrides 注入，**对上游改动免疫**，且上游若已钉了别的版本，
本脚本会把它改写成 2.4.28（dependency_overrides 优先级最高，一定生效）。

实现说明
--------
纯文本处理，不依赖 PyYAML（CI 环境不保证装了它）。
- 已有 dependency_overrides 段 → 在其中替换/插入该条目
- 没有该段 → 在文件末尾追加一段
- 已经是 2.4.28 → 不动（幂等，可重复运行）

用法（在仓库根目录执行）：
    python3 patch/pin_shared_prefs.py
"""

from __future__ import annotations

import io
import os
import re
import sys

PACKAGE = "shared_preferences_android"
# 2.5.0 = Pigeon JNI（会崩）；2.4.28 = 最后一个传统 Pigeon 通道实现版本。
PINNED_VERSION = "2.4.28"
OVERRIDES_KEY = "dependency_overrides:"

# 匹配 dependency_overrides 段内的条目行，保留原缩进（通常 2 空格）
ENTRY_RE = re.compile(r"^(\s+)" + re.escape(PACKAGE) + r":\s*(\S.*)$")
# 顶层键（顶格、非注释），用于判断 dependency_overrides 段在哪里结束
TOP_KEY_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*:")


def find_overrides_block(lines: list[str]) -> int | None:
    """返回顶层 dependency_overrides: 所在行号，找不到返回 None。"""
    for i, line in enumerate(lines):
        if line.strip() == OVERRIDES_KEY:
            return i
    return None


def find_block_end(lines: list[str], start: int) -> int:
    """返回该段结束后的行号（不含）：遇到下一个顶层 key 或文件末尾。"""
    for i in range(start + 1, len(lines)):
        if TOP_KEY_RE.match(lines[i]):
            return i
    return len(lines)


def pin(path: str) -> int:
    with io.open(path, encoding="utf-8") as f:
        text = f.read()
    lines = text.split("\n")

    entry = "  {}: {}".format(PACKAGE, PINNED_VERSION)
    ov_idx = find_overrides_block(lines)

    if ov_idx is None:
        # 没有 dependency_overrides 段：整段追加到末尾
        block = [OVERRIDES_KEY, entry]
        # 去掉结尾多余空行，保证追加后格式干净
        while lines and lines[-1].strip() == "":
            lines.pop()
        lines.extend(block + [""])
        _write(path, lines)
        print("已新建 dependency_overrides 段并钉住 {} -> {}".format(PACKAGE, PINNED_VERSION), flush=True)
        return 0

    end = find_block_end(lines, ov_idx)

    # 段内是否已有该条目
    for i in range(ov_idx + 1, end):
        m = ENTRY_RE.match(lines[i])
        if m:
            current = m.group(2).strip().strip('"\'')
            if current == PINNED_VERSION:
                print("{} 已钉在 {}，跳过（幂等）。".format(PACKAGE, PINNED_VERSION), flush=True)
                return 0
            lines[i] = "{}{}: {}".format(m.group(1), PACKAGE, PINNED_VERSION)
            _write(path, lines)
            print(
                "已将 {} 从 {} 改写为 {}（覆盖上游既有 override）。".format(PACKAGE, current, PINNED_VERSION),
                flush=True,
            )
            return 0

    # 段内没有该条目：插入到段首
    lines.insert(ov_idx + 1, entry)
    _write(path, lines)
    print("已在既有 dependency_overrides 段内钉住 {} -> {}".format(PACKAGE, PINNED_VERSION), flush=True)
    return 0


def _write(path: str, lines: list[str]) -> None:
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "pubspec.yaml"
    if not os.path.isfile(path):
        print("::error::未找到 {}".format(path), flush=True)
        return 1
    try:
        return pin(path)
    except Exception as exc:  # 不让工具失败拖垮整个构建判断
        print("::error::钉住 {} 失败：{}".format(PACKAGE, exc), flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
