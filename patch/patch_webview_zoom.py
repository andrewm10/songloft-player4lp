#!/usr/bin/env python3
"""构建期注入：放大插件 WebView 里的文字（高分屏车机插件字太小）。

为什么需要它
------------
插件界面是 InAppWebView 里的 HTML/CSS，字号由插件自己的 CSS 写死（后端
internal/jsplugin/assets/common.css），而 Flutter 侧的「设置 → 字体大小」
（TextScaler，0.85~1.3）只作用于 Flutter 的 Text，对 WebView 完全无效。
上游又把 supportZoom 关掉了，用户连双指放大都用不了，于是 2560×1440 这类
高分屏车机上插件文字明显偏小、且无法自救。

做什么
------
不改上游源码（直接改 lib/ 会让每次同步上游都冲突），构建时对
lib/features/home/presentation/render/plugin_render_surface_webview.dart 的
initialSettings 块做文本注入：

    textZoom: 130            // Android WebView 文字缩放百分比（只放大文字，不动布局）
    builtInZoomControls: false
    displayZoomControls: false
    supportZoom: false -> true   // 允许双指放大，保底自救

幂等与失败策略
--------------
- 块里已有 textZoom（上游自己加了）：跳过并提示，保持上游取值；
- 找不到目标块 / 命中多于一处：::error:: 退出，让构建失败。
  绝不静默放行——否则「插件字小」会悄无声息地回来，谁也不知道。

环境变量
--------
PLUGIN_TEXT_ZOOM    文字缩放百分比，默认 130（允许 100–300）
PLUGIN_PINCH_ZOOM   是否允许双指放大，默认 1；设 0 则保持上游 supportZoom: false

用法：python3 patch/patch_webview_zoom.py   （在仓库根目录执行）
"""
import os
import re
import sys
from pathlib import Path

TARGET = Path(
    "lib/features/home/presentation/render/plugin_render_surface_webview.dart"
)
ANCHOR = "initialSettings: InAppWebViewSettings("


def fail(msg):
    print("::error::" + msg)
    sys.exit(1)


def env_int(name, default, lo, hi):
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        fail("%s 不是整数：%r" % (name, raw))
    if not (lo <= value <= hi):
        fail("%s 超出允许区间 %d–%d：%d" % (name, lo, hi, value))
    return value


def indent_of(line):
    return len(line) - len(line.lstrip(" "))


def find_block(lines):
    """返回 (start, end)：start 是 initialSettings 那一行，end 是闭合行（含）。"""
    hits = [i for i, line in enumerate(lines) if ANCHOR in line]
    if len(hits) != 1:
        fail(
            "期望在 %s 中恰好找到 1 处 %r，实际 %d 处。"
            "上游可能已重构，请人工确认后更新本脚本。" % (TARGET, ANCHOR, len(hits))
        )
    start = hits[0]
    header_indent = indent_of(lines[start])
    for j in range(start + 1, len(lines)):
        stripped = lines[j].strip()
        if not stripped:
            continue
        if indent_of(lines[j]) <= header_indent and stripped.startswith(")"):
            return start, j
    fail("未能定位 initialSettings 块的结尾，请人工确认文件结构。")


def main():
    zoom = env_int("PLUGIN_TEXT_ZOOM", 130, 100, 300)
    pinch = os.environ.get("PLUGIN_PINCH_ZOOM", "1").strip().lower() not in (
        "0",
        "false",
        "no",
        "off",
    )

    if not TARGET.is_file():
        fail(
            "未找到 %s。请在 Flutter 客户端仓库（songloft-org/songloft-player）"
            "根目录执行本脚本。" % TARGET
        )

    raw = TARGET.read_bytes().decode("utf-8")
    # 保留原文件换行风格：CI 上通常是 LF，但本地 Windows 检出可能是 CRLF，
    # 不做这层处理会把整份文件「全量改写」，diff 里看不出真正的改动。
    newline = "\r\n" if "\r\n" in raw else "\n"
    lines = raw.replace("\r\n", "\n").split("\n")
    start, end = find_block(lines)

    block = lines[start : end + 1]
    if any(re.match(r"^\s*textZoom\s*:", line) for line in block):
        print("上游已自带 textZoom，跳过注入（保持上游取值）。")
        return

    prop_indent = None
    for line in lines[start + 1 : end]:
        if line.strip():
            prop_indent = indent_of(line)
            break
    if prop_indent is None:
        prop_indent = indent_of(lines[start]) + 2
    pad = " " * prop_indent

    injected = [
        pad + "// 构建期注入：高分屏车机上插件字号偏小，放大到 %d%%" % zoom,
        pad + "textZoom: %d," % zoom,
    ]
    if pinch:
        injected += [
            pad + "// 不显示缩放按钮，但保留双指放大——文字仍偏小时可手动放大",
            pad + "builtInZoomControls: false,",
            pad + "displayZoomControls: false,",
        ]

    flipped = []
    if pinch:
        for j in range(start + 1, end):
            if re.match(r"^\s*supportZoom\s*:\s*false\s*,\s*$", lines[j]):
                lines[j] = re.sub(r"false", "true", lines[j], count=1)
                flipped.append("supportZoom: false -> true")

    lines[start + 1 : start + 1] = injected
    TARGET.write_bytes(newline.join(lines).encode("utf-8"))

    print(
        "已注入插件 WebView 文字缩放：textZoom=%d，双指放大=%s"
        % (zoom, "开" if pinch else "关（保持上游 supportZoom: false）")
    )
    for item in flipped:
        print("  改动：%s" % item)

    print("=== 改动后的 initialSettings 块 ===")
    new_lines = TARGET.read_text(encoding="utf-8").split("\n")
    new_start, new_end = find_block(new_lines)
    for line in new_lines[new_start : new_end + 1]:
        print("  " + line)


if __name__ == "__main__":
    main()
