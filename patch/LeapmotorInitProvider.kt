package com.songloft.songloft_flutter

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * 方控「进程级」初始化入口——对应文档第 1 步：
 *   在 Application.onCreate 里注册（进程级，覆盖所有入口）
 *       SteeringWheelControl.register(this)
 *       CarMediaSourceClaim.start(this)
 *
 * 本补丁不直接改上游 SongloftApplication（那样每次同步上游都可能冲突/丢改动），
 * 改用 Android 标准的 ContentProvider 自动初始化（与 Firebase / WorkManager 同一套路）：
 * 系统在创建 Application **之前**就会实例化本 Provider 并回调 onCreate()，
 * 因此覆盖「任何路径启动 App」的时机要求，且初始化失败绝不会波及主流程。
 *
 * 注册顺序与文档一致：先方控接收器，再媒体源声明。
 *   - 方控接收器：同步注册（毫秒级，必须尽早，避免启动瞬间漏键）
 *   - 媒体源声明：放后台线程启动（含 1 秒心跳，不参与首次按键的关键路径）
 */
internal class LeapmotorInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        try {
            val ctx = context ?: return true

            // ① 方控接收器（同步；失败也不抛，方控不可用但 App 照常启动）
            try {
                Leapmotor24Wheel.register(ctx)
            } catch (t: Throwable) {
                Log.e(TAG, "方控接收器初始化失败（已忽略，不影响主程序启动）", t)
            }

            // ② 媒体源声明（后台线程；含心跳与 top activity 监听）
            Thread {
                try {
                    LeapmotorMediaClaim.start(ctx)
                } catch (t: Throwable) {
                    Log.e(TAG, "媒体源声明初始化失败（已忽略，不影响主程序启动）", t)
                }
            }.also { it.name = "leapmotor-claim"; it.start() }

            Log.i(TAG, "方控初始化入口（ContentProvider）已触发：接收器已注册，媒体源声明已启动")
        } catch (t: Throwable) {
            Log.e(TAG, "方控初始化入口异常（已忽略，不影响主程序启动）", t)
        }
        // 返回 true：本 Provider 不对外提供数据，但返回 true 是 ContentProvider 的标准语义
        // （返回 false 会被系统视为「provider 加载失败」并告警）。
        return true
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

    private companion object {
        const val TAG = "Songloft-Leapmotor"
    }
}
