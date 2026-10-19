package com.songloft.songloft_flutter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 方控来源模式配置入口（ADB 触发），用于实车规避「按一下跳两首」。
 *
 * 对应 DiPlay-Leapmotor leapmotor-24-27 分支的 Leapmotor24ConfigReceiver：
 * 用一条 ADB 广播即可在车机上切换「车机广播 / 原生媒体键」来源（auto / factory / android）。
 * 模式持久化在 SharedPreferences，重启不丢；卸载重装回到 auto。
 *
 * 切换命令（车机开启 USB 调试后执行）：
 *   adb shell am broadcast -a com.songloft.songloft_flutter.leapmotor24.CONFIGURE --es wheel_source android
 *   （wheel_source 可选：auto 默认 / factory 只认车机广播 / android 只认原生媒体键）
 */
internal class Leapmotor24ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Leapmotor24Wheel.ACTION_CONFIGURE) return
        val src = intent.getStringExtra(Leapmotor24Wheel.EXTRA_WHEEL_SOURCE)
        if (src.isNullOrBlank()) {
            Log.i(
                "Songloft-Leapmotor",
                "方控配置：未提供 wheel_source，当前模式=${Leapmotor24Wheel.currentMode()}",
            )
            return
        }
        Leapmotor24Wheel.setMode(context.applicationContext, src)
    }
}
