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

    fun run(context: Context, command: String, text: String?) {
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
