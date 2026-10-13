package com.jarvis.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val prefs = Prefs(context)
        if (!prefs.enabled) return
        Notifier.showRestartNotice(context)
    }
}
