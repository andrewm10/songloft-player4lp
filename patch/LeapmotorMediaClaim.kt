package com.songloft.songloft_flutter

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * 零跑方控——第二层：占媒体源（防双控）。《方向盘方控方案》第 2 节称其为
 * 「★ 最关键、最容易漏」的一层。
 *
 * 车机按「当前媒体源」分发方向盘按键：谁声明了媒体源，方向盘就归谁。
 * 不声明 -> 源仍是原车音乐 App -> 按键同时打到两边（双控 + 双音频），
 * 且按键归属不稳定，容易出现「按下一首变成暂停」这类落错动作。
 *
 * 声明广播（文档实机反推格式，一个字都别改）：
 *   action : com.leapmotor.transfer.MEDIA.INFO
 *   flags  : FLAG_RECEIVER_INCLUDE_BACKGROUND | FLAG_RECEIVER_FOREGROUND
 *   extras : "info" = JSON 字符串
 *            { "type":"music",
 *              "data":{ "type":15,            <- 内层 type 硬编码 15，别改
 *                       "name":..., "title":..., "artist":...,
 *                       "duration":0,
 *                       "imagUrl":"",         <- 厂商原版少一个 e，别「修正」成 imageUrl
 *                       "state":0|1 } }       <- 0=暂停 1=播放
 *
 * 三个配套动作（缺一个就「偶发失灵」）：
 *   ① 1 秒心跳，一直重推（源会被系统/别的 App 抢走）
 *   ② 按键后立即重推一次（抢回归属，实机验证有效）——由 Leapmotor24Wheel 去重后调用 onWheelKey()
 *   ③ 前台门：只在 Settings.Global["display_0_top_activity"] 以自己包名开头时才推，
 *      并注册 ContentObserver 监听其变化（变化时立即 refresh + publishNow）。
 *      **红线 #2：这个门绝不能抄进方控接收器**，否则 App 在后台时方控就不响应了。
 *
 * 对播放器类 App 的放宽（文档第 7 节判定表明确建议）：
 *   除「自己在前台」外，只要自己正在出声（AudioManager.isMusicActive）也允许声明——
 *   否则后台播放时 skipped 会一直涨、方控归属保不住。
 *
 * 曲目信息（文档第 5 步「本地播放器能拿到真的」）：
 *   Kotlin 层读本 App 自己通知栏里的标题/歌手（audio_service 的媒体通知），
 *   无需任何权限、也无需改动上游 Dart 代码；读不到时回退到占位值。
 */
internal object LeapmotorMediaClaim {
    private const val TAG = "Songloft-Leapmotor"

    private const val ACTION_MEDIA_INFO = "com.leapmotor.transfer.MEDIA.INFO"
    private const val EXTRA_INFO = "info"
    private const val DATA_TYPE = 15          // 内层 type 硬编码，别改
    private const val HEARTBEAT_MILLIS = 1000L
    private const val TOP_ACTIVITY_KEY = "display_0_top_activity"

    private const val PREFS_KEY_ENABLED = "media_claim"

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    @Volatile private var selfOnTop = false
    @Volatile private var started = false
    @Volatile private var enabled = true

    private var sentCount = 0
    private var skippedCount = 0
    private var observer: ContentObserver? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            try {
                refresh()
                publish()
            } catch (t: Throwable) {
                Log.w(TAG, "媒体源心跳异常（已忽略，不影响播放）", t)
            } finally {
                handler.postDelayed(this, HEARTBEAT_MILLIS)
            }
        }
    }

    /** 进程级启动，由 LeapmotorInitProvider 调用（与方控接收器同一入口）。 */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        appContext = app
        enabled = app.getSharedPreferences(Leapmotor24Wheel.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREFS_KEY_ENABLED, true)
        started = true

        refresh()
        registerTopObserver(app)
        handler.post(heartbeat)   // 立即推一次，之后每秒一次
        Log.i(TAG, "媒体源：声明已启动 action=$ACTION_MEDIA_INFO 心跳=${HEARTBEAT_MILLIS}ms enabled=$enabled")
    }

    /** 重算前台门（文档 §2：心跳里也要 refresh 一次，防 ContentObserver 漏事件）。 */
    fun refresh() {
        val ctx = appContext ?: return
        selfOnTop = runCatching {
            val top = Settings.Global.getString(ctx.contentResolver, TOP_ACTIVITY_KEY) ?: ""
            top.startsWith(ctx.packageName + "/")
        }.getOrDefault(false)
    }

    /** 文档 §4 数据流里的「按键后立即重推一次」★ —— 抢回归属。 */
    fun onWheelKey(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        try {
            refresh()
            publish()
        } catch (t: Throwable) {
            Log.w(TAG, "按键后重推媒体源失败（已忽略）", t)
        }
    }

    fun setEnabled(context: Context, value: String) {
        val on = value.trim().lowercase() in setOf("1", "on", "true", "yes")
        enabled = on
        context.getSharedPreferences(Leapmotor24Wheel.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREFS_KEY_ENABLED, on).apply()
        Log.i(TAG, "媒体源声明已${if (on) "开启" else "关闭"}（已持久化）")
        if (on) publish()
    }

    fun describe(): String =
        "enabled=$enabled 自己在前台=$selfOnTop sent=$sentCount skipped=$skippedCount"

    // ===== 内部实现 =====

    private fun publish() {
        val ctx = appContext ?: return
        if (!enabled) {
            skippedCount++
            return
        }
        if (!mayClaim(ctx)) {
            skippedCount++
            if (skippedCount % 30 == 1) logStatus(ctx, "前台门未通过")
            return
        }
        send(ctx)
    }

    /**
     * 前台门 + 播放器放宽。
     * 文档红线 #2：本方法只用于「声明媒体源」，不要用于决定是否处理按键。
     */
    private fun mayClaim(ctx: Context): Boolean = selfOnTop || isMusicActive(ctx)

    private fun send(ctx: Context) {
        val (title, artist) = readNowPlaying(ctx)
        val state = if (isMusicActive(ctx)) 1 else 0

        val info = JSONObject().apply {
            put("type", "music")
            put("data", JSONObject().apply {
                put("type", DATA_TYPE)
                put("name", title)
                put("title", title)
                put("artist", artist)
                put("duration", 0)
                put("imagUrl", "")          // 少一个 e 是厂商原版写法，别改
                put("state", state)
            })
        }

        val intent = Intent(ACTION_MEDIA_INFO).apply {
            putExtra(EXTRA_INFO, info.toString())
            // 注意：Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND(0x01000000) 是 @SystemApi，
            // 公开 SDK(android.jar) 里没有这个常量，写成 Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
            // 会直接编译失败——所以这里用字面量保留文档原意（该 flag 需要系统权限才生效，
            // 普通应用发送时系统会忽略，不影响行为）。
            addFlags(0x01000000 or Intent.FLAG_RECEIVER_FOREGROUND)
        }
        runCatching { ctx.sendBroadcast(intent) }
            .onSuccess {
                sentCount++
                if (sentCount % 30 == 1) logStatus(ctx, "心跳")
            }
            .onFailure { Log.w(TAG, "媒体源声明发送失败", it) }
    }

    private fun logStatus(ctx: Context, reason: String) {
        val top = runCatching { Settings.Global.getString(ctx.contentResolver, TOP_ACTIVITY_KEY) }
            .getOrNull() ?: "?"
        Log.i(TAG, "媒体源：广播声明sent=$sentCount skipped=$skippedCount 自己在前台=$selfOnTop top=$top（$reason）")
    }

    private fun registerTopObserver(ctx: Context) {
        if (observer != null) return
        val created = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // 文档 §2：变化时立即 refresh + publishNow
                try {
                    refresh()
                    publish()
                } catch (t: Throwable) {
                    Log.w(TAG, "top activity 变化处理异常（已忽略）", t)
                }
            }
        }
        runCatching {
            ctx.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(TOP_ACTIVITY_KEY),
                false,
                created,
            )
            observer = created
        }.onFailure {
            // 收不到变化事件也不致命：1 秒心跳里每次都会 refresh 兜底
            Log.w(TAG, "top activity 监听注册失败（心跳会兜底 refresh）", it)
        }
    }

    private fun isMusicActive(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive == true
    }.getOrDefault(false)

    /** 读本 App 自己通知栏里的曲目信息（零权限；读不到就回退占位值）。 */
    private fun readNowPlaying(ctx: Context): Pair<String, String> {
        val fallbackTitle = "Songloft"
        val fallback = fallbackTitle to ""
        return runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@runCatching fallback
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching fallback
            val statusBar = nm.activeNotifications ?: return@runCatching fallback
            val own = statusBar.firstOrNull { it.packageName == ctx.packageName }?.notification
                ?: return@runCatching fallback
            val extras = own.extras ?: return@runCatching fallback

            fun text(key: String): String? =
                extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            val rawTitle = text(Notification.EXTRA_TITLE)
            val rawText = text(Notification.EXTRA_TEXT)
            val rawSub = text(Notification.EXTRA_SUB_TEXT)

            val title = rawTitle ?: rawText ?: fallbackTitle
            val artist = rawSub ?: rawText?.takeIf { it != title } ?: ""
            title to artist
        }.getOrDefault(fallback)
    }
}
