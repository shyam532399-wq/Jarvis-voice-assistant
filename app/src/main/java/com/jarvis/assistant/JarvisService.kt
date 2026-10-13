package com.jarvis.assistant

import android.Manifest
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class JarvisService : Service() {

    companion object {
        const val ACTION_STOP = "com.jarvis.assistant.STOP"
        private const val SAMPLE_RATE = 16000
        private const val STATUS_IDLE = "Jarvis bolo..."
        private const val STATUS_FOLLOW = "Follow-up: bolo..."

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, JarvisService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, JarvisService::class.java))
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker
    private lateinit var gemini: GeminiClient
    private lateinit var actions: ActionExecutor

    private var wakeLock: PowerManager.WakeLock? = null
    private val stopFlag = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)

    @Volatile
    private var listenUntil = 0L

    @Volatile
    private var ignoreUntil = 0L
    private var worker: Thread? = null
    private val exec = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val history = ArrayList<String>()

    @Volatile
    private var ringing = false

    @Volatile
    private var ringNumber: String? = null

    @Volatile
    private var ringName = ""
    private var receiverRegistered = false

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val st = i.getStringExtra(TelephonyManager.EXTRA_STATE)
            @Suppress("DEPRECATION")
            val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            onPhoneState(st, num)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Notifier.ensureChannels(this)
        speaker = Speaker(this)
        speaker.init()
        gemini = GeminiClient(prefs)
        actions = ActionExecutor(this)
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notifier.ID_LISTEN,
                Notifier.listening(this, "Jarvis sun raha hai"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (_: Exception) {
            JarvisState.status = "Start nahi hua"
            Notifier.showRestartNotice(this)
            stopSelf()
            return START_NOT_STICKY
        }
        Notifier.clearRestartNotice(this)
        JarvisState.running = true
        JarvisState.status = "Shuru ho raha hai..."
        registerPhoneReceiver()
        startPipeline()
        return START_STICKY
    }

    override fun onDestroy() {
        stopFlag.set(true)
        JarvisState.running = false
        JarvisState.status = "Offline"
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            try {
                unregisterReceiver(phoneReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        try {
            worker?.join(1500)
        } catch (_: InterruptedException) {
        }
        exec.shutdownNow()
        speaker.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun registerPhoneReceiver() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                phoneReceiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun onPhoneState(state: String?, number: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!number.isNullOrBlank()) ringNumber = number
                if (!ringing) {
                    ringing = true
                    handler.postDelayed({ announceCall() }, 800)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> {
                ringing = false
                ringNumber = null
                ringName = ""
                listenUntil = 0L
            }
        }
    }

    private fun announceCall() {
        if (!ringing || stopFlag.get()) return
        val name = ContactsHelper.nameFor(this, ringNumber)
            ?: ringNumber?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
                ?.toCharArray()?.joinToString(" ")
            ?: "Unknown number"
        ringName = name
        try {
            exec.execute {
                if (!ringing) return@execute
                busy.set(true)
                try {
                    JarvisState.lastHeard = "(incoming call)"
                    say(name + " ka call aa raha hai, uthaun ya kaat dun?")
                } finally {
                    val now = SystemClock.elapsedRealtime()
                    ignoreUntil = now + 300
                    if (ringing) listenUntil = now + 25_000L
                    busy.set(false)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun startPipeline() {
        if (worker?.isAlive == true) return
        stopFlag.set(false)
        val t = Thread({ pipelineLoop() }, "jarvis-audio")
        worker = t
        t.start()
    }

    private fun fatal(status: String, spoken: String) {
        JarvisState.status = status
        prefs.enabled = false
        try {
            exec.execute {
                speaker.speakBlocking(spoken)
                handler.post { stopSelf() }
            }
        } catch (_: Exception) {
            handler.post { stopSelf() }
        }
    }

    private fun pipelineLoop() {
        val key = prefs.porcupineKey
        if (key.isBlank()) {
            fatal("Porcupine key missing", "Porcupine key Settings me daalo.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fatal("Mic permission missing", "Mic permission do.")
            return
        }
        var porcupine: ai.picovoice.porcupine.Porcupine? = null
        var rec: AudioRecord? = null
        try {
            val p = ai.picovoice.porcupine.Porcupine.Builder()
                .setAccessKey(key)
                .setKeyword(ai.picovoice.porcupine.Porcupine.BuiltInKeyword.JARVIS)
                .build(applicationContext)
            porcupine = p
            val frameLen = p.frameLength
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf, frameLen * 2 * 4)
            val r = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            rec = r
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                fatal("Mic start nahi hua", "Mic start nahi ho paya.")
                return
            }
            r.startRecording()

            val frame = ShortArray(frameLen)
            var noiseFloor = 250.0
            JarvisState.status = STATUS_IDLE

            while (!stopFlag.get()) {
                if (!readFrame(r, frame)) {
                    if (!stopFlag.get()) fatal("Mic band ho gaya", "Mic ka access chala gaya.")
                    break
                }
                val now = SystemClock.elapsedRealtime()
                if (busy.get() || now < ignoreUntil) continue
                val level = rms(frame)
                val threshold = (noiseFloor * 2.5).coerceIn(600.0, 3000.0)

                if (now < listenUntil) {
                    if (JarvisState.status != STATUS_FOLLOW) JarvisState.status = STATUS_FOLLOW
                    if (level > threshold) {
                        dispatch(capture(r, frame, frameLen, threshold, true))
                    } else {
                        noiseFloor = noiseFloor * 0.97 + level * 0.03
                    }
                    continue
                }

                if (JarvisState.status != STATUS_IDLE) JarvisState.status = STATUS_IDLE
                noiseFloor = noiseFloor * 0.98 + minOf(level, 2000.0) * 0.02

                if (ai.picovoice.porcupine.Porcupine.Builder().setAccessKey(key).setKeyword(ai.picovoice.porcupine.Porcupine.BuiltInKeyword.JARVIS).build(applicationContext).process(frame) >= 0) {
                }
            }
        } catch (_: Exception) {
            fatal("Audio error", "Audio me kuch gadbad ho gayi.")
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            try { rec?.release() } catch (_: Exception) {}
            try { porcupine?.delete() } catch (_: Exception) {}
        }
    }

    private fun readFrame(rec: AudioRecord, buf: ShortArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = rec.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun rms(buf: ShortArray): Double {
        var s = 0.0
        for (v in buf) s += v.toDouble() * v.toDouble()
        return sqrt(s / buf.size)
    }

    private fun capture(
        rec: AudioRecord,
        first: ShortArray?,
        frameLen: Int,
        threshold: Double,
        alreadySpeaking: Boolean
    ): ByteArray? {
        val out = ByteArrayOutputStream()
        val frame = ShortArray(frameLen)
        val prev = ShortArray(frameLen)
        var havePrev = false
        var speech = alreadySpeaking
        var speechFrames = 0
        var silent = 0
        var waited = 0
        var total = 0
        val maxFrames = 10 * SAMPLE_RATE / frameLen
        val silenceFrames = (1.2 * SAMPLE_RATE / frameLen).toInt()
        val waitFrames = 5 * SAMPLE_RATE / frameLen

        JarvisState.status = "Sun raha hoon..."
        if (alreadySpeaking && first != null) {
            writeFrame(out, first)
            total++
            speechFrames++
        }
        while (!stopFlag.get() && total < maxFrames) {
            if (!readFrame(rec, frame)) return null
            val loud = rms(frame) > threshold
            if (!speech) {
                if (loud) {
                    speech = true
                    if (havePrev) {
                        writeFrame(out, prev)
                        total++
                    }
                    writeFrame(out, frame)
                    total++
                    speechFrames++
                    silent = 0
                } else {
                    waited++
                    System.arraycopy(frame, 0, prev, 0, frameLen)
                    havePrev = true
                    if (waited >= waitFrames) return null
                }
            } else {
                writeFrame(out, frame)
                total++
                if (loud) {
                    speechFrames++
                    silent = 0
                } else {
                    silent++
                    if (silent >= silenceFrames) break
                }
            }
        }
        if (speechFrames < 6) return null
        return out.toByteArray()
    }

    private fun writeFrame(out: ByteArrayOutputStream, f: ShortArray) {
        val b = ByteArray(f.size * 2)
        var j = 0
        for (s in f) {
            val v = s.toInt()
            b[j++] = (v and 0xFF).toByte()
            b[j++] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(b, 0, b.size)
    }

    private fun dispatch(pcm: ByteArray?) {
        if (pcm == null || stopFlag.get()) return
        busy.set(true)
        try {
            exec.execute { handleUtterance(pcm) }
        } catch (_: Exception) {
            busy.set(false)
        }
    }

    private fun handleUtterance(pcm: ByteArray) {
        var extendWindow = false
        try {
            JarvisState.status = "Soch raha hoon..."
            if (!isOnline()) {
                say("Internet nahi hai. Connection check karo.")
                return
            }
            val reply = try {
                gemini.ask(Wav.fromPcm16(pcm, SAMPLE_RATE), buildContext())
            } catch (e: GeminiException) {
                say(errorMessage(e))
                return
            }
            if (reply.say.isBlank() && reply.actions.isEmpty()) {
                if (ringing) ignoreUntil = SystemClock.elapsedRealtime() + 800
                return
            }
            if (reply.heard.isNotBlank()) JarvisState.lastHeard = reply.heard
            val override = actions.run(reply.actions)
            val text = override ?: reply.say
            addHistory(reply.heard, text)
            if (text.isNotBlank()) say(text)
            val callAction = reply.actions.any { it.type == "answer_call" || it.type == "reject_call" }
            if (callAction) {
                ringing = false
                listenUntil = 0L
            } else {
                extendWindow = true
            }
        } catch (_: Exception) {
            try {
                say("Kuch gadbad ho gayi, dobara bolo.")
            } catch (_: Exception) {
            }
        } finally {
            val now = SystemClock.elapsedRealtime()
            ignoreUntil = now + 400
            if (extendWindow) listenUntil = now + prefs.followUpSeconds * 1000L
            busy.set(false)
        }
    }

    private fun say(text: String) {
        JarvisState.status = "Bol raha hoon..."
        JarvisState.lastSaid = text
        speaker.speakBlocking(text)
    }

    private fun addHistory(heard: String, said: String) {
        if (heard.isNotBlank()) history.add("User: " + heard)
        if (said.isNotBlank()) history.add("Jarvis: " + said)
        while (history.size > 12) history.removeAt(0)
    }

    private fun buildContext(): String {
        val km = getSystemService(KeyguardManager::class.java)
        val time = SimpleDateFormat("EEE, d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("Now: ").append(time).append(". ")
        sb.append("Screen locked: ").append(if (km.isKeyguardLocked) "yes" else "no").append(". ")
        if (ringing) {
            sb.append("A phone call is ringing right now from ").append(ringName.ifBlank { "an unknown caller" }).append(". ")
        } else {
            sb.append("No call is ringing. ")
        }
        if (history.isNotEmpty()) {
            sb.append("\nRecent conversation:\n").append(history.joinToString("\n"))
        }
        sb.append("\nAnswer the audio command as JSON.")
        return sb.toString()
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun errorMessage(e: GeminiException): String = when (e.kind) {
        GeminiException.Kind.NO_KEY -> "Gemini key Settings me daalo."
        GeminiException.Kind.AUTH -> "Gemini key galat lag rahi hai."
        GeminiException.Kind.MODEL -> "Model ka naam sahi nahi hai."
        GeminiException.Kind.QUOTA -> "Gemini ki limit khatam, thodi der baad try karo."
        GeminiException.Kind.NETWORK -> "Internet nahi hai. Connection check karo."
        GeminiException.Kind.SERVER, GeminiException.Kind.PARSE -> "Kuch gadbad ho gayi, dobara bolo."
    }
}
