package com.tv.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Sends the remote's Home button to this launcher, and lets the phone remote
 * press the D-pad, Back and type anywhere on the TV.
 */
class HomeButtonService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) RemoteCommands.openHome(this)
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    /** Returns false for commands that don't need this service. */
    fun run(command: String, text: String?): Boolean {
        when (command) {
            "up" -> move(GLOBAL_ACTION_DPAD_UP, View.FOCUS_UP)
            "down" -> move(GLOBAL_ACTION_DPAD_DOWN, View.FOCUS_DOWN)
            "left" -> move(GLOBAL_ACTION_DPAD_LEFT, View.FOCUS_LEFT)
            "right" -> move(GLOBAL_ACTION_DPAD_RIGHT, View.FOCUS_RIGHT)
            "ok" -> select()
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "type" -> type(text.orEmpty())
            "enter" -> enter()
            else -> return false
        }
        return true
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

    companion object {
        @Volatile
        var instance: HomeButtonService? = null
            private set
    }
}
