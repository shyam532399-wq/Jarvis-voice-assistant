package com.jarvis.assistant

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlin.math.max
import kotlin.math.min

fun Context.findActivity(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

fun isAccessibilityEnabled(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val me = ComponentName(ctx, JarvisAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

fun safeStart(ctx: Context, intent: Intent) {
    try {
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(ctx, "Ye screen khul nahi paayi", Toast.LENGTH_SHORT).show()
    }
}

object Fuzzy {
    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    fun score(query: String, target: String): Double {
        val q = norm(query)
        val t = norm(target)
        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (q == t) return 1.0
        val tokens = target.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.any { it == q }) return 0.9
        if (min(q.length, t.length) >= 3 && (t.contains(q) || q.contains(t))) {
            val r = min(q.length, t.length).toDouble() / max(q.length, t.length).toDouble()
            return 0.6 + 0.4 * r
        }
        val d = lev(q, t)
        return 1.0 - d.toDouble() / max(q.length, t.length).toDouble()
    }

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
