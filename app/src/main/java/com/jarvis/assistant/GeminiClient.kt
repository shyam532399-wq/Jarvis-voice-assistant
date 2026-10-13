package com.jarvis.assistant

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, NETWORK, AUTH, MODEL, QUOTA, SERVER, PARSE }
}

data class JarvisAction(val type: String, val args: JSONObject)

data class GeminiReply(val heard: String, val say: String, val actions: List<JarvisAction>)

fun JSONObject.str(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key, "")
}

class GeminiClient(private val prefs: Prefs) {

    fun ask(wav: ByteArray, context: String): GeminiReply {
        val key = prefs.geminiKey
        if (key.isBlank()) throw GeminiException(GeminiException.Kind.NO_KEY, "Gemini key missing")
        val model = prefs.model.trim().removePrefix("models/").ifBlank { Prefs.DEFAULT_MODEL }

        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val userParts = JSONArray()
        userParts.put(
            JSONObject().put(
                "inlineData",
                JSONObject().put("mimeType", "audio/wav").put("data", b64)
            )
        )
        userParts.put(JSONObject().put("text", context))

        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.3).put("responseMimeType", "application/json")
            )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + Uri.encode(model) + ":generateContent")
        val conn = url.openConnection() as HttpURLConnection
        var raw = ""
        var code = 0
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 40000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        } catch (e: IOException) {
            throw GeminiException(GeminiException.Kind.NETWORK, e.message ?: "network")
        } finally {
            conn.disconnect()
        }

        if (code !in 200..299) {
            val kind = when {
                code == 401 || code == 403 -> GeminiException.Kind.AUTH
                code == 400 && raw.contains("API key", ignoreCase = true) -> GeminiException.Kind.AUTH
                code == 404 -> GeminiException.Kind.MODEL
                code == 429 -> GeminiException.Kind.QUOTA
                else -> GeminiException.Kind.SERVER
            }
            throw GeminiException(kind, "HTTP $code")
        }
        return parse(raw)
    }

    private fun parse(raw: String): GeminiReply {
        val root = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw GeminiException(GeminiException.Kind.PARSE, "bad response")
        }
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.str("text"))
            }
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) return GeminiReply("", "", emptyList())

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return GeminiReply("", text.take(200), emptyList())
        }
        val obj = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: JSONException) {
            throw GeminiException(GeminiException.Kind.PARSE, "bad json")
        }
        val acts = ArrayList<JarvisAction>()
        val arr = obj.optJSONArray("actions")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val t = a.str("type").trim()
                if (t.isEmpty()) continue
                acts.add(JarvisAction(t, a.optJSONObject("args") ?: JSONObject()))
            }
        }
        return GeminiReply(obj.str("heard"), obj.str("say"), acts)
    }

    companion object {
        val SYSTEM_PROMPT = """
You are JARVIS, a calm, polite, slightly witty voice assistant living inside the user's Android phone. The user speaks Hindi, English or Hinglish. The attached audio is what the user just said to you.

Reply with ONE JSON object and nothing else (no markdown, no code fences):
{"heard":"<short transcript of the user's words>","say":"<your spoken reply>","actions":[{"type":"<action>","args":{}}]}

Style of "say": Hinglish written in Roman letters, calm, short (max 2 short sentences, under 25 words), no emojis, no markdown. Call the user "sir" only occasionally. Never read out JSON, URLs or symbols.

Actions you may use ("actions" can be empty):
- answer_call {}                          answer the ringing call
- reject_call {}                          reject / end the ringing call
- open_app {"name":"WhatsApp"}            open an installed app (give the app name in Roman letters)
- call_contact {"name":"Mummy"}           call a saved contact (or {"number":"9876543210"})
- lock_screen {}                          lock the phone
- unlock_screen {}                        wake the screen and dismiss a swipe lock
- home {}  back {}  recents {}            navigation buttons
- volume {"direction":"up|down|mute|unmute|max"}  or  {"percent":0-100}   media volume
- flashlight {"on":true}  or  {"on":false}   torch (omit "on" to toggle)

Rules:
- Use actions only when the user clearly asks for them. For normal questions answer briefly in "say" with "actions":[].
- If the context says a call is ringing and the user says haan / uthao / utha lo / pick up / receive, use answer_call. If they say kaat do / cut / reject / mat uthao / busy, use reject_call.
- If the audio is only silence, noise, music, TV, or clearly not meant for you, return {"heard":"","say":"","actions":[]}.
- For unlock_screen keep "say" very short. The app handles PIN or pattern safety itself. Never ask for, guess or repeat a PIN or password.
- If you cannot do something, say so in one short line.
""".trimIndent()
    }
}
