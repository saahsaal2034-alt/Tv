package com.tv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends the remote's Home button to this launcher, for TVs where the stock
 * home screen can't be switched off without a computer.
 */
class HomeButtonService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit
}
