package com.tv.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Carries out a phone remote button press. Arrows, OK, Back and typing need
 * the accessibility service; everything else works without it.
 */
object RemoteCommands {

    // Android key codes for the TV remote service.
    private val keys = mapOf(
        "up" to KeyEvent.KEYCODE_DPAD_UP, "down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT, "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ok" to KeyEvent.KEYCODE_DPAD_CENTER, "back" to KeyEvent.KEYCODE_BACK,
        "enter" to KeyEvent.KEYCODE_ENTER, "playpause" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        "volup" to KeyEvent.KEYCODE_VOLUME_UP, "voldown" to KeyEvent.KEYCODE_VOLUME_DOWN,
        "mute" to KeyEvent.KEYCODE_VOLUME_MUTE, "rewind" to KeyEvent.KEYCODE_MEDIA_REWIND,
        "ff" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "power" to KeyEvent.KEYCODE_POWER,
        "input" to KeyEvent.KEYCODE_TV_INPUT,
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    fun run(context: Context, command: String, text: String?) {
        // Once paired, the TV's own remote service works in every app. If it
        // can't be reached, fall back to the accessibility service.
        if (AtvRemote.paired) {
            val fallback = { mainHandler.post { runLocally(context, command, text) }; Unit }
            when (command) {
                "type" -> return AtvRemote.type(text.orEmpty(), fallback)
                "typestart" -> return AtvRemote.startTyping()
                else -> keys[command]?.let { return AtvRemote.key(it, fallback) }
            }
        }
        runLocally(context, command, text)
    }

    private fun runLocally(context: Context, command: String, text: String?) {
        if (command == "typestart") return
        // On the Vortrex screen itself, press the key directly: always works.
        val home = MainActivity.foreground
        if (home != null && command in listOf("up", "down", "left", "right", "ok", "back")) {
            return home.pressKey(keys.getValue(command))
        }
        if (command == "launch") return Apps.launch(context, text.orEmpty())
        if (HomeButtonService.instance?.run(command, text) == true) return
        val audio = context.getSystemService(AudioManager::class.java)
        when (command) {
            "volup" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "voldown" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "playpause" -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "rewind" -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_REWIND)
            "ff" -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "netflix" -> Apps.launch(context, NETFLIX)
            "youtube" -> Apps.launch(context, YOUTUBE)
            "home" -> openHome(context)
        }
    }

    private fun mediaKey(audio: AudioManager, keyCode: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun openHome(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}
