package com.tv.launcher

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Runs outside the app, started through the TV's own debugging connection
 * (like Shizuku or scrcpy), which is allowed to press keys in any app. Reads
 * commands from stdin, one per line: "key <code>" or "text <characters>".
 */
object KeyInjector {
    private lateinit var inputManager: Any
    private lateinit var inject: java.lang.reflect.Method

    @JvmStatic
    fun main(args: Array<String>) {
        Looper.prepareMainLooper()
        // InputManager.getInstance() and injectInputEvent() are hidden, so reach them by reflection.
        val managerClass = Class.forName("android.hardware.input.InputManager")
        inputManager = managerClass.getMethod("getInstance").invoke(null)!!
        inject = managerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

        println("ready")
        System.out.flush()
        val input = System.`in`.bufferedReader()
        while (true) {
            val line = input.readLine() ?: break
            try {
                when {
                    line.startsWith("key ") -> press(line.substring(4).trim().toInt())
                    line.startsWith("text ") -> type(line.substring(5))
                }
            } catch (e: Exception) {
                println("error ${e.message}")
                System.out.flush()
            }
        }
    }

    private fun press(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        send(down)
        send(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
    }

    private fun type(text: String) {
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: return
        for (event in events) {
            event.source = InputDevice.SOURCE_KEYBOARD
            send(event)
        }
    }

    // Mode 0 is "async": don't wait for the app to handle the key.
    private fun send(event: InputEvent) {
        inject.invoke(inputManager, event, 0)
    }
}
