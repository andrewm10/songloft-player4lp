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
import kotlin.math.minOf
import org.json.JSONObject
import java.util.Locale

/**
 * 零跑（Leapmotor）车机方向盘媒体键（方控）适配。
 *
 * 零跑车机不把方向盘按键作为 Android KeyEvent 下发，而是经 CAN → ICU → 广播转发，
 * 第三方 App 收不到任何 KeyEvent。因此必须监听这些广播，并将其翻译成标准
 * `MEDIA_BUTTON` 键事件，转发给 Songloft 已经在用的 audio_service
 * （com.ryanheise.audioservice.MediaButtonReceiver），由它驱动播放/暂停/上一首/下一首。
 *
 * 协议与去重逻辑移植自 Wh1t3co1azZ/DiPlay-Leapmotor 的 LeapmotorMediaKeys.kt
 * （上游为 CarPlay 场景，本文件去掉了 CarPlay 依赖，只保留「广播 → 媒体键」这一段）。
 * 适用车型：零跑 2021–2023 款（固件差异通过同时注册多种 action 别名兼容）。
 *
 * 接入方式：在 SongloftApplication.onCreate() 里调用 LeapmotorMediaKeys.attach(this) 即可，
 * 进程退出时可选调用 detach()。
 */
internal object LeapmotorMediaProtocol {
    /** 主通道：已知固件里出现过的全部 action 拼写形式（含不同车型的别名）。 */
    private val CHANNEL_A = listOf(
        "car.meter.music.BROADCAST",
        "car.hmi.music.BROADCAST",
        "com.leapmotor.command.music",
        "com.leapmotor.command.multimedia",
        "com.leapmotor.command.media",
        "com.leapmotor.command.playpause",
        "com.leapmotor.command.next",
        "com.leapmotor.command.previous",
        "com.leapmotor.command.key",
        "com.leapmotor.command.mediaKey",
        "com.leapmotor.multimedia.mediaKey",
        "com.leapmotor.multimedia.MediaKey",
        "com.leapmotor.media.keyevent",
        "com.leapmotor.media.KEY_EVENT",
        "com.leapmotor.audio.key",
        "com.leapmotor.audio.KEY_EVENT",
        "com.leapmotor.audiocenter.mediaKey",
        "com.leapmotor.vehicle.keyevent",
        "com.leapmotor.vehicle.mediaKey",
        "car.hmi.media.KEY_EVENT",
        "car.hmi.keyevent",
        "car.media.keyevent",
        "car.media.next",
        "car.media.previous",
        "com.leapmotor.ICUMainCtrl",
        "com.leapmotor.ICUCtrl",
        "com.leapmotor.customkey.media.next",
        "com.leapmotor.customkey.media.prev",
        "com.leapmotor.customkey.media.pauseplay",
        "com.leapmotor.customkey.music.next",
        "com.leapmotor.customkey.music.prev",
    )

    /**
     * 车机收音机/语音助手自用命令，必须识别并丢弃，否则会抢走收音机。
     */
    private val STOCK_HOUSEKEEPING = listOf(
        "com.leapmotor.command.netRadio",
        "com.leapmotor.command.fmradio",
        "com.leapmotor.music.netradio.search",
        "car.meter.query.BROADCAST",
        "com.leapmotor.pohone.toxmly.calling",
    )

    /** 键码走整形 extra 而非文本。 */
    val EXTRA_CHANNELS = listOf(
        "com.leapmotor.customkey.music.pauseplay",
        "com.leapmotor.ICU2MMICtrl",
    )

    /** C11 自身的仪表/语音控制通道。 */
    val C11_CHANNEL = listOf(
        "com.leapmotor.action.METER.CTRL",
        "com.leapmotor.action.VOICE.CTRL",
    )

    /** attach 时注册的全部 action（含整型 extra 通道，确保不漏接）。 */
    val ACTIONS: List<String> = CHANNEL_A + STOCK_HOUSEKEEPING + C11_CHANNEL + EXTRA_CHANNELS

    private val MEDIA_KEY_EXTRA = arrayOf("ICU_MediaKey", "mediaKey")
    private val MEDIA_SWITCH_EXTRA = arrayOf("ICU_MediaSwitch", "mediaSwitch")

    private const val RECEIVER_EXTRA = "receiver"
    private const val ACTION_EXTRA = "action"
    private const val KEY_EVENT_TYPE_EXTRA = "keyEventType"
    private const val VALUE_EXTRA = "value"

    fun isExtraChannel(action: String?): Boolean = action != null && EXTRA_CHANNELS.contains(action)
    fun isC11Channel(action: String?): Boolean = action != null && C11_CHANNEL.contains(action)

    /**
     * `receiver` 是带长度前缀的 UTF-8 JSON：前两字节以 `first*100 + second` 编码长度，
     * 之后才是 JSON 正文（ICU 的帧格式）。
     */
    fun actionFromPayload(payload: ByteArray?): String? {
        if (payload == null || payload.size <= 2) return null
        val declared = (payload[0].toInt() and 0xff) * 100 + (payload[1].toInt() and 0xff)
        val end = minOf(payload.size, declared + 2)
        if (end <= 2) return null
        return actionFromJson(String(payload, 2, end - 2, Charsets.UTF_8))
    }

    fun actionFromJson(text: String?): String? {
        val body = (text ?: "").trim()
        if (body.isEmpty() || !body.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(body)
            // 忽略自己的回显，避免写操作被误当成按键（防回环）。
            if (root.optString("src") == "diplay") return@runCatching null
            val type = root.optString("type")
            if (type.isNotEmpty() && !type.equals("music", ignoreCase = true)) return@runCatching null
            root.optJSONObject("data")?.optString("action")?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /** C11 仪表/后座通道以相同结构转发方向盘与后座按键。 */
    fun c11ChannelAction(intent: Intent?): String? {
        if (intent == null) return null
        val type = runCatching { intent.getStringExtra(KEY_EVENT_TYPE_EXTRA) }.getOrNull() ?: return null
        if (type != "steeringWheel" && type != "backRow") return null
        return when (runCatching { intent.getStringExtra(VALUE_EXTRA) }.getOrNull()) {
            "1" -> "preOne"
            "2" -> "nextOne"
            "3" -> "playpause"
            else -> null
        }
    }

    /** 读取 [keys] 中第一个存在的整型 extra，都不存在时返回 0。 */
    private fun intExtra(intent: Intent, keys: Array<String>): Int {
        val missing = Int.MIN_VALUE
        for (key in keys) {
            val value = runCatching { intent.getIntExtra(key, missing) }.getOrDefault(missing)
            if (value != missing) return value
        }
        return 0
    }

    fun extraChannelAction(intent: Intent?): String? {
        if (intent == null) return null
        val switch = intExtra(intent, MEDIA_SWITCH_EXTRA)
        val key = intExtra(intent, MEDIA_KEY_EXTRA)
        if (key == 1) return "playpause"
        return when (switch) {
            2 -> "nextOne"
            1 -> "preOne"
            else -> null
        }
    }

    /** 所有 extra 的 `key=value` 形式，用于「未匹配」时的排查。 */
    fun extraChannelDetail(intent: Intent?): String {
        val extras = intent?.extras ?: return "no extras"
        return extras.keySet().sorted().joinToString(" ") { key ->
            val value = runCatching { extras.get(key) }.getOrNull()
            when {
                value == null -> "$key=null"
                value is ByteArray -> "$key=byte[${value.size}]"
                else -> "$key=$value"
            }
        }
    }

    internal fun receiverExtra(intent: Intent): ByteArray? =
        runCatching { intent.getByteArrayExtra(RECEIVER_EXTRA) }.getOrNull()

    internal fun actionExtra(intent: Intent): String? =
        runCatching { intent.getStringExtra(ACTION_EXTRA) }.getOrNull()
}

/**
 * 把车机的媒体广播翻译成标准媒体键，驱动 Songloft 的 audio_service 媒体会话。
 */
internal object LeapmotorMediaKeys {
    private const val TAG = "Songloft-Leapmotor"
    private const val DUPLICATE_WINDOW_MILLIS = 250L
    private const val AUDIO_SERVICE_PKG = "com.ryanheise.audioservice"
    private const val AUDIO_SERVICE_RECEIVER = "com.ryanheise.audioservice.MediaButtonReceiver"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var lastKeyUptimeMillis = 0L
    private var lastKeyCode = 0

    @Synchronized
    fun attach(context: Context) {
        if (receiver != null) return
        appContext = context.applicationContext
        val filter = IntentFilter().apply {
            LeapmotorMediaProtocol.ACTIONS.forEach { addAction(it) }
        }
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
        // Android 13+ 必须用 RECEIVER_NOT_EXPORTED；某些固件上两参数 registerReceiver 会被拒。
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                created,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        receiver = created.takeIf { registered }
        Log.i(TAG, "media keys listening, registered=$registered, actions=${LeapmotorMediaProtocol.ACTIONS.size}")
    }

    @Synchronized
    fun detach() {
        val ctx = appContext
        val existing = receiver
        if (ctx != null && existing != null) runCatching { ctx.unregisterReceiver(existing) }
        receiver = null
        appContext = null
        Log.i(TAG, "media keys detached")
    }

    /** 同一物理键常被多个通道同时投递，250ms 窗口内同一 keyCode 只放行一次。 */
    private fun claimDispatchWindow(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        if (keyCode == lastKeyCode && now - lastKeyUptimeMillis < DUPLICATE_WINDOW_MILLIS) return false
        lastKeyCode = keyCode
        lastKeyUptimeMillis = now
        return true
    }

    private fun handle(intent: Intent?) {
        val action = intent?.action ?: return
        val mapped: String? = when {
            LeapmotorMediaProtocol.isExtraChannel(action) ->
                LeapmotorMediaProtocol.extraChannelAction(intent)
            LeapmotorMediaProtocol.isC11Channel(action) ->
                LeapmotorMediaProtocol.c11ChannelAction(intent)
            else -> {
                val fromPayload = LeapmotorMediaProtocol.actionFromPayload(
                    LeapmotorMediaProtocol.receiverExtra(intent),
                )
                fromPayload
                    ?: LeapmotorMediaProtocol.actionExtra(intent)?.takeIf { it.isNotBlank() }
                    ?: action
            }
        }
        if (mapped != null) {
            dispatch(mapped, action)
        } else {
            Log.i(TAG, "unmatched action=$action extras=[${LeapmotorMediaProtocol.extraChannelDetail(intent)}]")
        }
    }

    /** 把 Leapmotor 动作串翻译为标准媒体键并转发给 audio_service。 */
    private fun dispatch(rawAction: String, source: String) {
        val keyCode = mapToKeyCode(rawAction) ?: run {
            // 非方控媒体键（多为收音机/语音路径），直接丢弃，避免误触。
            Log.i(TAG, "ignored (not a wheel media key): source=$source action=${rawAction.trim()}")
            return
        }
        if (!claimDispatchWindow(keyCode)) {
            Log.i(TAG, "duplicate suppressed: action=${rawAction.trim()}")
            return
        }
        sendMediaButton(keyCode)
        Log.i(TAG, "dispatched: source=$source action=${rawAction.trim()} -> keyCode=$keyCode")
    }

    /**
     * 仅映射「方控安全」的媒体键集合（next/prev/playpause 系列）。
     * 裸的 play/pause/stop 同样来自收音机与语音助手路径，放行会造成误触，故故意排除
     * （对应上游 isWheelAction() 的范围小于 forLeapmotorAction() 的设计）。
     */
    private fun mapToKeyCode(action: String?): Int? {
        if (action == null) return null
        return when (action.trim().lowercase(Locale.ROOT)) {
            "playpause", "pauseplay", "toggleplay", "toggleplaypause", "play_pause" ->
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "nextone", "nexttrack" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev", "preone", "previous", "previoustrack" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> null
        }
    }

    /** 构造 MEDIA_BUTTON 广播，定向发给 audio_service 的 MediaButtonReceiver。 */
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
