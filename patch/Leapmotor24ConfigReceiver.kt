package com.songloft.songloft_flutter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 方控配置入口（ADB 触发），对应文档 §5「配置项远程可调，不必重出包」。
 *
 * 文档里的配置是三行属性（mediaSession / steerIndexPrev / steerIndexNext / steerIndexPlay），
 * 本 App 没有 /sdcard/cluster.properties 那套宿主，改用一条 ADB 广播等价实现，
 * 全部持久化在 SharedPreferences，重启不丢。
 *
 * 三条命令：
 *   1) 来源模式（应急用）——auto/factory 转发车机广播；android 只认原生媒体键
 *      adb shell am broadcast -a com.songloft.songloft_flutter.leapmotor24.CONFIGURE --es wheel_source auto
 *
 *   2) 键码映射（对应文档坑 #9：不同固件上/下一曲可能反了，现场试着修）
 *      adb shell am broadcast -a com.songloft.songloft_flutter.leapmotor24.CONFIGURE --es map "1=88,4=87,7=85"
 *      格式 action=keyCode，逗号分隔；87=下一曲 88=上一曲 85=播放暂停 126=播放 127=暂停
 *      改错不要紧：--es map "reset" 恢复默认。
 *
 *   3) 媒体源开关（对应文档 mediaSession=true/false）
 *      adb shell am broadcast -a com.songloft.songloft_flutter.leapmotor24.CONFIGURE --es media_claim off
 *
 *   不带任何参数即为查询：会打印当前模式、映射与抢源统计。
 */
class Leapmotor24ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Leapmotor24Wheel.ACTION_CONFIGURE) return
        val app = context.applicationContext

        val source = intent.getStringExtra(Leapmotor24Wheel.EXTRA_WHEEL_SOURCE)
        val mapSpec = intent.getStringExtra(Leapmotor24Wheel.EXTRA_KEY_MAP)
        val claim = intent.getStringExtra(EXTRA_MEDIA_CLAIM)

        if (source.isNullOrBlank() && mapSpec.isNullOrBlank() && claim.isNullOrBlank()) {
            Log.i(TAG, "方控配置查询：${Leapmotor24Wheel.describe()}；${LeapmotorMediaClaim.describe()}")
            return
        }

        if (!source.isNullOrBlank()) Leapmotor24Wheel.setMode(app, source)

        if (!mapSpec.isNullOrBlank()) {
            if (mapSpec.trim().equals("reset", ignoreCase = true)) {
                Leapmotor24Wheel.resetKeyMap(app)
            } else {
                Leapmotor24Wheel.setKeyMap(app, mapSpec)
            }
        }

        if (!claim.isNullOrBlank()) LeapmotorMediaClaim.setEnabled(app, claim)

        Log.i(TAG, "方控配置完成：${Leapmotor24Wheel.describe()}；${LeapmotorMediaClaim.describe()}")
    }

    private companion object {
        const val TAG = "Songloft-Leapmotor"
        const val EXTRA_MEDIA_CLAIM = "media_claim"
    }
}
