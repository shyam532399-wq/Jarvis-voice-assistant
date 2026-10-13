package com.jarvis.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat

object ContactsHelper {
    fun nameFor(ctx: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            var result: String? = null
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) result = c.getString(0)
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    fun findNumber(ctx: Context, name: String): Pair<String, String>? {
        if (name.isBlank()) return null
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        var best: Pair<String, String>? = null
        var bestScore = 0.0
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val s = Fuzzy.score(name, n)
                if (s > bestScore) {
                    bestScore = s
                    best = Pair(n, num)
                }
            }
        }
        return if (bestScore >= 0.6) best else null
    }
}

object ActivityStarter {
    fun start(ctx: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val svc = JarvisAccessibilityService.instance
        if (svc != null) {
            try {
                svc.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        ctx.startActivity(intent)
    }
}

object OemSettings {
    private val candidates = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.vivo.abe" to "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.powersaver.PowerSaverSettings"
    )

    fun openAutostart(ctx: Context) {
        for ((pkg, cls) in candidates) {
            try {
                val i = Intent().setComponent(ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        openAppInfo(ctx)
    }

    fun openAppInfo(ctx: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        safeStart(ctx, i)
    }
}
