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
 * 零跑 2024–2027 款车机（C10 及以后）方向盘媒体键（方控）——第一层：收按键。
 *
 * 严格按《方向盘方控方案（CarPlay / LeaPlay 实测版）》第 6 部分「移植到别的 App」实现：
 *   ① 进程级动态注册（文档红线 #1：不要搬出应用级初始化；本补丁用 ContentProvider 达到
 *      与 Application.onCreate 等价、且更早的时机，见 LeapmotorInitProvider）
 *   ② 同码 400ms 去重（文档实测参数，别改小；车机有两条取键路径会同时触发，不去重就是「跳两首」）
 *   ③ 会话未就绪时补发：每 400ms 重试，最多 20 次 / 8 秒
 *   ④ 按键事件「按下 + 抬起」成对发送（文档红线 #5 / 坑 #8）
 *   ⑤ 去重通过后立刻重推媒体源声明（文档 §4 数据流：抢归属要在执行动作之前）
 *
 * 协议（实机反推，非文档）：
 *   action : com.leapmotor.steerwheelcontrol.media
 *   extra  : "action" = Int   1 = 上一曲 / 4 = 下一曲 / 7 = 播放暂停
 *   其它值（音量、电话等）归车机，忽略。
 *
 * 第三层「送达」在本 App 的等价实现（文档第 4 步：把发 HID 报文换成调自己的播放器）：
 *   构造标准 ACTION_MEDIA_BUTTON 键事件，定向投递给 audio_service 的 MediaButtonReceiver，
 *   由它驱动 Flutter 侧的播放控制。不碰上游任何 Dart / Application 代码。
 *
 * 日志关键字（对应文档第 7 节）：
 *   方控接收器已注册（com.leapmotor.steerwheelcontrol.media）   ← ① 注册成功
 *   方控广播: action=4                                          ← ② 收到真实按键值
 *   方控 → 下一曲（keyCode=87，按下+抬起成对）                   ← ③ 动作执行
 *   媒体源：广播声明sent=N skipped=M 自己在前台=true top=...      ← ④ 抢源状态
 */
internal object Leapmotor24Wheel {
    private const val TAG = "Songloft-Leapmotor"

    const val ACTION_MEDIA = "com.leapmotor.steerwheelcontrol.media"
    private const val EXTRA_ACTION = "action"

    /** 文档实测：车机有两条取键路径，同码 ≤400ms 视为同一次按键。 */
    private const val DUPLICATE_WINDOW_MILLIS = 400L

    /** 文档第 1 节「补发」：会话未就绪时 400ms × 20 次 ≈ 8 秒。 */
    private const val RETRY_INTERVAL_MILLIS = 400L
    private const val MAX_RETRY = 20

    private const val AUDIO_SERVICE_PKG = "com.ryanheise.audioservice"
    private const val AUDIO_SERVICE_RECEIVER = "com.ryanheise.audioservice.MediaButtonReceiver"

    // ===== 方控配置（ADB 可改，对应文档 §5「配置项远程可调，不必重出包」）=====
    const val ACTION_CONFIGURE = "com.songloft.songloft_flutter.leapmotor24.CONFIGURE"
    const val EXTRA_WHEEL_SOURCE = "wheel_source"
    const val EXTRA_KEY_MAP = "map"
    const val PREFS_NAME = "songloft_leapmotor24"
    const val KEY_WHEEL_SOURCE = "wheel_source"
    const val KEY_KEY_MAP = "key_map"

    // 来源模式：auto/factory 转发车机广播；android 只认原生媒体键（应急用）
    const val MODE_AUTO = "auto"
    const val MODE_FACTORY = "factory"
    const val MODE_ANDROID = "android"

    /**
     * 车机 action -> 标准媒体键。默认值来自实机反推；文档坑 #9 指出映射因固件而异，
     * 故支持 ADB 改写（见 setKeyMap），现场可试不同组合而不必重新打包。
     */
    private val DEFAULT_KEY_MAP = mapOf(
        1 to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        4 to KeyEvent.KEYCODE_MEDIA_NEXT,
        7 to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    @Volatile private var receiver: BroadcastReceiver? = null

    private var lastKeyCode = 0
    private var lastUptimeMillis = 0L

    @Volatile private var mode: String = MODE_AUTO
    @Volatile private var keyMap: Map<Int, Int> = DEFAULT_KEY_MAP

    // 补发状态（同一时刻只记一个待发按键，后按覆盖先按，避免堆积后连跳多首）
    @Volatile private var pendingKeyCode = 0
    @Volatile private var retryAttempt = 0

    /**
     * 进程级注册。由 LeapmotorInitProvider（ContentProvider，早于 Application.onCreate）调用，
     * 覆盖所有启动入口——文档坑 #1：注册写在 Activity/Service 里，从别的入口进入 App 方控全废。
     */
    @Synchronized
    fun register(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        appContext = app
        loadConfig(app)

        val created = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // 直投主线程处理，避免 goAsync 的超时窗口；单条按键处理是毫秒级。
                dispatch(ctx.applicationContext, intent)
            }
        }

        // 文档硬要求：必须动态注册（Android 8+ 自定义 action 送不到 manifest），
        // 且 Android 13+ 必须显式 RECEIVER_EXPORTED（广播来自车机其他进程）。
        val registered = runCatching {
            ContextCompat.registerReceiver(
                app,
                created,
                IntentFilter(ACTION_MEDIA),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure { Log.e(TAG, "方控接收器注册失败（方控将不可用）", it) }
            .isSuccess

        receiver = created.takeIf { registered }
        Log.i(TAG, "方控接收器已注册（$ACTION_MEDIA）registered=$registered mode=${currentMode()} map=$keyMap")
    }

    @Synchronized
    fun detach() {
        val ctx = appContext
        val existing = receiver
        if (ctx != null && existing != null) runCatching { ctx.unregisterReceiver(existing) }
        receiver = null
        retryAttempt = 0
        pendingKeyCode = 0
        Log.i(TAG, "方控接收器已卸载")
    }

    /** 动态接收器与静态接收器（Leapmotor24SteeringReceiver）共用此入口，共享同一份去重状态。 */
    @Synchronized
    fun dispatch(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_MEDIA) return
        if (appContext == null) appContext = context.applicationContext

        val raw = runCatching { intent.getIntExtra(EXTRA_ACTION, Int.MIN_VALUE) }
            .getOrDefault(Int.MIN_VALUE)
        if (raw == Int.MIN_VALUE) {
            Log.i(TAG, "方控忽略（广播未携带 int extra \"action\"）")
            return
        }
        Log.i(TAG, "方控广播: action=$raw")

        if (mode == MODE_ANDROID) {
            Log.i(TAG, "方控忽略（android 模式：交由原生媒体键处理）action=$raw")
            return
        }

        val keyCode = keyMap[raw]
        if (keyCode == null) {
            Log.i(TAG, "方控忽略（非媒体键）action=$raw")
            return
        }
        if (!claim(keyCode)) return

        // 文档 §4 数据流：去重通过 -> ① 立即重推媒体源声明（抢归属）-> ② 执行动作
        LeapmotorMediaClaim.onWheelKey(context)
        sendWithRetry(keyCode)
    }

    /** 文档 §1 去重：同一 keyCode 且间隔 ≤400ms 只放行一次。 */
    private fun claim(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        if (keyCode == lastKeyCode && now - lastUptimeMillis <= DUPLICATE_WINDOW_MILLIS) {
            Log.i(TAG, "方控去重 keyCode=$keyCode（${now - lastUptimeMillis}ms 内重复）")
            return false
        }
        lastKeyCode = keyCode
        lastUptimeMillis = now
        return true
    }

    // ===== 文档第 1 节「补发」：会话未就绪时记住按键，400ms 重试，≤20 次 / 8 秒 =====

    private fun sendWithRetry(keyCode: Int) {
        if (isPlayerReady()) {
            deliver(keyCode, 0)
            return
        }
        pendingKeyCode = keyCode
        retryAttempt = 1
        Log.i(TAG, "播放器会话未就绪，记住按键 keyCode=$keyCode，${RETRY_INTERVAL_MILLIS}ms 后重试（最多 $MAX_RETRY 次）")
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (retryAttempt > MAX_RETRY) {
            Log.w(TAG, "补发放弃：重试 $MAX_RETRY 次仍未见播放器会话（keyCode=$pendingKeyCode）")
            pendingKeyCode = 0
            return
        }
        mainHandler.postDelayed({
            val code = pendingKeyCode
            if (code == 0) return@postDelayed
            if (isPlayerReady()) {
                val attempt = retryAttempt
                pendingKeyCode = 0
                retryAttempt = 0
                Log.i(TAG, "补发成功：第 $attempt 次重试时播放器已就绪 keyCode=$code")
                deliver(code, attempt)
            } else {
                retryAttempt++
                scheduleRetry()
            }
        }, RETRY_INTERVAL_MILLIS)
    }

    /**
     * 播放器会话是否就绪。audio_service 与本补丁在同一个 APK 内，
     * 直接反射读它的 `instance` 静态字段即可（比盲发再猜要可靠，也避免误补发导致连跳）。
     * 反射失败一律视为就绪（fail-open），绝不因为探测不到就吞掉按键。
     */
    private fun isPlayerReady(): Boolean = runCatching {
        val cls = Class.forName("com.ryanheise.audioservice.AudioService")
        val field = cls.declaredFields.firstOrNull {
            it.name == "instance" && java.lang.reflect.Modifier.isStatic(it.modifiers)
        } ?: return@runCatching true
        field.isAccessible = true
        field.get(null) != null
    }.getOrDefault(true)

    /** 文档红线 #5：按下 + 抬起成对发送，只发一次在部分实现上会不触发或重复触发。 */
    private fun deliver(keyCode: Int, attempt: Int) {
        val ctx = appContext ?: return
        val downTime = SystemClock.uptimeMillis()
        sendKey(ctx, KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        sendKey(ctx, KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        val retryNote = if (attempt > 0) "，补发第 $attempt 次" else ""
        Log.i(TAG, "方控 → ${keyName(keyCode)}（keyCode=$keyCode，按下+抬起成对$retryNote）")
    }

    /** 第三层「送达」的本 App 等价实现：定向投递给 audio_service 的媒体键接收器。 */
    private fun sendKey(ctx: Context, event: KeyEvent) {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(AUDIO_SERVICE_PKG, AUDIO_SERVICE_RECEIVER)
            putExtra(Intent.EXTRA_KEY_EVENT, event)
        }
        runCatching { ctx.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "媒体键投递失败 action=${event.action} keyCode=${event.keyCode}", it) }
    }

    private fun keyName(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "上一曲"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "下一曲"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "播放暂停"
        else -> "keyCode=$keyCode"
    }

    // ===== 配置（持久化在 SharedPreferences，重启不丢；对应文档 §5）=====

    fun currentMode(): String = mode

    fun currentKeyMap(): Map<Int, Int> = keyMap

    private fun loadConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_WHEEL_SOURCE, MODE_AUTO) ?: MODE_AUTO
        mode = if (savedMode in setOf(MODE_AUTO, MODE_FACTORY, MODE_ANDROID)) savedMode else MODE_AUTO

        val savedMap = prefs.getString(KEY_KEY_MAP, null)
        keyMap = if (savedMap.isNullOrBlank()) DEFAULT_KEY_MAP else parseKeyMap(savedMap) ?: DEFAULT_KEY_MAP
    }

    fun setMode(context: Context, newMode: String) {
        val normalized = newMode.trim().lowercase()
        if (normalized !in setOf(MODE_AUTO, MODE_FACTORY, MODE_ANDROID)) {
            Log.w(TAG, "方控配置：非法模式 $newMode（可选 auto/factory/android），忽略")
            return
        }
        mode = normalized
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WHEEL_SOURCE, normalized).apply()
        Log.i(TAG, "方控来源模式已切换为：$normalized（已持久化）")
    }

    /**
     * 改写 action -> keyCode 映射，格式 `"1=88,4=87,7=85"`（action=keyCode，逗号分隔）。
     * 文档坑 #9：某车机上/下一曲可能反了，序号/键码因固件而异，必须能远程试。
     */
    fun setKeyMap(context: Context, spec: String) {
        val parsed = parseKeyMap(spec)
        if (parsed == null || parsed.isEmpty()) {
            Log.w(TAG, "方控配置：映射格式非法「$spec」，应形如 1=88,4=87,7=85，忽略")
            return
        }
        keyMap = parsed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_KEY_MAP, spec).apply()
        Log.i(TAG, "方控键码映射已更新：$parsed（已持久化）")
    }

    fun resetKeyMap(context: Context) {
        keyMap = DEFAULT_KEY_MAP
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_KEY_MAP).apply()
        Log.i(TAG, "方控键码映射已恢复默认：$DEFAULT_KEY_MAP")
    }

    private fun parseKeyMap(spec: String): Map<Int, Int>? {
        val result = LinkedHashMap<Int, Int>()
        for (entry in spec.split(',', ';')) {
            val item = entry.trim()
            if (item.isEmpty()) continue
            val bits = item.split('=')
            if (bits.size != 2) return null
            val action = bits[0].trim().toIntOrNull() ?: return null
            val code = bits[1].trim().toIntOrNull() ?: return null
            result[action] = code
        }
        return result
    }

    /** 配置快照，供 ADB 查询。 */
    fun describe(): String =
        "mode=$mode map=$keyMap（默认 $DEFAULT_KEY_MAP）"
}
