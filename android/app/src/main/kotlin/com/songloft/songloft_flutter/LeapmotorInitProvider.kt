package com.songloft.songloft_flutter

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * 零跑 2024+ 方控「自动初始化」入口（无需改动上游 SongloftApplication）。
 *
 * 原理：在 AndroidManifest 中声明一个 exported=false 的 <provider> 后，系统会在 App 启动时
 * （甚至早于 Application.onCreate）自动实例化本 ContentProvider 并回调 onCreate()。
 * 借此即可在完全不碰上游 Application 类的前提下挂接方控监听。
 *
 * 这与 Firebase / WorkManager / LeakCanary 的「ContentProvider 自动初始化」是同一套路，
 * 是 Android 上不侵入 Application 的标准做法。
 *
 * 收益（直接回应「上游改了 SongloftApplication 会不会影响使用」的担忧）：
 *   - 上游哪怕把 SongloftApplication 改名、改包名、增删 onCreate() 里的初始化逻辑，
 *     都和本方控补丁完全解耦，不会再出现「整文件覆盖 SongloftApplication.kt 导致
 *     丢失上游改动 / 编译失败」的问题。
 *   - 方控初始化失败也绝不抛到主流程，从设计上杜绝「装了补丁后 App 打不开（不能进入）」。
 */
internal class LeapmotorInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        try {
            val ctx = context ?: return false
            // ContentProvider.onCreate() 运行在主线程、且早于应用任何界面（包括 Splash/"正在启动中"页）。
            // 为杜绝「方控初始化万一阻塞主线程 → Splash 卡死」的风险，挂接动作放到后台线程执行。
            // （当前 attach 本身只做读 SharedPreferences + 注册广播，本就很快；此处只是兜底。）
            Thread {
                try {
                    Leapmotor24Wheel.attach(ctx)
                } catch (t: Throwable) {
                    Log.e("Songloft-Leapmotor", "方控初始化失败（已忽略，不影响主程序启动）", t)
                }
            }.also { it.name = "leapmotor-init"; it.start() }
            Log.i("Songloft-Leapmotor", "方控初始化入口（ContentProvider）已触发，挂接将在后台线程执行")
        } catch (t: Throwable) {
            Log.e("Songloft-Leapmotor", "方控初始化入口异常（已忽略，不影响主程序启动）", t)
        }
        // 返回 false：本 Provider 不对外提供数据，不需要被系统保留实例。
        return false
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}
