package com.jarvis.assistant

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

class UnlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }

            override fun onDismissCancelled() {
                finish()
            }

            override fun onDismissError() {
                finish()
            }
        })
    }
}
