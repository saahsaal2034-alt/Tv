package com.tv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Sends the remote's Home button to this launcher, and carries out the
 * phone remote's button presses anywhere on the TV.
 */
class HomeButtonService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager

    override fun onServiceConnected() {
        audio = getSystemService(AudioManager::class.java)
        RemoteServer.start(this) { command -> mainHandler.post { run(command) } }
    }

    override fun onDestroy() {
        RemoteServer.stop()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) openHome()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    private fun run(command: String) {
        when (command) {
            "up" -> move(GLOBAL_ACTION_DPAD_UP, View.FOCUS_UP)
            "down" -> move(GLOBAL_ACTION_DPAD_DOWN, View.FOCUS_DOWN)
            "left" -> move(GLOBAL_ACTION_DPAD_LEFT, View.FOCUS_LEFT)
            "right" -> move(GLOBAL_ACTION_DPAD_RIGHT, View.FOCUS_RIGHT)
            "ok" -> select()
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> openHome()
            "volup" -> volume(AudioManager.ADJUST_RAISE)
            "voldown" -> volume(AudioManager.ADJUST_LOWER)
            "mute" -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
            "playpause" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "netflix" -> Apps.launch(this, NETFLIX)
            "youtube" -> Apps.launch(this, YOUTUBE)
        }
    }

    // Android 13+ can press the D-pad directly; older versions move focus by hand.
    private fun move(globalAction: Int, direction: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && performGlobalAction(globalAction)) return
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.focusSearch(direction)
            ?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun select() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) return
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun volume(direction: Int) =
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

    private fun mediaKey(keyCode: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun openHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}
