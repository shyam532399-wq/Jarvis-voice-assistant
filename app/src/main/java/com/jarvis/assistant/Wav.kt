package com.jarvis.assistant

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Wav {
    fun fromPcm16(pcm: ByteArray, sampleRate: Int = 16000): ByteArray {
        val out = ByteArray(44 + pcm.size)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36 + pcm.size)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1.toShort())
        bb.putShort(1.toShort())
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2)
        bb.putShort(2.toShort())
        bb.putShort(16.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(pcm.size)
        bb.put(pcm)
        return out
    }
}
