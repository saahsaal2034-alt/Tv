package com.tv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
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
        RemoteServer.start(this) { command, text -> mainHandler.post { run(command, text) } }
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

    private fun run(command: String, text: String?) {
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
            "type" -> type(text.orEmpty())
            "enter" -> enter()
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

    // Typing replaces the text in the TV's focused text box with the phone's text.
    private fun type(text: String) {
        val field = textField() ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun enter() {
        val field = textField()
        if (field != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        ) return
        select()
    }

    /** The focused text box, or the first one on screen if none has focus. */
    private fun textField(): AccessibilityNodeInfo? {
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(queue::add)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
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
