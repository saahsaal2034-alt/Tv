package com.tv.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
        "mute" to KeyEvent.KEYCODE_VOLUME_MUTE,
    )

    fun run(context: Context, command: String, text: String?) {
        // Once paired, the TV's own remote service works in every app.
        if (AtvRemote.paired) {
            when (command) {
                "type" -> return AtvRemote.type(text.orEmpty())
                "typestart" -> return AtvRemote.startTyping()
                else -> keys[command]?.let { return AtvRemote.key(it) }
            }
        }
        if (command == "typestart") return
        if (HomeButtonService.instance?.run(command, text) == true) return
        val audio = context.getSystemService(AudioManager::class.java)
        when (command) {
            "volup" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "voldown" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "playpause" -> {
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            }
            "netflix" -> Apps.launch(context, NETFLIX)
            "youtube" -> Apps.launch(context, YOUTUBE)
            "home" -> openHome(context)
        }
    }

    fun openHome(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}
