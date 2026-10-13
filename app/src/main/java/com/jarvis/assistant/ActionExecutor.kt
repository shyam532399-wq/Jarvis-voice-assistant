package com.jarvis.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

class ActionExecutor(private val ctx: Context) {
    private var torchOn = false

    fun run(list: List<JarvisAction>): String? {
        var msg: String? = null
        for (a in list) {
            val r = try {
                exec(a)
            } catch (e: Exception) {
                "Ye kaam nahi ho paya."
            }
            if (r != null && msg == null) msg = r
        }
        return msg
    }

    private fun has(p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun exec(a: JarvisAction): String? {
        return when (a.type) {
            "answer_call" -> answerCall()
            "reject_call" -> rejectCall()
            "open_app" -> openApp(a.args)
            "call_contact" -> callContact(a.args)
            "lock_screen" -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "unlock_screen" -> unlock()
            "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "volume" -> volume(a.args)
            "flashlight" -> flashlight(a.args)
            else -> null
        }
    }

    private fun answerCall(): String? {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return "Phone permission nahi hai, Setup me allow karo."
        val tm = ctx.getSystemService(TelecomManager::class.java)
        tm.acceptRingingCall()
        return null
    }

    @Suppress("DEPRECATION")
    private fun rejectCall(): String? {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return "Phone permission nahi hai, Setup me allow karo."
        val tm = ctx.getSystemService(TelecomManager::class.java)
        return if (tm.endCall()) null else "Call kaat nahi paya."
    }

    private fun openApp(args: JSONObject): String? {
        val name = args.str("name")
        if (name.isBlank()) return "Kaunsa app kholun?"
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var bestPkg: String? = null
        var best = 0.0
        for (ri in pm.queryIntentActivities(main, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val s = Fuzzy.score(name, label)
            if (s > best) {
                best = s
                bestPkg = pkg
            }
        }
        if (bestPkg == null || best < 0.6) return name + " app nahi mila."
        val launch = pm.getLaunchIntentForPackage(bestPkg) ?: return name + " khul nahi paya."
        ActivityStarter.start(ctx, launch)
        return null
    }

    private fun callContact(args: JSONObject): String? {
        if (!has(Manifest.permission.CALL_PHONE)) return "Call permission nahi hai, Setup me allow karo."
        val direct = args.str("number").filter { it.isDigit() || it == '+' }
        val name = args.str("name")
        val num: String
        if (direct.length >= 3) {
            num = direct
        } else {
            if (!has(Manifest.permission.READ_CONTACTS)) return "Contacts permission do."
            val found = ContactsHelper.findNumber(ctx, name) ?: return name + " contact nahi mila."
            num = found.second
        }
        ActivityStarter.start(ctx, Intent(Intent.ACTION_CALL, Uri.fromParts("tel", num, null)))
        return null
    }

    private fun global(action: Int): String? {
        val svc = JarvisAccessibilityService.instance ?: return "Accessibility service on karo."
        return if (svc.performGlobalAction(action)) null else "Ye nahi ho paya."
    }

    @Suppress("DEPRECATION")
    private fun unlock(): String? {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "jarvis:unlock"
        )
        wl.acquire(10_000L)
        if (!km.isKeyguardLocked) return null
        ActivityStarter.start(ctx, Intent(ctx, UnlockActivity::class.java))
        return if (km.isDeviceSecure) "PIN aap daalo." else null
    }

    private fun volume(args: JSONObject): String? {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val dir = args.str("direction").lowercase()
        val pct = args.optInt("percent", -1)
        when {
            pct in 0..100 ->
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * pct / 100f), 0)
            dir == "up" -> repeat(2) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            }
            dir == "down" -> repeat(2) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            }
            dir == "max" -> am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            dir == "mute" ->
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            dir == "unmute" ->
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            else -> return "Volume kitna karna hai?"
        }
        return null
    }

    private fun flashlight(args: JSONObject): String? {
        val cm = ctx.getSystemService(CameraManager::class.java)
        var id: String? = null
        for (cid in cm.cameraIdList) {
            val ch = cm.getCameraCharacteristics(cid)
            val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val facing = ch.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                id = cid
                break
            }
        }
        if (id == null) return "Is phone me flashlight nahi mili."
        val on = if (args.has("on") && !args.isNull("on")) args.optBoolean("on", true) else !torchOn
        cm.setTorchMode(id, on)
        torchOn = on
        return null
    }
}
