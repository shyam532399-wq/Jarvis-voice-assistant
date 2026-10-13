package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false
    private val initLatch = CountDownLatch(1)

    @Volatile
    private var doneLatch: CountDownLatch? = null

    fun init() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    var r = t.setLanguage(Locale("hi", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        r = t.setLanguage(Locale("en", "IN"))
                    }
                    t.setSpeechRate(0.95f)
                    t.setPitch(0.9f)
                    t.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            doneLatch?.countDown()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            doneLatch?.countDown()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            doneLatch?.countDown()
                        }
                    })
                    ready = true
                }
            }
            initLatch.countDown()
        }
    }

    fun speakBlocking(text: String) {
        if (text.isBlank()) return
        initLatch.await(5, TimeUnit.SECONDS)
        val t = tts ?: return
        if (!ready) return
        val latch = CountDownLatch(1)
        doneLatch = latch
        val res = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
        if (res != TextToSpeech.SUCCESS) return
        latch.await(30, TimeUnit.SECONDS)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
