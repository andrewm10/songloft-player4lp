package com.songloft.songloft_flutter

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/**
 * 零跑 2024–2027 款车机（C10 及以后）方向盘媒体键（方控）适配。
 *
 * 协议来源：Wh1t3co1azZ/DiPlay-Leapmotor 的 leapmotor-24-27 分支 Leapmotor24WheelKeys.kt。
 *   广播 action : com.leapmotor.steerwheelcontrol.media
 *   int extra   : "action"
 *       1 -> 上一首 (KEYCODE_MEDIA_PREVIOUS)
 *       4 -> 下一首 (KEYCODE_MEDIA_NEXT)
 *       7 -> 播放/暂停 (KEYCODE_MEDIA_PLAY_PAUSE)
 *   其它值（音量、电话等）归车机，忽略。
 *
 * 与 2021–2023 那套（CAN→ICU→广播、leapmotor.cantoapp.update、int extra 1010105）完全不是一套，
 * 切勿混用（LEAPMOTOR24.md 明确两代不要混用）。
 *
 * 适配方式：监听该广播 -> 翻译成标准 ACTION_MEDIA_BUTTON 键事件 -> 定向发给 Songloft 使用的
 * audio_service MediaButtonReceiver，由它驱动播放控制。Songloft 的 AndroidManifest 已声明
 * com.ryanheise.audioservice.MediaButtonReceiver，无需改播放逻辑。
 *
 * 关键：必须用 RECEIVER_EXPORTED 注册——该广播来自车机自身控制服务（非特权 UID），
 * 用 RECEIVER_NOT_EXPORTED 会被系统过滤、收不到（这也是 24-27 分支与 21-23 的差异之一）。
 *
 * 接入：由 LeapmotorInitProvider（ContentProvider 自动初始化）以崩溃安全方式调用
 * Leapmotor24Wheel.attach(context)，无需改动上游 SongloftApplication（与上游 Application 改动彻底解耦）。
 *
 * 规避「按一下跳两首」：2024+ 同一次按键可能产生「车机广播 + 原生媒体键」两条事件。本架构下原生媒体键
 * 由 audio_service 直接处理（无法被本补丁拦截），因此交叉去重改为「只保留一条通道」——通过 ADB 切换
 * 方控来源模式（auto / factory / android，见上方常量）。默认 auto 转发车机广播；若实车出现重复触发，
 * 用 `adb shell am broadcast -a com.songloft.songloft_flutter.leapmotor24.CONFIGURE --es wheel_source android`
 * 切到 android 模式（忽略车机广播、只认原生媒体键）即可消除重复。模式持久化在 SharedPreferences，重启不丢。
 */
internal object Leapmotor24Wheel {
    private const val TAG = "Songloft-Leapmotor"
    const val ACTION_MEDIA = "com.leapmotor.steerwheelcontrol.media"
    private const val EXTRA_ACTION = "action"

    // 2024+ 同一次物理按键可能经两条路径到达（车机广播 + 普通 Android 媒体键），
    // 去重窗口比 21–23 的 250ms 更宽。
    private const val DUPLICATE_WINDOW_MILLIS = 350L

    private const val AUDIO_SERVICE_PKG = "com.ryanheise.audioservice"
    private const val AUDIO_SERVICE_RECEIVER = "com.ryanheise.audioservice.MediaButtonReceiver"

    // ===== 方控来源模式（ADB 可切换，规避「按一下跳两首」）=====
    // 2024+ 同一次物理按键可能同时产生「车机广播」与「原生 Android 媒体键」两条事件。
    // 本架构下原生媒体键由 audio_service 直接处理（无法被本补丁拦截），故交叉去重靠「只保留一条通道」实现：
    //   auto    (默认)：转发车机广播（350ms 窗口去重同通道重复）；绝大多数车型开箱即用。
    //   factory        ：等同 auto（仅转发车机广播）；留给「只发广播」的车型。
    //   android        ：忽略车机广播，完全交由原生媒体键处理；若你的车出现「按一下跳两首」，切到此模式即可。
    const val ACTION_CONFIGURE = "com.songloft.songloft_flutter.leapmotor24.CONFIGURE"
    const val EXTRA_WHEEL_SOURCE = "wheel_source"
    const val PREFS_NAME = "songloft_leapmotor24"
    const val KEY_WHEEL_SOURCE = "wheel_source"
    const val MODE_AUTO = "auto"
    const val MODE_FACTORY = "factory"
    const val MODE_ANDROID = "android"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    @Volatile private var receiver: BroadcastReceiver? = null
    private var lastKeyCode = 0
    private var lastUptimeMillis = 0L
    @Volatile private var mode: String = MODE_AUTO

    @Synchronized
    fun attach(context: Context) {
        if (receiver != null) return
        appContext = context.applicationContext
        initMode(context.applicationContext)
        val created = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val pending = goAsync()
                mainHandler.post {
                    try {
                        handle(intent)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
        // 必须用 EXPORTED：广播来自车机控制服务（非特权 UID），NOT_EXPORTED 会被系统过滤。
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                created,
                IntentFilter(ACTION_MEDIA),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        receiver = created.takeIf { registered }
        Log.i(TAG, "2024+ 方控监听已注册 action=$ACTION_MEDIA registered=$registered mode=${currentMode()}")
    }

    @Synchronized
    fun detach() {
        val ctx = appContext
        val existing = receiver
        if (ctx != null && existing != null) runCatching { ctx.unregisterReceiver(existing) }
        receiver = null
        appContext = null
        Log.i(TAG, "2024+ 方控监听已卸载")
    }

    fun currentMode(): String = mode

    fun initMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_WHEEL_SOURCE, MODE_AUTO) ?: MODE_AUTO
        mode = if (saved in setOf(MODE_AUTO, MODE_FACTORY, MODE_ANDROID)) saved else MODE_AUTO
    }

    fun setMode(context: Context, newMode: String) {
        val normalized = newMode.lowercase().trim()
        if (normalized !in setOf(MODE_AUTO, MODE_FACTORY, MODE_ANDROID)) {
            Log.w(TAG, "方控配置：非法模式 $newMode（可选 auto/factory/android），忽略")
            return
        }
        mode = normalized
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_WHEEL_SOURCE, normalized).apply()
        }
        Log.i(TAG, "方控来源模式已切换为：$normalized（已持久化，重启后同样生效）")
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != ACTION_MEDIA) return
        val raw = runCatching { intent.getIntExtra(EXTRA_ACTION, -1) }.getOrDefault(-1)
        if (mode == MODE_ANDROID) {
            Log.i(TAG, "方控忽略（android 模式：交由原生媒体键处理，不转发车机广播）action=$raw")
            return
        }
        val keyCode = mapAction(raw)
        if (keyCode == null) {
            Log.i(TAG, "方控忽略（非媒体键）action=$raw")
            return
        }
        if (!claim(keyCode)) return
        sendMediaButton(keyCode)
        Log.i(TAG, "方控转发 action=$raw -> keyCode=$keyCode")
    }

    /** 1->上一首 4->下一首 7->播放/暂停；其它归车机。 */
    private fun mapAction(action: Int): Int? = when (action) {
        1 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        4 -> KeyEvent.KEYCODE_MEDIA_NEXT
        7 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        else -> null
    }

    /** 350ms 窗口内同一 keyCode 只放行一次，避免同一次按键被重复投递。 */
    private fun claim(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        if (keyCode == lastKeyCode && now - lastUptimeMillis < DUPLICATE_WINDOW_MILLIS) {
            Log.i(TAG, "方控去重 keyCode=$keyCode")
            return false
        }
        lastKeyCode = keyCode
        lastUptimeMillis = now
        return true
    }

    /** 构造 ACTION_MEDIA_BUTTON 广播，定向发给 audio_service 的 MediaButtonReceiver。 */
    private fun sendMediaButton(keyCode: Int) {
        val ctx = appContext ?: return
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(AUDIO_SERVICE_PKG, AUDIO_SERVICE_RECEIVER)
            putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
        }
        runCatching { ctx.sendBroadcast(intent) }
    }
}
