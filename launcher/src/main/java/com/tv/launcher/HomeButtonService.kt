package com.tv.launcher

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Keeps this launcher as the TV's home screen (on start-up, on Home, and
 * whenever the stock home screen appears), and lets the phone remote press
 * the D-pad, Back and type anywhere on the TV.
 */
class HomeButtonService : AccessibilityService() {

    // Also runs when the TV starts up, so Vortrex opens instead of Google TV.
    override fun onServiceConnected() {
        instance = this
        RemoteCommands.openHome(this)
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

    // Whenever the stock home screen comes up, replace it with Vortrex.
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName?.toString() in STOCK_LAUNCHERS
        ) {
            RemoteCommands.openHome(this)
        }
    }

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

    // Android 13+ can press the D-pad directly. Older versions move focus by
    // hand: ask the app for the next item, and if it has no answer, pick the
    // nearest focusable item on screen in that direction.
    private fun move(globalAction: Int, direction: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && performGlobalAction(globalAction)) return
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.focusSearch(direction)?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) == true) return

        val candidates = focusables()
        if (focused == null) {
            candidates.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return
        }
        val from = Rect().also(focused::getBoundsInScreen)
        candidates
            .map { it to Rect().also(it::getBoundsInScreen) }
            .filter { (_, bounds) -> bounds != from }
            .mapNotNull { (node, bounds) -> distance(from, bounds, direction)?.let { node to it } }
            .minByOrNull { it.second }
            ?.first?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun focusables(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(queue::add)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isFocusable && node.isVisibleToUser && node.isEnabled) result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return result
    }

    /** How far [to] is from [from] in [direction], or null if it's the other way. */
    private fun distance(from: Rect, to: Rect, direction: Int): Long? {
        val dx = (to.centerX() - from.centerX()).toLong()
        val dy = (to.centerY() - from.centerY()).toLong()
        val (along, across) = when (direction) {
            View.FOCUS_RIGHT -> dx to dy
            View.FOCUS_LEFT -> -dx to dy
            View.FOCUS_DOWN -> dy to dx
            else -> -dy to dx
        }
        if (along <= 0) return null
        // Prefer items straight ahead over ones off to the side.
        return along * along + 4 * across * across
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
        private val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx", // Google TV
            "com.google.android.tvlauncher", // Android TV
            "com.google.android.leanbacklauncher", // Older Android TV
        )

        @Volatile
        var instance: HomeButtonService? = null
            private set
    }
}
