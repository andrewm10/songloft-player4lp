package com.songloft.songloft_flutter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 方控静态接收器（文档第 3 步、对应 LeaPlay 的 SteeringWheelReceiver）。
 *
 * 职责只有一个：把广播原样转交给 [Leapmotor24Wheel.dispatch]，与动态注册的接收器
 * **共用同一份去重状态**——这样万一车机固件同时把广播送到静态与动态两条路径，
 * 400ms 同码去重依然能拦住第二次，不会「跳两首」。
 *
 * 说明（文档第 1 节已注明）：Android 8+ 起自定义 action 基本送不到 manifest 声明的接收器，
 * 所以真正干活的是 [Leapmotor24Wheel.register] 里的动态注册；本类是按文档要求保留的
 * 兜底路径，部分厂商固件仍会投递，保留它无害且能覆盖这类机型。
 */
class Leapmotor24SteeringReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Leapmotor24Wheel.ACTION_MEDIA) return
        Log.i("Songloft-Leapmotor", "方控广播（静态接收器路径）: action=${intent.action}")
        Leapmotor24Wheel.dispatch(context.applicationContext, intent)
    }
}
